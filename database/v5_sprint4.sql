-- =====================================================================
-- BotiCompara - Sprint 4 (H67, H72-H94)
-- Solo para actualizar la BD que ya existe. Ejecutar en el SQL Editor de
-- Supabase (como postgres) después de v4_sprint3.sql.
-- Agrega:
--   - login con Google en usuario: google_uid y password_hash opcional (H67);
--   - suspensión y baja de usuarios: suspendido_hasta y motivo_estado (H91-H93).
--     activo sigue mandando, no hay columna estado:
--       ACTIVO     = activo and (suspendido_hasta is null or suspendido_hasta <= now())
--       SUSPENDIDO = activo and suspendido_hasta > now()
--       BAJA       = not activo
--   - tablas reserva (H72-H84), resena (H85-H89) y usuario_accion (H94), con
--     RLS, política app_acceso_total para boticompara_app y trigger de
--     actualizado_en;
--   - v_producto_busqueda oculta los productos de dueños suspendidos o dados
--     de baja (H91-H93). Mismas columnas, en el mismo orden.
-- Todo va en una transacción: si algo falla, no se aplica nada. Se puede
-- repetir sin romper nada ni duplicar datos.
--
-- PRUEBA EN SECO: reemplazar el "commit;" final por
--   do $$ begin raise exception 'PRUEBA_EN_SECO'; end $$;
-- Si el único error es PRUEBA_EN_SECO, todo lo anterior funcionó y se
-- deshizo. Entonces volver a poner "commit;" y ejecutarlo de verdad.
-- =====================================================================

begin;

-- ---------------------------------------------------------------------
-- H67: USUARIO, login con Google. password_hash pasa a ser opcional
-- (cuentas creadas solo con Google), pero toda cuenta necesita al menos
-- una forma de entrar.
-- ---------------------------------------------------------------------
alter table public.usuario
  add column if not exists google_uid varchar(128);

alter table public.usuario alter column password_hash drop not null;

alter table public.usuario drop constraint if exists uq_usuario_google_uid;
alter table public.usuario add constraint uq_usuario_google_uid unique (google_uid);

alter table public.usuario drop constraint if exists chk_usuario_credencial;
alter table public.usuario add constraint chk_usuario_credencial
  check (password_hash is not null or google_uid is not null);

-- ---------------------------------------------------------------------
-- H91-H93: USUARIO, suspensión y baja hechas por el admin en Django.
-- Durante la suspensión activo sigue en true y la suspensión vence sola
-- cuando pasa suspendido_hasta. La baja es activo = false.
-- Una cuenta dada de baja no puede estar suspendida a la vez, y toda
-- suspensión lleva motivo.
-- ---------------------------------------------------------------------
alter table public.usuario
  add column if not exists suspendido_hasta timestamptz,
  add column if not exists motivo_estado    text;

alter table public.usuario drop constraint if exists chk_usuario_baja_sin_suspension;
alter table public.usuario add constraint chk_usuario_baja_sin_suspension
  check (activo or suspendido_hasta is null);

alter table public.usuario drop constraint if exists chk_usuario_motivo_suspension;
alter table public.usuario add constraint chk_usuario_motivo_suspension
  check (suspendido_hasta is null or motivo_estado is not null);

-- ---------------------------------------------------------------------
-- H72-H84: RESERVA de un producto por el comprador.
-- La botica se obtiene con el producto (no hay botica_id). precio_unitario
-- es el precio al momento de reservar. El stock lo maneja la API (sin
-- triggers). Sin on delete cascade: usuarios y productos se dan de baja
-- lógica y la reserva es un registro de la botica que no debe desaparecer.
-- ---------------------------------------------------------------------
create table if not exists public.reserva (
  id                   bigserial primary key,
  codigo               varchar(20)   not null,
  usuario_id           bigint        not null references public.usuario(id),
  producto_id          bigint        not null references public.producto(id),
  cantidad             integer       not null,
  precio_unitario      numeric(10,2) not null,
  estado               varchar(20)   not null default 'PENDIENTE',
  motivo_rechazo       text,
  vence_en             timestamptz   not null,
  recordatorio_enviado boolean       not null default false,
  creado_en            timestamptz   not null default now(),
  actualizado_en       timestamptz   not null default now(),
  constraint uq_reserva_codigo unique (codigo),
  constraint chk_reserva_cantidad check (cantidad > 0),
  constraint chk_reserva_precio check (precio_unitario > 0),
  constraint chk_reserva_estado
    check (estado in ('PENDIENTE', 'CONFIRMADA', 'LISTA', 'ENTREGADA',
                      'RECHAZADA', 'CANCELADA', 'VENCIDA')),
  constraint chk_reserva_motivo_rechazo
    check (estado <> 'RECHAZADA' or motivo_rechazo is not null)
);

