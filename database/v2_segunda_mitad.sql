-- =====================================================================
-- BotiCompara - Cambios de la segunda mitad del Sprint 1
-- Ejecutar UNA vez en el SQL Editor de Supabase (como postgres) sobre la
-- BD que ya tiene schema.sql aplicado. Se puede repetir sin romper nada.
-- =====================================================================

-- ---------------------------------------------------------------------
-- TOKEN_USUARIO (H17, H20): recuperación de contraseña y verificación
-- de correo. Cada token sirve una sola vez y tiene fecha de vencimiento.
-- RECUPERACION: código de 6 dígitos (fácil de escribir en el móvil), vence a
--   los 15 min y se invalida tras 5 intentos fallidos (columna intentos).
-- VERIFICACION: token largo que viaja en el enlace del correo (48 h).
-- ---------------------------------------------------------------------
create table if not exists public.token_usuario (
  id         bigserial primary key,
  usuario_id bigint       not null references public.usuario(id) on delete cascade,
  token      varchar(255) not null,
  tipo       varchar(20)  not null check (tipo in ('VERIFICACION', 'RECUPERACION')),
  expira_en  timestamptz  not null,
  usado      boolean      not null default false,
  intentos   smallint     not null default 0 check (intentos >= 0),
  creado_en  timestamptz  not null default now()
);

-- Por si la tabla se creó con la primera versión de este script
alter table public.token_usuario
  add column if not exists intentos smallint not null default 0 check (intentos >= 0);

create index if not exists idx_token_usuario_usuario on public.token_usuario(usuario_id);
-- El código de 6 dígitos no es único globalmente: se busca por usuario + tipo
create index if not exists idx_token_usuario_busqueda on public.token_usuario(usuario_id, tipo, usado);

-- Mismo esquema de seguridad que usuario, botica y producto
alter table public.token_usuario enable row level security;
revoke all on public.token_usuario from anon, authenticated;
grant select, insert, update, delete on public.token_usuario to boticompara_app;
grant usage, select on sequence public.token_usuario_id_seq to boticompara_app;
drop policy if exists app_acceso_total on public.token_usuario;
create policy app_acceso_total on public.token_usuario
  for all to boticompara_app using (true) with check (true);

-- ---------------------------------------------------------------------
-- STORAGE (H4): bucket PRIVADO para las licencias de funcionamiento.
-- Solo PDF, JPG o PNG de hasta 5 MB. El backend sube los archivos con la
-- service role key; nadie los puede leer desde la API pública.
-- ---------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('licencias', 'licencias', false, 5242880,
        array['application/pdf', 'image/jpeg', 'image/png'])
on conflict (id) do nothing;
