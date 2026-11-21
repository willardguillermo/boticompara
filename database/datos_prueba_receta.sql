-- =====================================================================
-- BotiCompara - Datos de prueba de la condición de venta (H72, 20/11/2026)
-- Ejecutar en el SQL Editor de Supabase (como postgres) después de
-- v6_receta.sql, seed.sql y seed_v3_catalogo.sql.
-- Agrega:
--   - condicion_venta de los medicamentos TEST-001 a TEST-012 (por
--     registro_sanitario):
--       SIN_RECETA: Panadol, Paracetamol Genérico, Advil, Ibuprofeno
--                   Genérico, Aspirina Genérica y Loratadina Genérica;
--       CON_RECETA: Amoxil, Amoxicilina Genérica, Cozaar, Losartán
--                   Genérico, Omeprazol Genérico y Nexium.
--   - el caso de receta retenida: categoría "Psicofármacos", principio
--     activo "Clonazepam", medicamento TEST-013 "Clonazepam Genérico"
--     (tableta 2 mg, RECETA_RETENIDA, registro vigente) y un producto
--     enlazado en Botica Salud Total (Tableta 2 mg x 30, S/ 18.50, stock 20)
--     para probar el 409 de POST /reservas.
-- Todo se busca por nombre o registro sanitario, nunca por id fijo.
-- Se puede repetir sin duplicar: lo que ya existe no se vuelve a insertar.
--
-- PRUEBA EN SECO: reemplazar el "commit;" final por
--   do $$ begin raise exception 'PRUEBA_EN_SECO'; end $$;
-- (la línea está comentada justo antes del commit). Si el único error es
-- PRUEBA_EN_SECO, todo lo anterior funcionó y se deshizo. Entonces volver
-- a poner "commit;" y ejecutarlo de verdad.
-- =====================================================================

begin;

-- ---------------------------------------------------------------------
-- 1. CONDICIÓN DE VENTA de TEST-001 a TEST-012
-- ---------------------------------------------------------------------
update public.medicamento m
set condicion_venta = v.condicion
from (values
  ('TEST-001', 'SIN_RECETA'),  -- Panadol
  ('TEST-002', 'SIN_RECETA'),  -- Paracetamol Genérico
  ('TEST-003', 'SIN_RECETA'),  -- Advil
  ('TEST-004', 'SIN_RECETA'),  -- Ibuprofeno Genérico
  ('TEST-005', 'CON_RECETA'),  -- Amoxil
  ('TEST-006', 'CON_RECETA'),  -- Amoxicilina Genérica
  ('TEST-007', 'CON_RECETA'),  -- Cozaar
  ('TEST-008', 'CON_RECETA'),  -- Losartán Genérico
  ('TEST-009', 'CON_RECETA'),  -- Omeprazol Genérico
  ('TEST-010', 'CON_RECETA'),  -- Nexium
  ('TEST-011', 'SIN_RECETA'),  -- Aspirina Genérica
  ('TEST-012', 'SIN_RECETA')   -- Loratadina Genérica (registro vencido, H39)
) as v(registro_sanitario, condicion)
where m.registro_sanitario = v.registro_sanitario
  and m.condicion_venta is distinct from v.condicion;

-- ---------------------------------------------------------------------
-- 2. CATEGORÍA y PRINCIPIO ACTIVO (sin distinguir mayúsculas ni tildes,
-- como en seed_v3_catalogo.sql)
-- ---------------------------------------------------------------------
insert into public.categoria (nombre)
select 'Psicofármacos'
where not exists (
  select 1 from public.categoria c
  where translate(lower(trim(c.nombre)), 'áéíóúü', 'aeiouu') = 'psicofarmacos'
);

insert into public.principio_activo (nombre)
select 'Clonazepam'
where not exists (
  select 1 from public.principio_activo pa
  where translate(lower(trim(pa.nombre)), 'áéíóúü', 'aeiouu') = 'clonazepam'
);

-- ---------------------------------------------------------------------
-- 3. MEDICAMENTO TEST-013 (registro sanitario único: uq_medicamento_registro).
-- Si ya existe, solo se asegura su condición.
-- ---------------------------------------------------------------------
insert into public.medicamento
  (nombre_comercial, presentacion, concentracion, principio_activo_id, categoria_id,
   registro_sanitario, fecha_vencimiento_registro, condicion_venta)
