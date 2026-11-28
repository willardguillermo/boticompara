-- =====================================================================
-- BotiCompara - Registro con el correo verificado por código (H1, H20, 26/11/2026)
-- Solo para actualizar la BD que ya existe. Ejecutar en el SQL Editor de
-- Supabase (como postgres) después de v8_ruc_solo_aprobadas.sql.
-- Problema: la cuenta se creaba antes de verificar el correo (llegaba un
-- enlace después). Ahora el correo se verifica con un código de 6 dígitos
-- ANTES de crear la cuenta, y en usuario y botica no se guarda nada hasta
-- que el registro esté completo (docs/API.md, sección 1).
-- Agrega:
--   - Tabla codigo_correo: los códigos de 6 dígitos que se envían por
--     correo y el token de un solo uso que se entrega al confirmarlos.
--       REGISTRO     = POST /auth/registro/codigo y /verificar: todavía
--                      no hay cuenta, por eso se guarda el correo y no un
--                      usuario_id (sin llave foránea).
--       VERIFICACION = POST /auth/verificacion/codigo y /confirmar:
--                      cuentas antiguas con correo_verificado = false.
--     Nunca guarda el código ni el token en claro: solo su HMAC-SHA256
--     (64 caracteres hexadecimales, con el JWT_SECRET como clave).
--   - Índices: el último código de un correo y cuántos se enviaron en la
--     última hora; el token (único); y la fecha, para la limpieza.
--   - RLS, sin acceso para la API automática de Supabase y política
--     app_acceso_total para boticompara_app, igual que las demás tablas.
-- No cambia usuario ni token_usuario, y no carga datos. La API borra cada
-- hora las filas de más de un día (consulta al final, por si se quiere
-- hacer a mano). Todo va en una transacción: si algo falla, no se aplica
-- nada. Se puede repetir sin romper nada ni duplicar datos.
--
-- PRUEBA EN SECO: reemplazar el "commit;" final por
--   do $$ begin raise exception 'PRUEBA_EN_SECO'; end $$;
-- (la línea está comentada justo antes del commit). Si el único error es
-- PRUEBA_EN_SECO, todo lo anterior funcionó y se deshizo. Entonces volver
-- a poner "commit;" y ejecutarlo de verdad.
-- =====================================================================

begin;

-- ---------------------------------------------------------------------
-- H20: CODIGO_CORREO. Una fila por código enviado.
--   vence_en       = el código vence a los 15 minutos.
--   intentos       = códigos incorrectos; al quinto queda invalidado.
--   verificado_en  = cuándo se confirmó el código (ya no sirve otra vez).
--   token_hash     = solo REGISTRO: el tokenVerificacion que se entregó
--                    al confirmar; vence a los 30 minutos (token_vence_en).
--   usado_en       = cuándo se gastó el token (se creó la cuenta).
--   anulado        = se pidió otro código para el mismo correo y
--                    propósito: este ya no sirve, ni su token.
-- ---------------------------------------------------------------------
create table if not exists public.codigo_correo (
  id              bigserial primary key,
  correo          varchar(150)  not null,
  proposito       varchar(20)   not null,
  codigo_hash     varchar(64)   not null,
  intentos        smallint      not null default 0,
  vence_en        timestamptz   not null,
  verificado_en   timestamptz,
  token_hash      varchar(64),
  token_vence_en  timestamptz,
  usado_en        timestamptz,
  anulado         boolean       not null default false,
  creado_en       timestamptz   not null default now(),
  constraint chk_codigo_correo_proposito
    check (proposito in ('REGISTRO', 'VERIFICACION')),
  -- La API guarda el correo en minúsculas y sin espacios: así se compara con "="
  constraint chk_codigo_correo_minusculas
    check (correo = lower(btrim(correo))),
  constraint chk_codigo_correo_intentos
    check (intentos between 0 and 5),
  -- El token y su vencimiento van juntos, solo en REGISTRO y solo después de
  -- confirmar el código
  constraint chk_codigo_correo_token
    check ((token_hash is null) = (token_vence_en is null)
           and (token_hash is null or (proposito = 'REGISTRO' and verificado_en is not null)))
);