create index if not exists idx_reserva_usuario  on public.reserva(usuario_id);
create index if not exists idx_reserva_producto on public.reserva(producto_id);
create index if not exists idx_reserva_estado   on public.reserva(estado);
-- Para el job de recordatorios y de reservas vencidas (H84)
create index if not exists idx_reserva_vence_en
  on public.reserva(vence_en) where estado in ('PENDIENTE', 'CONFIRMADA', 'LISTA');

drop trigger if exists trg_reserva_actualizado on public.reserva;
create trigger trg_reserva_actualizado
before update on public.reserva
for each row execute function public.set_actualizado_en();

-- ---------------------------------------------------------------------
-- H85-H89: RESENA de una botica. Una por comprador y botica.
-- respuesta/respondida_en: respuesta del dueño (van juntas).
-- oculta/motivo_ocultamiento: moderación del admin en Django.
-- ---------------------------------------------------------------------
create table if not exists public.resena (
  id                  bigserial primary key,
  botica_id           bigint       not null references public.botica(id),
  usuario_id          bigint       not null references public.usuario(id),
  estrellas           smallint     not null,
  comentario          varchar(500),
  respuesta           varchar(500),
  respondida_en       timestamptz,
  oculta              boolean      not null default false,
  motivo_ocultamiento text,
  creado_en           timestamptz  not null default now(),
  actualizado_en      timestamptz  not null default now(),
  constraint uq_resena_usuario_botica unique (usuario_id, botica_id),
  constraint chk_resena_estrellas check (estrellas between 1 and 5),
  constraint chk_resena_respuesta
    check ((respuesta is null) = (respondida_en is null)),
  constraint chk_resena_motivo_ocultamiento
    check (not oculta or motivo_ocultamiento is not null)
);

create index if not exists idx_resena_botica on public.resena(botica_id);

drop trigger if exists trg_resena_actualizado on public.resena;
create trigger trg_resena_actualizado
before update on public.resena
for each row execute function public.set_actualizado_en();

-- ---------------------------------------------------------------------
-- H94: USUARIO_ACCION, historial de suspensiones, reactivaciones y bajas
-- hechas en Django. realizado_por es el username del admin (igual que
-- botica_revision.revisado_por). El motivo es obligatorio salvo al
-- reactivar.
-- ---------------------------------------------------------------------
create table if not exists public.usuario_accion (
  id            bigserial primary key,
  usuario_id    bigint       not null references public.usuario(id),
  accion        varchar(20)  not null,
  motivo        text,
  realizado_por varchar(150) not null,
  fecha         timestamptz  not null default now(),
  constraint chk_usuario_accion_accion
    check (accion in ('SUSPENDER', 'REACTIVAR', 'DAR_BAJA')),
  constraint chk_usuario_accion_motivo
    check (accion = 'REACTIVAR' or motivo is not null)
);

create index if not exists idx_usuario_accion_usuario
  on public.usuario_accion(usuario_id, fecha);

