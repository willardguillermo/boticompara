-- =====================================================================
-- BotiCompara - Sprint 3 (H46, H47, H59, H60, H61, H62, H66)
-- Solo para actualizar la BD que ya existe. Ejecutar en el SQL Editor de
-- Supabase (como postgres) después de v3_catalogo_maestro.sql.
-- Agrega:
--   - columnas forma_farmaceutica, titular y codigo_digemid en medicamento (H46);
--   - precio objetivo y último precio avisado en favorito (H60, H61);
--   - preferencia notificaciones_precio en usuario (H66);
--   - tablas alerta_precio (H59, H61), busqueda (H62) y botica_revision (H47),
--     con RLS y política app_acceso_total para boticompara_app;
--   - carga inicial de botica_revision y los correos reales de los usuarios
--     de prueba.
-- Todo va en una transacción: si algo falla, no se aplica nada. Se puede
-- repetir sin romper nada ni duplicar datos.
-- =====================================================================

begin;

-- ---------------------------------------------------------------------
-- H46: MEDICAMENTO, datos del registro sanitario de DIGEMID.
-- codigo_digemid no es único: un mismo código puede repetirse entre
-- presentaciones.
-- ---------------------------------------------------------------------
alter table public.medicamento
  add column if not exists forma_farmaceutica varchar(100),
  add column if not exists titular            varchar(200),
  add column if not exists codigo_digemid     varchar(30);

create index if not exists idx_medicamento_codigo_digemid
  on public.medicamento(codigo_digemid) where codigo_digemid is not null;

-- ---------------------------------------------------------------------
-- H60, H61: FAVORITO con precio objetivo. ultimo_precio_avisado evita
-- repetir la alerta por el mismo precio.
-- ---------------------------------------------------------------------
alter table public.favorito
  add column if not exists precio_objetivo       numeric(10,2),
  add column if not exists ultimo_precio_avisado numeric(10,2);

alter table public.favorito drop constraint if exists chk_favorito_precio_objetivo;
alter table public.favorito add constraint chk_favorito_precio_objetivo
  check (precio_objetivo is null or precio_objetivo > 0);

-- ---------------------------------------------------------------------
-- H66: USUARIO, preferencia para recibir avisos de bajada de precio.
-- ---------------------------------------------------------------------
alter table public.usuario
  add column if not exists notificaciones_precio boolean not null default true;

-- ---------------------------------------------------------------------
-- H59, H61: ALERTA_PRECIO, avisos de bajada de precio de un favorito.
-- ---------------------------------------------------------------------
create table if not exists public.alerta_precio (
  id              bigserial primary key,
  usuario_id      bigint        not null references public.usuario(id) on delete cascade,
  producto_id     bigint        not null references public.producto(id) on delete cascade,
  precio_anterior numeric(10,2) not null,
  precio_nuevo    numeric(10,2) not null,
  leida           boolean       not null default false,
  fecha           timestamptz   not null default now(),
  constraint chk_alerta_precio_baja
    check (precio_nuevo > 0 and precio_nuevo < precio_anterior)
);

create index if not exists idx_alerta_precio_usuario
  on public.alerta_precio(usuario_id, leida, fecha desc);
create index if not exists idx_alerta_precio_producto
  on public.alerta_precio(producto_id);

-- ---------------------------------------------------------------------
-- H62: BUSQUEDA, registro de búsquedas del comprador (null = anónima).
-- El backend guarda texto = q.trim().toLowerCase().
-- ---------------------------------------------------------------------
create table if not exists public.busqueda (
  id         bigserial primary key,
  usuario_id bigint       references public.usuario(id) on delete set null,
  texto      varchar(150) not null check (char_length(texto) >= 2),
  tipo       varchar(20)  not null check (tipo in ('nombre', 'principio')),
  resultados integer      not null check (resultados >= 0),
  fecha      timestamptz  not null default now()
);

create index if not exists idx_busqueda_fecha on public.busqueda(fecha);
create index if not exists idx_busqueda_texto on public.busqueda(texto);

-- ---------------------------------------------------------------------
-- H47: BOTICA_REVISION, historial de aprobaciones y rechazos hechos en
-- Django. revisado_por es el username del admin; null = sin registro
-- (carga inicial).
-- ---------------------------------------------------------------------
create table if not exists public.botica_revision (
  id           bigserial primary key,
  botica_id    bigint       not null references public.botica(id) on delete cascade,
  estado       varchar(20)  not null check (estado in ('APROBADO', 'RECHAZADO')),
  motivo       text,
  revisado_por varchar(150),
  fecha        timestamptz  not null default now(),
  constraint chk_botica_revision_motivo
    check (estado <> 'RECHAZADO' or motivo is not null)
);

