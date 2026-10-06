-- =====================================================================
-- BotiCompara - Esquema de base de datos (PostgreSQL / Supabase)
-- Única fuente de verdad de la estructura de la BD.
-- Administrador: Guillermo. Nadie más crea ni modifica tablas.
-- Cambios: abrir un Pull Request a este archivo.
-- =====================================================================

-- En Supabase las extensiones viven en el esquema "extensions"
create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;

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
-- PRODUCTO: catálogo de cada botica.
-- Eliminar = baja lógica (activo = false), para no romper futuras reservas.
-- ---------------------------------------------------------------------
create table if not exists producto (
  id               bigserial primary key,
  botica_id        bigint        not null
                   references botica(id) on delete cascade,
  nombre_comercial varchar(150)  not null,
  principio_activo varchar(150)  not null,
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

create trigger trg_producto_actualizado
before update on producto
for each row execute function public.set_actualizado_en();

-- ---------------------------------------------------------------------
-- VISTA para la búsqueda del comprador (H22, H23, H24):
-- solo productos activos, con stock, de boticas APROBADAS.
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
  b.telefono         as botica_telefono
from producto p
join botica b on b.id = p.botica_id
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
