-- =====================================================================
-- BotiCompara - Notificaciones del dueño en la web (H103, 22/11/2026)
-- Solo para actualizar la BD que ya existe. Ejecutar en el SQL Editor de
-- Supabase (como postgres) después de v6_receta.sql.
-- Agrega:
--   - Tabla notificacion: avisos para el dueño de una botica sobre las
--     reservas de sus productos (docs/API.md, sección 12):
--       NUEVA_RESERVA     = un comprador hizo una reserva;
--       RESERVA_CANCELADA = el comprador la canceló;
--       RESERVA_VENCIDA   = venció sin entregarse. Se generará cuando
--                           exista la tarea de H84 (fuera del sprint).
--     Una sola por reserva y tipo (evita duplicados). Ningún dato del
--     comprador, ni su nombre: el mensaje es "Amoxil x 3 · S/ 72.00".
--   - Índice parcial para contar las no leídas de un usuario (la web lo
--     consulta cada 30 s) e índice para listar las más recientes.
--   - RLS, sin acceso para la API automática de Supabase y política
--     app_acceso_total para boticompara_app, igual que las demás tablas.
-- No carga datos: las notificaciones las crea la API.
-- Todo va en una transacción: si algo falla, no se aplica nada. Se puede
-- repetir sin romper nada ni duplicar datos.
--
-- PRUEBA EN SECO: reemplazar el "commit;" final por
--   do $$ begin raise exception 'PRUEBA_EN_SECO'; end $$;
-- (la línea está comentada justo antes del commit). Si el único error es
-- PRUEBA_EN_SECO, todo lo anterior funcionó y se deshizo. Entonces volver
-- a poner "commit;" y ejecutarlo de verdad.
-- =====================================================================

begin;

-- ---------------------------------------------------------------------
-- H103: NOTIFICACION para el dueño de la botica.
-- usuario_id es el dueño que la recibe (botica.usuario_id del producto
-- reservado). titulo y mensaje se guardan ya escritos: dicen lo que pasó
-- en ese momento. La API la guarda después del commit de la reserva, en
-- una transacción aparte; si falla, la reserva no se ve afectada. Sin on
-- delete cascade, como reserva: usuarios y reservas no se borran.
-- ---------------------------------------------------------------------
create table if not exists public.notificacion (
  id          bigserial primary key,
  usuario_id  bigint        not null references public.usuario(id),
  tipo        varchar(30)   not null,
  titulo      varchar(100)  not null,
  mensaje     varchar(500)  not null,
  reserva_id  bigint        not null references public.reserva(id),
  leida       boolean       not null default false,
  creado_en   timestamptz   not null default now(),
  constraint chk_notificacion_tipo
    check (tipo in ('NUEVA_RESERVA', 'RESERVA_CANCELADA', 'RESERVA_VENCIDA')),
  -- Cada uno de estos cambios ocurre una sola vez en la vida de una reserva:
  -- si el mismo evento llega dos veces, el segundo insert falla y no duplica
  constraint uq_notificacion_reserva_tipo unique (reserva_id, tipo)
);

-- GET /notificaciones/no-leidas/total, cada 30 s: solo las filas no leídas
create index if not exists idx_notificacion_no_leidas
  on public.notificacion(usuario_id) where not leida;
-- GET /notificaciones: las 50 más recientes del usuario
create index if not exists idx_notificacion_usuario_fecha
  on public.notificacion(usuario_id, creado_en desc, id desc);

-- ---------------------------------------------------------------------
-- H103: SEGURIDAD, igual que en v5. RLS activado, sin acceso para la API
-- automática de Supabase y política app_acceso_total solo para
-- boticompara_app (si el rol existe).
-- ---------------------------------------------------------------------
do $$
begin
  alter table public.notificacion enable row level security;
  revoke all on public.notificacion from anon, authenticated;

  if exists (select 1 from pg_roles where rolname = 'boticompara_app') then
    grant select, insert, update, delete on public.notificacion to boticompara_app;
    drop policy if exists app_acceso_total on public.notificacion;
    create policy app_acceso_total on public.notificacion
      for all to boticompara_app using (true) with check (true);
    grant usage, select on sequence public.notificacion_id_seq to boticompara_app;
  end if;
end
$$;

-- do $$ begin raise exception 'PRUEBA_EN_SECO'; end $$;
commit;

-- ---------------------------------------------------------------------
-- VERIFICACIÓN (ejecutar aparte, después del commit)
-- ---------------------------------------------------------------------
-- Columnas:
-- select ordinal_position, column_name, data_type, character_maximum_length,
--        is_nullable, column_default
-- from information_schema.columns
-- where table_schema = 'public' and table_name = 'notificacion'
-- order by ordinal_position;
-- -- 8 filas, todas con is_nullable = NO
--
-- Restricciones (check de tipos, único, llaves foráneas):
-- select conname, pg_get_constraintdef(oid) as definicion
-- from pg_constraint
-- where conrelid = 'public.notificacion'::regclass
-- order by conname;
-- -- chk_notificacion_tipo, uq_notificacion_reserva_tipo, la llave primaria
-- -- y las 2 llaves foráneas (usuario y reserva)
--
-- Índices:
-- select indexname, indexdef from pg_indexes
-- where schemaname = 'public' and tablename = 'notificacion'
-- order by indexname;
-- -- idx_notificacion_no_leidas (parcial: WHERE (NOT leida)),
-- -- idx_notificacion_usuario_fecha, notificacion_pkey y
-- -- uq_notificacion_reserva_tipo
--
-- RLS y política:
-- select c.relrowsecurity,
--        (select count(*) from pg_policies p where p.schemaname = 'public'
--           and p.tablename = 'notificacion' and p.policyname = 'app_acceso_total') as politica
-- from pg_class c
-- where c.oid = 'public.notificacion'::regclass;
-- -- relrowsecurity = true, politica = 1
--
-- Sin acceso para la API automática de Supabase:
-- select grantee, privilege_type from information_schema.role_table_grants
-- where table_schema = 'public' and table_name = 'notificacion'
--   and grantee in ('anon', 'authenticated');
-- -- 0 filas
--
-- El check rechaza un tipo desconocido (no deja nada guardado):
-- begin;
-- insert into public.notificacion (usuario_id, tipo, titulo, mensaje, reserva_id)
-- select b.usuario_id, 'OTRO', 't', 'm', r.id
-- from public.reserva r
-- join public.producto p on p.id = r.producto_id
-- join public.botica b on b.id = p.botica_id
-- limit 1;
-- rollback;
-- -- ERROR: ... violates check constraint "chk_notificacion_tipo"
-- -- (si todavía no hay reservas, el insert no inserta nada y no hay error)
