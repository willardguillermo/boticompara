-- =====================================================================
-- BotiCompara - Esquema de base de datos (PostgreSQL / Supabase)
-- Única fuente de verdad de la estructura de la BD.
-- Administrador: Guillermo. Nadie más crea ni modifica tablas.
-- Cambios: abrir un Pull Request a este archivo.
-- =====================================================================

-- En Supabase las extensiones viven en el esquema "extensions"
create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;
-- Búsqueda por subcadena (lower(col) like '%texto%') con índices trigram (H29-H32)
create extension if not exists pg_trgm with schema extensions;

-- ---------------------------------------------------------------------
-- Función que actualiza la columna actualizado_en en cada UPDATE
-- ---------------------------------------------------------------------
create or replace function public.set_actualizado_en()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.actualizado_en = now();
  return new;
end;
$$;

-- ---------------------------------------------------------------------
-- USUARIO: compradores y dueños de botica (login lo maneja Spring Boot)
-- Los administradores de la plataforma usan el login propio de Django.
-- ---------------------------------------------------------------------
create table if not exists usuario (
  id                bigserial primary key,
  nombre            varchar(100) not null,
  correo            varchar(150) not null unique,
  password_hash     varchar(255) not null,               -- BCrypt
  telefono          varchar(20),
  direccion         varchar(255),
  rol               varchar(20)  not null
                    check (rol in ('COMPRADOR', 'DUENO_BOTICA')),
  correo_verificado boolean      not null default false,
  activo            boolean      not null default true,
  creado_en         timestamptz  not null default now(),
  actualizado_en    timestamptz  not null default now()
);

create trigger trg_usuario_actualizado
before update on usuario
for each row execute function public.set_actualizado_en();

-- ---------------------------------------------------------------------
-- BOTICA: una por dueño. Queda PENDIENTE hasta que el admin la aprueba.
-- ---------------------------------------------------------------------
create table if not exists botica (
  id               bigserial primary key,
  usuario_id       bigint       not null unique
                   references usuario(id) on delete cascade,
  nombre_comercial varchar(150) not null,
  ruc              varchar(11)  not null unique
                   check (ruc ~ '^(10|15|17|20)[0-9]{9}$'),
  razon_social     varchar(200) not null,
  direccion        varchar(255) not null,
  distrito         varchar(100) not null,
  telefono         varchar(20),
  latitud          numeric(9,6),                         -- H3 (2da mitad)
  longitud         numeric(9,6),                         -- H3 (2da mitad)
  licencia_url     text,                                 -- H4 (2da mitad)
  estado           varchar(20)  not null default 'PENDIENTE'
                   check (estado in ('PENDIENTE', 'APROBADO', 'RECHAZADO')),
  motivo_rechazo   text,
  creado_en        timestamptz  not null default now(),
  actualizado_en   timestamptz  not null default now(),
  constraint chk_motivo_rechazo
    check (estado <> 'RECHAZADO' or motivo_rechazo is not null)
);

create index if not exists idx_botica_estado on botica(estado);

create trigger trg_botica_actualizado
before update on botica
for each row execute function public.set_actualizado_en();

-- ---------------------------------------------------------------------
-- CATÁLOGO MAESTRO (H29-H32). En la BD que ya existe se agregó con
-- v3_catalogo_maestro.sql.
-- CATEGORIA: agrupación del catálogo maestro (analgésicos, antibióticos...)
-- ---------------------------------------------------------------------
create table if not exists categoria (
  id     bigserial primary key,
  nombre varchar(100) not null unique
);

-- ---------------------------------------------------------------------
-- PRINCIPIO_ACTIVO: nombre único sin distinguir mayúsculas
-- ---------------------------------------------------------------------
create table if not exists principio_activo (
  id          bigserial primary key,
  nombre      varchar(150) not null,
  descripcion text
);

create unique index if not exists uq_principio_activo_nombre
  on principio_activo(lower(nombre));

-- ---------------------------------------------------------------------
-- MEDICAMENTO: catálogo maestro. Los productos de cada botica pueden
-- enlazarse a un medicamento con producto.medicamento_id.
-- oculto = true lo retira de la búsqueda sin borrarlo.
-- ---------------------------------------------------------------------
create table if not exists medicamento (
  id                         bigserial primary key,
  nombre_comercial           varchar(150) not null,
  presentacion               varchar(100) not null,
  concentracion              varchar(50),
  principio_activo_id        bigint       not null references principio_activo(id),
  categoria_id               bigint       references categoria(id),
  registro_sanitario         varchar(50),
  fecha_vencimiento_registro date,
  oculto                     boolean      not null default false,
  -- Destino de la FK compuesta de medicamento_grupo
  constraint uq_medicamento_id_principio unique (id, principio_activo_id)
);