create index if not exists idx_botica_revision_fecha  on public.botica_revision(fecha);
create index if not exists idx_botica_revision_botica on public.botica_revision(botica_id, fecha);

-- Carga inicial: una fila por botica ya revisada que aún no tenga historial
insert into public.botica_revision (botica_id, estado, motivo, revisado_por, fecha)
select b.id, b.estado, b.motivo_rechazo, null, b.actualizado_en
from public.botica b
where b.estado in ('APROBADO', 'RECHAZADO')
  and not exists (select 1 from public.botica_revision r where r.botica_id = b.id);

-- ---------------------------------------------------------------------
-- Correos de prueba: ahora reciben correo de verdad (reenvío a la bandeja
-- del equipo). Solo cambia si el correo nuevo no está ocupado.
-- ---------------------------------------------------------------------
update public.usuario u
set correo = c.nuevo
from (values
  ('rosa@boticasalud.pe',  'rosa@boticompara.nissidev.dev'),
  ('jorge@farmavida.pe',   'jorge@boticompara.nissidev.dev'),
  ('lucia@boticanueva.pe', 'lucia@boticompara.nissidev.dev'),
  ('carlos@correo.com',    'carlos@boticompara.nissidev.dev')
) as c(viejo, nuevo)
where u.correo = c.viejo
  and not exists (select 1 from public.usuario x where x.correo = c.nuevo);

-- ---------------------------------------------------------------------
-- H47, H59, H61, H62: SEGURIDAD de las tablas nuevas, igual que el
-- catálogo maestro. RLS activado, sin acceso para la API automática de
-- Supabase y política app_acceso_total solo para boticompara_app (si el
-- rol existe).
-- ---------------------------------------------------------------------
do $$
declare
  t text;
begin
  foreach t in array array['alerta_precio', 'busqueda', 'botica_revision'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke all on public.%I from anon, authenticated', t);
  end loop;

  if exists (select 1 from pg_roles where rolname = 'boticompara_app') then
    foreach t in array array['alerta_precio', 'busqueda', 'botica_revision'] loop
      execute format('grant select, insert, update, delete on public.%I to boticompara_app', t);
      execute format('drop policy if exists app_acceso_total on public.%I', t);
      execute format('create policy app_acceso_total on public.%I '
                     'for all to boticompara_app using (true) with check (true)', t);
    end loop;
    grant usage, select on sequence
      public.alerta_precio_id_seq,
      public.busqueda_id_seq,
      public.botica_revision_id_seq
    to boticompara_app;
  end if;
end
$$;

commit;

-- ---------------------------------------------------------------------
-- VERIFICACIÓN (ejecutar aparte, después del commit)
-- ---------------------------------------------------------------------
-- Columnas nuevas:
-- select table_name, column_name, data_type, is_nullable, column_default
-- from information_schema.columns
-- where table_schema = 'public'
--   and (table_name, column_name) in (
--     ('medicamento', 'forma_farmaceutica'), ('medicamento', 'titular'),
--     ('medicamento', 'codigo_digemid'), ('favorito', 'precio_objetivo'),
--     ('favorito', 'ultimo_precio_avisado'), ('usuario', 'notificaciones_precio'))
-- order by table_name, column_name;
-- -- 6 filas
--
-- Tablas nuevas con RLS activo:
-- select relname, relrowsecurity
-- from pg_class
-- where relnamespace = 'public'::regnamespace
--   and relname in ('alerta_precio', 'busqueda', 'botica_revision');
-- -- 3 filas, relrowsecurity = true
--
-- Historial inicial de revisiones:
-- select r.botica_id, b.nombre_comercial, r.estado, r.motivo, r.revisado_por, r.fecha
-- from public.botica_revision r
-- join public.botica b on b.id = r.botica_id
-- order by r.botica_id, r.fecha;
-- -- una fila por botica APROBADO o RECHAZADO, revisado_por null
--
-- Correos nuevos:
-- select id, nombre, correo from public.usuario
-- where correo like '%@boticompara.nissidev.dev' order by id;
-- -- 4 filas
