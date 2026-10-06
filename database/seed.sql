-- =====================================================================
-- BotiCompara - Datos de prueba
-- Ejecutar DESPUÉS de schema.sql. Se puede re-ejecutar (limpia primero).
-- Contraseña de todos los usuarios de prueba: Boti2026!
-- Hash BCrypt ($2a$) generado con pgcrypto, compatible con Spring Security.
-- =====================================================================

truncate table public.producto, public.botica, public.usuario restart identity cascade;

-- Usuarios
insert into public.usuario (nombre, correo, password_hash, telefono, direccion, rol, correo_verificado) values
  ('Rosa Quispe',    'rosa@boticasalud.pe',  extensions.crypt('Boti2026!', extensions.gen_salt('bf', 10)), '987654321', null,                    'DUENO_BOTICA', true),
  ('Jorge Mendoza',  'jorge@farmavida.pe',   extensions.crypt('Boti2026!', extensions.gen_salt('bf', 10)), '976543210', null,                    'DUENO_BOTICA', true),
  ('Lucía Torres',   'lucia@boticanueva.pe', extensions.crypt('Boti2026!', extensions.gen_salt('bf', 10)), '965432109', null,                    'DUENO_BOTICA', true),
  ('Carlos Ramírez', 'carlos@correo.com',    extensions.crypt('Boti2026!', extensions.gen_salt('bf', 10)), '954321098', 'Av. Lima 123, Chosica', 'COMPRADOR',    true);

-- Boticas (RUC con dígito verificador válido)
-- Salud Total y FarmaVida aprobadas; Nueva Era pendiente para probar la aprobación en Django
insert into public.botica (usuario_id, nombre_comercial, ruc, razon_social, direccion, distrito, telefono, estado)
select u.id, b.nombre_comercial, b.ruc, b.razon_social, b.direccion, b.distrito, b.telefono, b.estado
from (values
  ('rosa@boticasalud.pe',  'Botica Salud Total', '20601234565', 'Salud Total Farma S.A.C.', 'Av. Lima Sur 450',       'Lurigancho-Chosica', '014567890', 'APROBADO'),
  ('jorge@farmavida.pe',   'FarmaVida',          '20549871233', 'FarmaVida Perú E.I.R.L.',  'Jr. Trujillo 210',       'Lurigancho-Chosica', '014561234', 'APROBADO'),
  ('lucia@boticanueva.pe', 'Botica Nueva Era',   '20712345676', 'Nueva Era Salud S.A.C.',   'Av. Nicolás Ayllón 980', 'Ate',                '013459876', 'PENDIENTE')
) as b(correo, nombre_comercial, ruc, razon_social, direccion, distrito, telefono, estado)
join public.usuario u on u.correo = b.correo;

-- Productos: mismos principios activos en ambas boticas para comparar precios
insert into public.producto (botica_id, nombre_comercial, principio_activo, presentacion, precio, stock)
select bo.id, p.nombre_comercial, p.principio_activo, p.presentacion, p.precio, p.stock
from (values
  ('20601234565', 'Panadol',              'Paracetamol', 'Tableta 500 mg x 10',  8.50,  40),
  ('20601234565', 'Paracetamol Genérico', 'Paracetamol', 'Tableta 500 mg x 10',  2.50, 120),
  ('20601234565', 'Advil',                'Ibuprofeno',  'Tableta 400 mg x 10', 12.90,  25),
  ('20601234565', 'Ibuprofeno Genérico',  'Ibuprofeno',  'Tableta 400 mg x 10',  3.80,  80),
  ('20601234565', 'Amoxil',               'Amoxicilina', 'Cápsula 500 mg x 12', 24.00,  15),
  ('20601234565', 'Omeprazol Genérico',   'Omeprazol',   'Cápsula 20 mg x 14',   4.20,  60),
  ('20601234565', 'Losartán Genérico',    'Losartán',    'Tableta 50 mg x 30',   9.90,  30),
  ('20549871233', 'Panadol',              'Paracetamol', 'Tableta 500 mg x 10',  7.90,  35),
  ('20549871233', 'Paracetamol Genérico', 'Paracetamol', 'Tableta 500 mg x 10',  2.20,  90),
  ('20549871233', 'Amoxicilina Genérica', 'Amoxicilina', 'Cápsula 500 mg x 12',  8.50,  50),
  ('20549871233', 'Nexium',               'Esomeprazol', 'Tableta 40 mg x 14',  58.00,  10),
  ('20549871233', 'Omeprazol Genérico',   'Omeprazol',   'Cápsula 20 mg x 14',   3.90,  70),
  ('20549871233', 'Cozaar',               'Losartán',    'Tableta 50 mg x 30',  45.50,  12),
  ('20549871233', 'Ibuprofeno Genérico',  'Ibuprofeno',  'Tableta 400 mg x 10',  3.50,   0)  -- sin stock: NO debe salir en la búsqueda
) as p(ruc, nombre_comercial, principio_activo, presentacion, precio, stock)
join public.botica bo on bo.ruc = p.ruc;