create index if not exists idx_medicamento_principio on medicamento(principio_activo_id);
create index if not exists idx_medicamento_categoria on medicamento(categoria_id);

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
create table if not exists grupo_equivalencia (
  id                  bigserial primary key,
  nombre              varchar(150) not null,
  principio_activo_id bigint       not null references principio_activo(id),
  constraint uq_grupo_id_principio unique (id, principio_activo_id)
);

create index if not exists idx_grupo_equivalencia_principio
  on grupo_equivalencia(principio_activo_id);

create table if not exists medicamento_grupo (
  medicamento_id      bigint not null primary key,
  grupo_id            bigint not null,
  principio_activo_id bigint not null,
  constraint fk_medicamento_grupo_medicamento
    foreign key (medicamento_id, principio_activo_id)
    references medicamento(id, principio_activo_id) on delete cascade,
  constraint fk_medicamento_grupo_grupo
    foreign key (grupo_id, principio_activo_id)
    references grupo_equivalencia(id, principio_activo_id) on delete cascade
);

create index if not exists idx_medicamento_grupo_grupo on medicamento_grupo(grupo_id);

-- ---------------------------------------------------------------------
-- PRODUCTO: catálogo de cada botica.
-- Eliminar = baja lógica (activo = false), para no romper futuras reservas.
-- medicamento_id es el enlace opcional al catálogo maestro (H29-H32);
-- principio_activo (texto) se mantiene: lo usan la web y la carga CSV de H11.
-- ---------------------------------------------------------------------
create table if not exists producto (
  id               bigserial primary key,
  botica_id        bigint        not null
                   references botica(id) on delete cascade,
  nombre_comercial varchar(150)  not null,
  principio_activo varchar(150)  not null,
  medicamento_id   bigint        references medicamento(id),
  presentacion     varchar(100)  not null,               -- ej. "Tableta 500 mg x 10"
  precio           numeric(10,2) not null check (precio > 0),
  stock            integer       not null check (stock >= 0),
  stock_minimo     integer       not null default 5 check (stock_minimo >= 0),  -- H12
  activo           boolean       not null default true,
  creado_en        timestamptz   not null default now(),
  actualizado_en   timestamptz   not null default now()
);

create index if not exists idx_producto_botica      on producto(botica_id);
create index if not exists idx_producto_nombre      on producto(lower(nombre_comercial));
create index if not exists idx_producto_principio   on producto(lower(principio_activo));
create index if not exists idx_producto_medicamento on producto(medicamento_id);

-- Índices trigram para la búsqueda del comprador (H29-H32). Spring Boot filtra
-- v_producto_busqueda con lower(columna) like '%texto%', por eso se indexa
-- la expresión lower(...).
create index if not exists idx_producto_nombre_trgm
  on producto using gin (lower(nombre_comercial) extensions.gin_trgm_ops);
create index if not exists idx_producto_principio_trgm
  on producto using gin (lower(principio_activo) extensions.gin_trgm_ops);

create trigger trg_producto_actualizado
before update on producto
for each row execute function public.set_actualizado_en();

-- ---------------------------------------------------------------------
-- CONSULTA_COMPARACION (H29-H32): registro de cada vez que se compara un
-- producto. usuario_id es null si la consulta fue anónima.
-- ---------------------------------------------------------------------
create table if not exists consulta_comparacion (
  id          bigserial primary key,
  producto_id bigint      not null references producto(id) on delete cascade,
  usuario_id  bigint      references usuario(id) on delete set null,
  fecha       timestamptz not null default now()
);

create index if not exists idx_consulta_comparacion_producto on consulta_comparacion(producto_id, fecha);
create index if not exists idx_consulta_comparacion_usuario  on consulta_comparacion(usuario_id);

-- ---------------------------------------------------------------------
-- FAVORITO (H29-H32): productos guardados por el comprador
-- ---------------------------------------------------------------------
create table if not exists favorito (
  usuario_id  bigint      not null references usuario(id) on delete cascade,
  producto_id bigint      not null references producto(id) on delete cascade,
  fecha       timestamptz not null default now(),
  primary key (usuario_id, producto_id)
);

create index if not exists idx_favorito_producto on favorito(producto_id);

