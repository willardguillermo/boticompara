-- =====================================================================
-- BotiCompara - Catálogo maestro y equivalencias (H29-H32)
-- Ejecutar en el SQL Editor de Supabase (como postgres) sobre la BD que ya
-- tiene schema.sql y v2_segunda_mitad.sql aplicados. Se puede repetir sin
-- romper nada.
-- =====================================================================

-- Búsqueda por subcadena (lower(col) like '%texto%') con índices trigram
create extension if not exists pg_trgm with schema extensions;

-- ---------------------------------------------------------------------
-- CATEGORIA: agrupación del catálogo maestro (analgésicos, antibióticos...)
-- ---------------------------------------------------------------------
create table if not exists public.categoria (
  id     bigserial primary key,
  nombre varchar(100) not null unique
);

-- ---------------------------------------------------------------------
-- PRINCIPIO_ACTIVO: nombre único sin distinguir mayúsculas
-- ---------------------------------------------------------------------
create table if not exists public.principio_activo (
  id          bigserial primary key,
  nombre      varchar(150) not null,
  descripcion text
);

create unique index if not exists uq_principio_activo_nombre
  on public.principio_activo(lower(nombre));

-- ---------------------------------------------------------------------
-- MEDICAMENTO: catálogo maestro. Los productos de cada botica pueden
-- enlazarse a un medicamento con producto.medicamento_id.
-- oculto = true lo retira de la búsqueda sin borrarlo.
-- ---------------------------------------------------------------------
create table if not exists public.medicamento (
  id                         bigserial primary key,
  nombre_comercial           varchar(150) not null,
  presentacion               varchar(100) not null,
  concentracion              varchar(50),
  principio_activo_id        bigint       not null references public.principio_activo(id),
  categoria_id               bigint       references public.categoria(id),
  registro_sanitario         varchar(50),
  fecha_vencimiento_registro date,
  oculto                     boolean      not null default false,
  -- Destino de la FK compuesta de medicamento_grupo
  constraint uq_medicamento_id_principio unique (id, principio_activo_id)
);

create index if not exists idx_medicamento_principio on public.medicamento(principio_activo_id);
create index if not exists idx_medicamento_categoria on public.medicamento(categoria_id);

-- ---------------------------------------------------------------------
-- GRUPO_EQUIVALENCIA y MEDICAMENTO_GRUPO
-- Un medicamento está en un solo grupo y todos los del grupo comparten
-- principio activo. Se valida sin triggers, con claves foráneas compuestas:
--   - el grupo guarda su principio_activo_id;
--   - medicamento_grupo repite principio_activo_id y lo amarra a la vez al
--     medicamento (id, principio_activo_id) y al grupo (id, principio_activo_id);
--   - la PK sobre medicamento_id impide que un medicamento esté en dos grupos.
-- Si se intenta cambiar el principio activo de un medicamento o de un grupo
-- que ya tiene miembros, la FK lo rechaza (un trigger solo en
-- medicamento_grupo no cubriría ese caso).
-- ---------------------------------------------------------------------
create table if not exists public.grupo_equivalencia (
  id                  bigserial primary key,
  nombre              varchar(150) not null,
  principio_activo_id bigint       not null references public.principio_activo(id),
  constraint uq_grupo_id_principio unique (id, principio_activo_id)
);

create index if not exists idx_grupo_equivalencia_principio
  on public.grupo_equivalencia(principio_activo_id);

create table if not exists public.medicamento_grupo (
  medicamento_id      bigint not null primary key,
  grupo_id            bigint not null,
  principio_activo_id bigint not null,
  constraint fk_medicamento_grupo_medicamento
    foreign key (medicamento_id, principio_activo_id)
    references public.medicamento(id, principio_activo_id) on delete cascade,
  constraint fk_medicamento_grupo_grupo
    foreign key (grupo_id, principio_activo_id)
    references public.grupo_equivalencia(id, principio_activo_id) on delete cascade
);

create index if not exists idx_medicamento_grupo_grupo on public.medicamento_grupo(grupo_id);

