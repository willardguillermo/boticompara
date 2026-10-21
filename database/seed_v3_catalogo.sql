-- =====================================================================
-- BotiCompara - Datos de prueba del catálogo maestro y equivalencias (H32, H36)
-- Ejecutar en el SQL Editor de Supabase (como postgres) DESPUÉS de
-- v3_catalogo_maestro.sql y de seed.sql. Se puede repetir: no duplica nada.
--
-- Respeta lo que ya existe en la BD: la categoría "Analgesicos", los 8
-- principios activos, Panadol (TEST-001), "Paracetamol Generico" (TEST-002),
-- Advil (TEST-003) y el grupo "Paracetamol 500 mg tableta".
--
-- Los nombres se comparan sin distinguir mayúsculas ni tildes con
-- translate(lower(trim(...)), 'áéíóúü', 'aeiouu'), así "Losartan" y
-- "Losartán" cuentan como el mismo.
--
-- Casos de prueba que deja:
--   - Loratadina Genérica con registro vencido (2025-12-31) para H39.
--   - Omeprazol, Nexium, Aspirina y Loratadina sin grupo para H41.
-- =====================================================================

begin;

-- ---------------------------------------------------------------------
-- 1. CATEGORÍAS
-- ---------------------------------------------------------------------
-- Corrige la tilde, salvo que "Analgésicos" ya exista aparte
update public.categoria c
set nombre = 'Analgésicos'
where lower(trim(c.nombre)) = 'analgesicos'
  and not exists (select 1 from public.categoria x where lower(trim(x.nombre)) = 'analgésicos');

insert into public.categoria (nombre)
select v.nombre
from (values
  ('Analgésicos'),
  ('Antibióticos'),
  ('Antihipertensivos'),
  ('Gastroprotectores'),
  ('Antihistamínicos')
) as v(nombre)
where not exists (
  select 1 from public.categoria c
  where translate(lower(trim(c.nombre)), 'áéíóúü', 'aeiouu')
      = translate(lower(v.nombre), 'áéíóúü', 'aeiouu')
);

-- ---------------------------------------------------------------------
-- PRINCIPIOS ACTIVOS: ya existen los 8; solo se crean si faltara alguno
-- ---------------------------------------------------------------------
insert into public.principio_activo (nombre)
select v.nombre
from (values
  ('Paracetamol'),
  ('Ibuprofeno'),
  ('Amoxicilina'),
  ('Losartán'),
  ('Omeprazol'),
  ('Esomeprazol'),
  ('Ácido Acetilsalicílico'),
  ('Loratadina')
) as v(nombre)
where not exists (
  select 1 from public.principio_activo pa
  where translate(lower(trim(pa.nombre)), 'áéíóúü', 'aeiouu')
      = translate(lower(v.nombre), 'áéíóúü', 'aeiouu')
);

-- ---------------------------------------------------------------------
-- 2. Corrige la tilde de "Paracetamol Generico" (TEST-002)
-- ---------------------------------------------------------------------
update public.medicamento
set nombre_comercial = 'Paracetamol Genérico'
where registro_sanitario = 'TEST-002'
  and nombre_comercial <> 'Paracetamol Genérico';

-- ---------------------------------------------------------------------
-- 3. MEDICAMENTOS nuevos (TEST-004 a TEST-012). El registro sanitario es
-- único (uq_medicamento_registro): si ya existe, no se vuelve a insertar.
-- ---------------------------------------------------------------------
insert into public.medicamento
  (nombre_comercial, presentacion, concentracion, principio_activo_id, categoria_id,
   registro_sanitario, fecha_vencimiento_registro)
select v.nombre_comercial, v.presentacion, v.concentracion, pa.id, c.id,
       v.registro_sanitario, v.vencimiento::date
from (values
  ('Ibuprofeno Genérico',  'Tableta', '400 mg', 'Ibuprofeno',             'Analgésicos',       'TEST-004', '2027-12-31'),
  ('Amoxil',               'Cápsula', '500 mg', 'Amoxicilina',            'Antibióticos',      'TEST-005', '2027-12-31'),
  ('Amoxicilina Genérica', 'Cápsula', '500 mg', 'Amoxicilina',            'Antibióticos',      'TEST-006', '2027-12-31'),
  ('Cozaar',               'Tableta', '50 mg',  'Losartán',               'Antihipertensivos', 'TEST-007', '2027-12-31'),
  ('Losartán Genérico',    'Tableta', '50 mg',  'Losartán',               'Antihipertensivos', 'TEST-008', '2027-12-31'),
  ('Omeprazol Genérico',   'Cápsula', '20 mg',  'Omeprazol',              'Gastroprotectores', 'TEST-009', '2027-12-31'),
  ('Nexium',               'Tableta', '40 mg',  'Esomeprazol',            'Gastroprotectores', 'TEST-010', '2027-12-31'),
  ('Aspirina Genérica',    'Tableta', '100 mg', 'Ácido Acetilsalicílico', 'Analgésicos',       'TEST-011', '2027-12-31'),
  ('Loratadina Genérica',  'Tableta', '10 mg',  'Loratadina',             'Antihistamínicos',  'TEST-012', '2025-12-31')  -- vencido: H39
) as v(nombre_comercial, presentacion, concentracion, principio, categoria, registro_sanitario, vencimiento)
cross join lateral (
  select id from public.principio_activo
  where translate(lower(trim(nombre)), 'áéíóúü', 'aeiouu') = translate(lower(v.principio), 'áéíóúü', 'aeiouu')
  order by id limit 1
) pa
cross join lateral (
  select id from public.categoria
  where translate(lower(trim(nombre)), 'áéíóúü', 'aeiouu') = translate(lower(v.categoria), 'áéíóúü', 'aeiouu')
  order by id limit 1
) c
where not exists (
  select 1 from public.medicamento m where m.registro_sanitario = v.registro_sanitario
);