select 'Clonazepam Genérico', 'Tableta', '2 mg', pa.id, c.id,
       'TEST-013', date '2027-12-31', 'RECETA_RETENIDA'
from (
  select id from public.principio_activo
  where translate(lower(trim(nombre)), 'áéíóúü', 'aeiouu') = 'clonazepam'
  order by id limit 1
) pa
cross join (
  select id from public.categoria
  where translate(lower(trim(nombre)), 'áéíóúü', 'aeiouu') = 'psicofarmacos'
  order by id limit 1
) c
where not exists (
  select 1 from public.medicamento m where m.registro_sanitario = 'TEST-013'
);

update public.medicamento
set condicion_venta = 'RECETA_RETENIDA'
where registro_sanitario = 'TEST-013'
  and condicion_venta is distinct from 'RECETA_RETENIDA';

-- ---------------------------------------------------------------------
-- 4. PRODUCTO en Botica Salud Total enlazado a TEST-013. Si la botica ya
-- tiene un producto activo con el mismo nombre y presentación (sin
-- distinguir mayúsculas), no se crea otro.
-- ---------------------------------------------------------------------
insert into public.producto
  (botica_id, nombre_comercial, principio_activo, medicamento_id, presentacion, precio, stock)
select b.id, 'Clonazepam Genérico', 'Clonazepam', m.id, 'Tableta 2 mg x 30', 18.50, 20
from public.botica b
join public.medicamento m on m.registro_sanitario = 'TEST-013'
where b.nombre_comercial = 'Botica Salud Total'
  and not exists (
    select 1 from public.producto p
    where p.botica_id = b.id
      and p.activo
      and lower(trim(p.nombre_comercial)) = 'clonazepam genérico'
      and lower(trim(p.presentacion)) = 'tableta 2 mg x 30'
  );

-- Si el producto ya existía sin enlace, se enlaza (regla D2: el principio
-- activo pasa a ser el del medicamento)
update public.producto p
set medicamento_id = m.id,
    principio_activo = 'Clonazepam'
from public.botica b, public.medicamento m
where b.id = p.botica_id
  and b.nombre_comercial = 'Botica Salud Total'
  and m.registro_sanitario = 'TEST-013'
  and p.activo
  and lower(trim(p.nombre_comercial)) = 'clonazepam genérico'
  and lower(trim(p.presentacion)) = 'tableta 2 mg x 30'
  and p.medicamento_id is distinct from m.id;

-- Si falta algún medicamento, la botica o el producto, los insert y update
-- no hacen nada sin avisar: aquí se detiene
do $$
begin
  if (select count(*) from public.medicamento
      where registro_sanitario in ('TEST-001', 'TEST-002', 'TEST-003', 'TEST-004',
                                   'TEST-005', 'TEST-006', 'TEST-007', 'TEST-008',
                                   'TEST-009', 'TEST-010', 'TEST-011', 'TEST-012',
                                   'TEST-013')
        and condicion_venta is not null) <> 13 then
    raise exception 'Faltan medicamentos TEST-001 a TEST-013 o su condición: revisa seed_v3_catalogo.sql y v6_receta.sql';
  end if;

  if not exists (
    select 1 from public.producto p
    join public.botica b on b.id = p.botica_id
    join public.medicamento m on m.id = p.medicamento_id
    where b.nombre_comercial = 'Botica Salud Total'
      and m.registro_sanitario = 'TEST-013'
      and p.activo
  ) then
    raise exception 'Falta el producto Clonazepam Genérico en Botica Salud Total: revisa que exista la botica (seed.sql)';
  end if;
end $$;

-- do $$ begin raise exception 'PRUEBA_EN_SECO'; end $$;
commit;

-- Verificación (opcional):
-- select m.registro_sanitario, m.nombre_comercial, m.condicion_venta, c.nombre as categoria
-- from public.medicamento m
-- left join public.categoria c on c.id = m.categoria_id
-- where m.registro_sanitario like 'TEST-%'
-- order by m.registro_sanitario;
-- -- 13 filas: 6 SIN_RECETA, 6 CON_RECETA y TEST-013 RECETA_RETENIDA
--
-- select nombre_comercial, presentacion, precio, stock, botica_nombre, condicion_venta
-- from public.v_producto_busqueda
-- where lower(principio_activo) like '%clonazepam%';
-- -- 1 fila: Clonazepam Genérico, Tableta 2 mg x 30, 18.50, 20, Botica Salud Total, RECETA_RETENIDA
