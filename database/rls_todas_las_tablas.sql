-- =====================================================================
-- Ejecutar en Supabase DESPUÉS de correr `python manage.py migrate` en Django.
-- Django crea sus propias tablas (auth_user, django_session, etc.) sin RLS,
-- y en Supabase eso las dejaría expuestas por la API automática.
-- Este script activa RLS en TODAS las tablas del esquema public.
-- Se puede ejecutar varias veces sin problema.
-- =====================================================================
do $$
declare
  r record;
begin
  for r in select tablename from pg_tables where schemaname = 'public' loop
    execute format('alter table public.%I enable row level security', r.tablename);
  end loop;
end $$;
