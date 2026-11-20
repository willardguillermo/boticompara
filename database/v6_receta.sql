-- =====================================================================
-- BotiCompara - Condición de venta de los medicamentos (H72, 20/11/2026)
-- Solo para actualizar la BD que ya existe. Ejecutar en el SQL Editor de
-- Supabase (como postgres) después de v5_sprint4.sql.
-- Agrega:
--   - medicamento.condicion_venta (DIGEMID), opcional:
--       SIN_RECETA      = venta libre;
--       CON_RECETA      = con receta médica (se puede reservar);
--       RECETA_RETENIDA = psicofármacos y estupefacientes, la botica se
--                         queda con la receta (no se puede reservar);
--       null            = sin información.
--   - v_producto_busqueda con la columna condicion_venta AL FINAL (sale del
--     medicamento enlazado; null si el producto no está enlazado). Las 17
--     columnas de v5 quedan iguales (nombre, orden y tipo), con el mismo
--     filtro y security_invoker.
-- Este script NO carga la condición de ningún medicamento: todos quedan
-- en null hasta que se decidan los datos de la demo.
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
-- H72: MEDICAMENTO, condición de venta según DIGEMID. Es un dato del
-- catálogo maestro: el dueño de botica no la cambia.
-- ---------------------------------------------------------------------
alter table public.medicamento
  add column if not exists condicion_venta varchar(20);

alter table public.medicamento drop constraint if exists chk_medicamento_condicion_venta;
alter table public.medicamento add constraint chk_medicamento_condicion_venta
  check (condicion_venta in ('SIN_RECETA', 'CON_RECETA', 'RECETA_RETENIDA'));

-- ---------------------------------------------------------------------
-- H72: V_PRODUCTO_BUSQUEDA con condicion_venta al final. Las 17 columnas
-- de v5 no cambian (create or replace view solo permite agregar columnas
-- al final). Conserva security_invoker; create or replace mantiene los
-- grants de la vista.
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
  b.longitud                                     as botica_longitud,
  m.condicion_venta                              as condicion_venta
from public.producto p
join public.botica b on b.id = p.botica_id
join public.usuario u on u.id = b.usuario_id
left join public.medicamento m on m.id = p.medicamento_id
where p.activo = true
  and p.stock > 0
  and b.estado = 'APROBADO'
  and u.activo
  and (u.suspendido_hasta is null or u.suspendido_hasta <= now());

-- do $$ begin raise exception 'PRUEBA_EN_SECO'; end $$;
commit;

-- ---------------------------------------------------------------------
-- VERIFICACIÓN (ejecutar aparte, después del commit)
-- ---------------------------------------------------------------------
-- Columna nueva:
-- select column_name, data_type, character_maximum_length, is_nullable
-- from information_schema.columns
-- where table_schema = 'public' and table_name = 'medicamento'
--   and column_name = 'condicion_venta';
-- -- 1 fila: character varying, 20, YES
--
-- Restricción:
-- select conname, pg_get_constraintdef(oid) as definicion
-- from pg_constraint
-- where conrelid = 'public.medicamento'::regclass
--   and conname = 'chk_medicamento_condicion_venta';
--
-- Vista: 18 columnas, condicion_venta la última, y security_invoker:
-- select ordinal_position, column_name
-- from information_schema.columns
-- where table_schema = 'public' and table_name = 'v_producto_busqueda'
-- order by ordinal_position;
-- select reloptions from pg_class where oid = 'public.v_producto_busqueda'::regclass;
-- select count(*) from public.v_producto_busqueda;
-- -- igual que antes del script
--
-- Todavía sin datos (todos null hasta que se carguen):
-- select condicion_venta, count(*) from public.medicamento group by 1;