-- ---------------------------------------------------------------------
-- PRODUCTO: enlace opcional al catálogo maestro.
-- producto.principio_activo (texto) se mantiene: lo usan la web y la carga
-- CSV de H11.
-- ---------------------------------------------------------------------
alter table public.producto
  add column if not exists medicamento_id bigint references public.medicamento(id);

create index if not exists idx_producto_medicamento on public.producto(medicamento_id);

-- ---------------------------------------------------------------------
-- CONSULTA_COMPARACION: registro de cada vez que se compara un producto.
-- usuario_id es null si la consulta fue anónima.
-- ---------------------------------------------------------------------
create table if not exists public.consulta_comparacion (
  id          bigserial primary key,
  producto_id bigint      not null references public.producto(id) on delete cascade,
  usuario_id  bigint      references public.usuario(id) on delete set null,
  fecha       timestamptz not null default now()
);

create index if not exists idx_consulta_comparacion_producto on public.consulta_comparacion(producto_id, fecha);
create index if not exists idx_consulta_comparacion_usuario  on public.consulta_comparacion(usuario_id);

-- ---------------------------------------------------------------------
-- FAVORITO: productos guardados por el comprador
-- ---------------------------------------------------------------------
create table if not exists public.favorito (
  usuario_id  bigint      not null references public.usuario(id) on delete cascade,
  producto_id bigint      not null references public.producto(id) on delete cascade,
  fecha       timestamptz not null default now(),
  primary key (usuario_id, producto_id)
);

create index if not exists idx_favorito_producto on public.favorito(producto_id);

-- ---------------------------------------------------------------------
-- ÍNDICES TRIGRAM para la búsqueda del comprador. Spring Boot filtra
-- v_producto_busqueda con lower(columna) like '%texto%', por eso se indexa
-- la expresión lower(...) de las columnas de producto que usa la vista.
-- ---------------------------------------------------------------------
create index if not exists idx_producto_nombre_trgm
  on public.producto using gin (lower(nombre_comercial) extensions.gin_trgm_ops);
create index if not exists idx_producto_principio_trgm
  on public.producto using gin (lower(principio_activo) extensions.gin_trgm_ops);

-- ---------------------------------------------------------------------
-- SEGURIDAD: mismo esquema que token_usuario. RLS activado, sin acceso para
-- la API automática de Supabase y política app_acceso_total solo para
-- boticompara_app.
-- ---------------------------------------------------------------------
do $$
declare
  t text;
begin
  foreach t in array array['categoria', 'principio_activo', 'medicamento', 'grupo_equivalencia',
                           'medicamento_grupo', 'consulta_comparacion', 'favorito'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke all on public.%I from anon, authenticated', t);
    execute format('grant select, insert, update, delete on public.%I to boticompara_app', t);
    execute format('drop policy if exists app_acceso_total on public.%I', t);
    execute format('create policy app_acceso_total on public.%I '
                   'for all to boticompara_app using (true) with check (true)', t);
  end loop;
end
$$;

grant usage, select on sequence
  public.categoria_id_seq,
  public.principio_activo_id_seq,
  public.medicamento_id_seq,
  public.grupo_equivalencia_id_seq,
  public.consulta_comparacion_id_seq
to boticompara_app;

-- ---------------------------------------------------------------------
-- VISTA de búsqueda (H22, H23, H24) ampliada con el catálogo maestro.
-- Las columnas actuales no cambian ni de nombre ni de orden; las nuevas van
-- al final (requisito de CREATE OR REPLACE VIEW). El filtro tampoco cambia:
-- quien consulta decide si excluye oculto o registro_vigente = false.
--   medicamento_id, principio_activo_id: null si el producto no está enlazado.
--   registro_vigente: null si no está enlazado o el registro no tiene fecha.
--   oculto: false si no está enlazado.
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
  coalesce(m.oculto, false)                      as oculto
from public.producto p
join public.botica b on b.id = p.botica_id
left join public.medicamento m on m.id = p.medicamento_id
where p.activo = true
  and p.stock > 0
  and b.estado = 'APROBADO';