-- ---------------------------------------------------------------------
-- VISTA para la búsqueda del comprador (H22, H23, H24):
-- solo productos activos, con stock, de boticas APROBADAS.
-- Columnas del catálogo maestro (H29-H32), al final:
--   medicamento_id, principio_activo_id: null si el producto no está enlazado.
--   registro_vigente: null si no está enlazado o el registro no tiene fecha.
--   oculto: false si no está enlazado. El filtro no lo aplica: lo decide
--   quien consulta.
-- ---------------------------------------------------------------------
create or replace view v_producto_busqueda
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
from producto p
join botica b on b.id = p.botica_id
left join medicamento m on m.id = p.medicamento_id
where p.activo = true
  and p.stock > 0
  and b.estado = 'APROBADO';

-- ---------------------------------------------------------------------
-- SEGURIDAD EN SUPABASE
-- No usamos la API automática de Supabase (todo pasa por Spring Boot y
-- Django). Activar RLS sin políticas bloquea esa API pública, mientras
-- Spring Boot y Django siguen funcionando porque se conectan como el
-- usuario dueño de las tablas.
-- ---------------------------------------------------------------------
alter table usuario  enable row level security;
alter table botica   enable row level security;
alter table producto enable row level security;

-- ---------------------------------------------------------------------
-- TOKEN_USUARIO (H17, H20): recuperación de contraseña y verificación
-- de correo. Cada token sirve una sola vez y tiene fecha de vencimiento.
-- RECUPERACION: código de 6 dígitos (fácil de escribir en el móvil), vence a
--   los 15 min y se invalida tras 5 intentos fallidos (columna intentos).
-- VERIFICACION: token largo que viaja en el enlace del correo (48 h).
-- En la BD que ya existe se agregó con v2_segunda_mitad.sql.
-- ---------------------------------------------------------------------
create table if not exists token_usuario (
  id         bigserial primary key,
  usuario_id bigint       not null references usuario(id) on delete cascade,
  token      varchar(255) not null,
  tipo       varchar(20)  not null check (tipo in ('VERIFICACION', 'RECUPERACION')),
  expira_en  timestamptz  not null,
  usado      boolean      not null default false,
  intentos   smallint     not null default 0 check (intentos >= 0),
  creado_en  timestamptz  not null default now()
);

create index if not exists idx_token_usuario_usuario  on token_usuario(usuario_id);
-- El código de 6 dígitos no es único globalmente: se busca por usuario + tipo
create index if not exists idx_token_usuario_busqueda on token_usuario(usuario_id, tipo, usado);

alter table token_usuario enable row level security;
revoke all on token_usuario from anon, authenticated;

-- Acceso solo para el rol de las aplicaciones. Va dentro de un bloque porque en
-- una BD nueva boticompara_app puede no existir todavía y el GRANT fallaría.
do $$
begin
  if exists (select 1 from pg_roles where rolname = 'boticompara_app') then
    grant select, insert, update, delete on token_usuario to boticompara_app;
    grant usage, select on sequence token_usuario_id_seq to boticompara_app;
    drop policy if exists app_acceso_total on token_usuario;
    create policy app_acceso_total on token_usuario
      for all to boticompara_app using (true) with check (true);
  end if;
end
$$;

-- ---------------------------------------------------------------------
-- SEGURIDAD DEL CATÁLOGO MAESTRO (H29-H32): mismo esquema que token_usuario.
-- RLS activado, sin acceso para la API automática de Supabase y política
-- app_acceso_total solo para boticompara_app (si el rol ya existe).
-- En la BD que ya existe se agregó con v3_catalogo_maestro.sql.
-- ---------------------------------------------------------------------
do $$
declare
  t text;
begin
  foreach t in array array['categoria', 'principio_activo', 'medicamento', 'grupo_equivalencia',
                           'medicamento_grupo', 'consulta_comparacion', 'favorito'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke all on public.%I from anon, authenticated', t);
  end loop;

  if exists (select 1 from pg_roles where rolname = 'boticompara_app') then
    foreach t in array array['categoria', 'principio_activo', 'medicamento', 'grupo_equivalencia',
                             'medicamento_grupo', 'consulta_comparacion', 'favorito'] loop
      execute format('grant select, insert, update, delete on public.%I to boticompara_app', t);
      execute format('drop policy if exists app_acceso_total on public.%I', t);
      execute format('create policy app_acceso_total on public.%I '
                     'for all to boticompara_app using (true) with check (true)', t);
    end loop;
    grant usage, select on sequence
      public.categoria_id_seq,
      public.principio_activo_id_seq,
      public.medicamento_id_seq,
      public.grupo_equivalencia_id_seq,
      public.consulta_comparacion_id_seq
    to boticompara_app;
  end if;
end
$$;
