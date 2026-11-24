-- =====================================================================
-- BotiCompara - El RUC solo se reserva al aprobar la botica (A5, 23/11/2026)
-- Solo para actualizar la BD que ya existe. Ejecutar en el SQL Editor de
-- Supabase (como postgres) después de v7_notificaciones.sql.
-- Problema: botica.ruc era unique en toda la tabla. Cualquiera que
-- registrara el RUC de una botica real la dejaba sin poder registrarse
-- ("El RUC ya está registrado"), aunque su solicitud quedara PENDIENTE
-- o RECHAZADO.
-- Cambia:
--   - Quita la restricción unique de botica.ruc (botica_ruc_key, o como
--     se llame: se busca por la columna).
--   - Crea uq_botica_ruc_aprobado: índice único parcial, un mismo RUC
--     solo puede estar en UNA botica APROBADO. Las PENDIENTE y RECHAZADO
--     pueden repetirlo (varias cuentas pueden pedir el mismo RUC; al
--     aprobar una, el admin de Django rechaza las demás pendientes).
--   - Crea idx_botica_ruc: índice normal para buscar por RUC (lo usaban
--     la API y el admin a través del índice de la restricción unique).
-- El check del formato del RUC (10, 15, 17 o 20 + 9 dígitos) no cambia.
-- No cambia datos. Todo va en una transacción: si algo falla, no se
-- aplica nada. Se puede repetir sin romper nada.
--
-- PRUEBA EN SECO: reemplazar el "commit;" final por
--   do $$ begin raise exception 'PRUEBA_EN_SECO'; end $$;
-- (la línea está comentada justo antes del commit). Si el único error es
-- PRUEBA_EN_SECO, todo lo anterior funcionó y se deshizo. Entonces volver
-- a poner "commit;" y ejecutarlo de verdad.
-- =====================================================================

begin;

-- ---------------------------------------------------------------------
-- A5: quitar el unique de toda la tabla. Se busca la restricción unique
-- que tenga solo la columna ruc, sin suponer su nombre.
-- ---------------------------------------------------------------------
do $$
declare
  restriccion text;
begin
  for restriccion in
    select c.conname
    from pg_constraint c
    join pg_attribute a on a.attrelid = c.conrelid and a.attnum = any (c.conkey)
    where c.conrelid = 'public.botica'::regclass
      and c.contype = 'u'
      and array_length(c.conkey, 1) = 1
      and a.attname = 'ruc'
  loop
    execute format('alter table public.botica drop constraint %I', restriccion);
    raise notice 'Se quitó la restricción %', restriccion;
  end loop;
end
$$;

-- ---------------------------------------------------------------------
-- A5: un RUC solo en una botica APROBADO. Antes de crearlo, si hubiera
-- dos aprobadas con el mismo RUC (no debería: hasta ahora era unique),
-- se detiene con un mensaje claro en vez del error del índice.
-- ---------------------------------------------------------------------
do $$
declare
  repetido text;
begin
  select ruc into repetido
  from public.botica
  where estado = 'APROBADO'
  group by ruc
  having count(*) > 1
  limit 1;
  if repetido is not null then
    raise exception 'Hay más de una botica APROBADO con el RUC %: rechaza las que sobren antes de ejecutar este script', repetido;
  end if;
end
$$;

create unique index if not exists uq_botica_ruc_aprobado
  on public.botica(ruc) where estado = 'APROBADO';

-- Buscar por RUC (POST /boticas, PUT /boticas/mia y el admin de Django)
create index if not exists idx_botica_ruc on public.botica(ruc);

-- do $$ begin raise exception 'PRUEBA_EN_SECO'; end $$;
commit;

-- ---------------------------------------------------------------------
-- VERIFICACIÓN (ejecutar aparte, después del commit)
-- ---------------------------------------------------------------------
-- Ya no hay unique en toda la columna ruc:
-- select conname, pg_get_constraintdef(oid) as definicion
-- from pg_constraint
-- where conrelid = 'public.botica'::regclass and contype = 'u';
-- -- solo botica_usuario_id_key (una botica por dueño); ninguna con (ruc)
--
-- Índices de ruc:
-- select indexname, indexdef from pg_indexes
-- where schemaname = 'public' and tablename = 'botica' and indexdef ilike '%(ruc)%'
-- order by indexname;
-- -- idx_botica_ruc (normal) y uq_botica_ruc_aprobado
-- -- (UNIQUE ... WHERE ((estado)::text = 'APROBADO'::text))
--
-- Dos PENDIENTE con el mismo RUC ya se pueden guardar; dos APROBADO no
-- (no deja nada guardado):
-- begin;
-- update public.botica set ruc = (select ruc from public.botica where estado = 'APROBADO' order by id limit 1)
-- where id = (select max(id) from public.botica where estado = 'APROBADO')
--   and id <> (select min(id) from public.botica where estado = 'APROBADO');
-- rollback;
-- -- ERROR: duplicate key value violates unique constraint "uq_botica_ruc_aprobado"
-- -- (si hay menos de dos boticas aprobadas, el update no cambia nada y no hay error)