-- ---------------------------------------------------------------------
-- H91-H93: V_PRODUCTO_BUSQUEDA oculta los productos cuyo dueño está
-- suspendido o dado de baja. Columnas idénticas (nombre, orden y tipo) a
-- las de v4: solo cambian el join a usuario y el filtro. Conserva
-- security_invoker; create or replace mantiene los grants de la vista.
-- ---------------------------------------------------------------------
create or replace view public.v_producto_busqueda
with (security_invoker = true) as
select
  p.id               as producto_id,
  p.nombre_comercial,
  p.principio_activo,
  p.presentacion,
  p.precio,
  p.stock,
  b.id               as botica_id,
  b.nombre_comercial as botica_nombre,
  b.direccion        as botica_direccion,
  b.distrito         as botica_distrito,
  b.telefono         as botica_telefono,
  p.medicamento_id,
  m.principio_activo_id,
  (m.fecha_vencimiento_registro >= current_date) as registro_vigente,
  coalesce(m.oculto, false)                      as oculto,
  b.latitud                                      as botica_latitud,
  b.longitud                                     as botica_longitud
from public.producto p
join public.botica b on b.id = p.botica_id
join public.usuario u on u.id = b.usuario_id
left join public.medicamento m on m.id = p.medicamento_id
where p.activo = true
  and p.stock > 0
  and b.estado = 'APROBADO'
  and u.activo
  and (u.suspendido_hasta is null or u.suspendido_hasta <= now());

-- ---------------------------------------------------------------------
-- H72-H94: SEGURIDAD de las tablas nuevas, igual que en v4. RLS activado,
-- sin acceso para la API automática de Supabase y política
-- app_acceso_total solo para boticompara_app (si el rol existe).
-- ---------------------------------------------------------------------
do $$
declare
  t text;
begin
  foreach t in array array['reserva', 'resena', 'usuario_accion'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke all on public.%I from anon, authenticated', t);
  end loop;

  if exists (select 1 from pg_roles where rolname = 'boticompara_app') then
    foreach t in array array['reserva', 'resena', 'usuario_accion'] loop
      execute format('grant select, insert, update, delete on public.%I to boticompara_app', t);
      execute format('drop policy if exists app_acceso_total on public.%I', t);
      execute format('create policy app_acceso_total on public.%I '
                     'for all to boticompara_app using (true) with check (true)', t);
    end loop;
    grant usage, select on sequence
      public.reserva_id_seq,
      public.resena_id_seq,
      public.usuario_accion_id_seq
    to boticompara_app;
  end if;
end
$$;

commit;

-- ---------------------------------------------------------------------
-- VERIFICACIÓN (ejecutar aparte, después del commit)
-- ---------------------------------------------------------------------
-- Columnas de usuario:
-- select column_name, data_type, character_maximum_length, is_nullable
-- from information_schema.columns
-- where table_schema = 'public' and table_name = 'usuario'
--   and column_name in ('password_hash', 'google_uid', 'suspendido_hasta', 'motivo_estado')
-- order by column_name;
-- -- 4 filas, todas is_nullable = YES
--
-- Restricciones nuevas:
-- select conrelid::regclass as tabla, conname, pg_get_constraintdef(oid) as definicion
-- from pg_constraint
-- where conrelid in ('public.usuario'::regclass, 'public.reserva'::regclass,
--                    'public.resena'::regclass, 'public.usuario_accion'::regclass)
--   and contype in ('c', 'u', 'f')
-- order by 1, 2;
--
-- Tablas nuevas con RLS, política y triggers:
-- select c.relname, c.relrowsecurity,
--        (select count(*) from pg_policies p where p.schemaname = 'public'
--           and p.tablename = c.relname and p.policyname = 'app_acceso_total') as politica,
--        (select count(*) from pg_trigger g where g.tgrelid = c.oid and not g.tgisinternal) as triggers
-- from pg_class c
-- where c.relnamespace = 'public'::regnamespace
--   and c.relname in ('reserva', 'resena', 'usuario_accion');
-- -- 3 filas, relrowsecurity = true, politica = 1; triggers = 1 en reserva y resena, 0 en usuario_accion
--
-- Índices nuevos:
-- select tablename, indexname, indexdef from pg_indexes
-- where schemaname = 'public' and tablename in ('reserva', 'resena', 'usuario_accion')
-- order by 1, 2;
--
-- Vista: mismas 17 columnas y security_invoker:
-- select reloptions from pg_class where oid = 'public.v_producto_busqueda'::regclass;
-- select count(*) from public.v_producto_busqueda;
-- -- igual que antes del script (hoy ningún dueño está suspendido ni dado de baja)