-- El último código de un correo y cuántos se enviaron en la última hora
-- (60 s entre códigos y máximo 5 por hora)
create index if not exists idx_codigo_correo_correo
  on public.codigo_correo(correo, proposito, creado_en desc);
-- POST /auth/registro y /registro/validar-ruc buscan por el hash del token
create unique index if not exists uq_codigo_correo_token
  on public.codigo_correo(token_hash) where token_hash is not null;
-- Limpieza de las filas viejas
create index if not exists idx_codigo_correo_creado
  on public.codigo_correo(creado_en);

-- ---------------------------------------------------------------------
-- H20: SEGURIDAD, igual que en v7. RLS activado, sin acceso para la API
-- automática de Supabase y política app_acceso_total solo para
-- boticompara_app (si el rol existe).
-- ---------------------------------------------------------------------
do $$
begin
  alter table public.codigo_correo enable row level security;
  revoke all on public.codigo_correo from anon, authenticated;

  if exists (select 1 from pg_roles where rolname = 'boticompara_app') then
    grant select, insert, update, delete on public.codigo_correo to boticompara_app;
    drop policy if exists app_acceso_total on public.codigo_correo;
    create policy app_acceso_total on public.codigo_correo
      for all to boticompara_app using (true) with check (true);
    grant usage, select on sequence public.codigo_correo_id_seq to boticompara_app;
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
-- where table_schema = 'public' and table_name = 'codigo_correo'
-- order by ordinal_position;
-- -- 12 filas; aceptan null solo verificado_en, token_hash, token_vence_en y usado_en
--
-- Restricciones:
-- select conname, pg_get_constraintdef(oid) as definicion
-- from pg_constraint
-- where conrelid = 'public.codigo_correo'::regclass
-- order by conname;
-- -- chk_codigo_correo_intentos, chk_codigo_correo_minusculas,
-- -- chk_codigo_correo_proposito, chk_codigo_correo_token y codigo_correo_pkey
--
-- Índices:
-- select indexname, indexdef from pg_indexes
-- where schemaname = 'public' and tablename = 'codigo_correo'
-- order by indexname;
-- -- codigo_correo_pkey, idx_codigo_correo_correo, idx_codigo_correo_creado
-- -- y uq_codigo_correo_token (UNIQUE ... WHERE (token_hash IS NOT NULL))
--
-- RLS y política:
-- select c.relrowsecurity,
--        (select count(*) from pg_policies p where p.schemaname = 'public'
--           and p.tablename = 'codigo_correo' and p.policyname = 'app_acceso_total') as politica
-- from pg_class c
-- where c.oid = 'public.codigo_correo'::regclass;
-- -- relrowsecurity = true, politica = 1
--
-- Sin acceso para la API automática de Supabase:
-- select grantee, privilege_type from information_schema.role_table_grants
-- where table_schema = 'public' and table_name = 'codigo_correo'
--   and grantee in ('anon', 'authenticated');
-- -- 0 filas
--
-- Los checks rechazan datos inválidos (no deja nada guardado):
-- begin;
-- insert into public.codigo_correo (correo, proposito, codigo_hash, vence_en)
-- values ('Ana@Correo.pe', 'REGISTRO', repeat('a', 64), now() + interval '15 minutes');
-- rollback;
-- -- ERROR: ... violates check constraint "chk_codigo_correo_minusculas"
-- begin;
-- insert into public.codigo_correo (correo, proposito, codigo_hash, vence_en, verificado_en, token_hash, token_vence_en)
-- values ('ana@correo.pe', 'VERIFICACION', repeat('a', 64), now(), now(), repeat('b', 64), now());
-- rollback;
-- -- ERROR: ... violates check constraint "chk_codigo_correo_token"
--
-- ---------------------------------------------------------------------
-- LIMPIEZA (la hace la API cada hora; esta es la misma consulta a mano)
-- ---------------------------------------------------------------------
-- Un código vive 15 minutos y su token 30 más; el límite por correo mira
-- la última hora. Pasado un día, ninguna fila sirve para nada:
-- delete from public.codigo_correo where creado_en < now() - interval '1 day';