-- ---------------------------------------------------------------------
-- 4. GRUPOS DE EQUIVALENCIA (el de paracetamol ya existe).
-- El principio activo del grupo se toma del primer medicamento de la lista,
-- así coincide con el de sus miembros (FK compuesta de medicamento_grupo).
-- ---------------------------------------------------------------------
insert into public.grupo_equivalencia (nombre, principio_activo_id)
select v.nombre, m.principio_activo_id
from (values
  ('Ibuprofeno 400 mg tableta',  'TEST-003'),  -- Advil
  ('Amoxicilina 500 mg cápsula', 'TEST-005'),  -- Amoxil
  ('Losartán 50 mg tableta',     'TEST-007')   -- Cozaar
) as v(nombre, registro_sanitario)
join public.medicamento m on m.registro_sanitario = v.registro_sanitario
where not exists (
  select 1 from public.grupo_equivalencia g
  where translate(lower(trim(g.nombre)), 'áéíóúü', 'aeiouu') = translate(lower(v.nombre), 'áéíóúü', 'aeiouu')
);

-- Miembros. La PK de medicamento_grupo es medicamento_id: un medicamento que
-- ya está en un grupo no se mueve (on conflict do nothing).
insert into public.medicamento_grupo (medicamento_id, grupo_id, principio_activo_id)
select m.id, g.id, m.principio_activo_id
from (values
  ('Ibuprofeno 400 mg tableta',  'TEST-003'),  -- Advil
  ('Ibuprofeno 400 mg tableta',  'TEST-004'),  -- Ibuprofeno Genérico
  ('Amoxicilina 500 mg cápsula', 'TEST-005'),  -- Amoxil
  ('Amoxicilina 500 mg cápsula', 'TEST-006'),  -- Amoxicilina Genérica
  ('Losartán 50 mg tableta',     'TEST-007'),  -- Cozaar
  ('Losartán 50 mg tableta',     'TEST-008')   -- Losartán Genérico
) as v(grupo, registro_sanitario)
join public.medicamento m on m.registro_sanitario = v.registro_sanitario
cross join lateral (
  select id from public.grupo_equivalencia
  where translate(lower(trim(nombre)), 'áéíóúü', 'aeiouu') = translate(lower(v.grupo), 'áéíóúü', 'aeiouu')
  order by id limit 1
) g
on conflict (medicamento_id) do nothing;

-- ---------------------------------------------------------------------
-- 5. ENLACE producto.medicamento_id por nombre comercial del producto
-- (sin distinguir mayúsculas ni tildes). Solo los nombres de la lista: el
-- producto inactivo "X" no se toca.
-- ---------------------------------------------------------------------
update public.producto p
set medicamento_id = m.id
from (values
  ('Panadol',              'TEST-001'),
  ('Paracetamol Genérico', 'TEST-002'),
  ('Advil',                'TEST-003'),
  ('Ibuprofeno Genérico',  'TEST-004'),
  ('Amoxil',               'TEST-005'),
  ('Amoxicilina Genérica', 'TEST-006'),
  ('Cozaar',               'TEST-007'),
  ('Losartán Genérico',    'TEST-008'),
  ('Omeprazol Genérico',   'TEST-009'),
  ('Nexium',               'TEST-010'),
  ('Aspirina Genérica',    'TEST-011'),
  ('Loratadina Genérica',  'TEST-012')
) as v(nombre_producto, registro_sanitario)
join public.medicamento m on m.registro_sanitario = v.registro_sanitario
where translate(lower(trim(p.nombre_comercial)), 'áéíóúü', 'aeiouu')
    = translate(lower(v.nombre_producto), 'áéíóúü', 'aeiouu')
  and p.medicamento_id is distinct from m.id;

commit;

-- ---------------------------------------------------------------------
-- 6. VERIFICACIÓN: cada producto con su medicamento y su grupo
-- ---------------------------------------------------------------------
select
  p.id                         as producto_id,
  b.nombre_comercial           as botica,
  p.nombre_comercial           as producto,
  p.activo,
  m.nombre_comercial           as medicamento,
  m.registro_sanitario,
  pa.nombre                    as principio_activo,
  c.nombre                     as categoria,
  m.fecha_vencimiento_registro as vencimiento,
  g.nombre                     as grupo
from public.producto p
join public.botica b                   on b.id = p.botica_id
left join public.medicamento m         on m.id = p.medicamento_id
left join public.principio_activo pa   on pa.id = m.principio_activo_id
left join public.categoria c           on c.id = m.categoria_id
left join public.medicamento_grupo mg  on mg.medicamento_id = m.id
left join public.grupo_equivalencia g  on g.id = mg.grupo_id
order by g.nombre nulls last, m.nombre_comercial nulls last, b.nombre_comercial;
