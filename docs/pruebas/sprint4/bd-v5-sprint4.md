# Evidencia: script de BD v5 del Sprint 4

- **Fecha:** 12/11/2026
- **Script:** `database/v5_sprint4.sql` (H67, H72-H94)
- **Dónde:** SQL Editor de Supabase, proyecto `uaipxaugvuhknhzwguqs`, como `postgres` (opción "Run without RLS").
- **Responsable:** Guillermo
- **Verificación:** consultas de solo lectura por MCP de Supabase, después de la corrida real.

## Qué agrega el script

| Bloque | Historias | Cambio |
|---|---|---|
| `usuario` | H67 | `google_uid varchar(128)` único; `password_hash` pasa a aceptar NULL; CHECK `chk_usuario_credencial` (contraseña o Google) |
| `usuario` | H91-H93 | `suspendido_hasta timestamptz` y `motivo_estado text`; CHECK `chk_usuario_baja_sin_suspension` y `chk_usuario_motivo_suspension`. `activo` sigue mandando, sin columna `estado` |
| `reserva` | H72-H84 | Tabla nueva, índices en `usuario_id`, `producto_id`, `estado` y parcial en `vence_en`, trigger `actualizado_en` |
| `resena` | H85-H89 | Tabla nueva, `UNIQUE (usuario_id, botica_id)`, índice en `botica_id`, trigger `actualizado_en` |
| `usuario_accion` | H94 | Tabla nueva, índice en `(usuario_id, fecha)` |
| `v_producto_busqueda` | H91-H93 | `join usuario`: oculta los productos de dueños dados de baja o suspendidos. Mismas 17 columnas |
| Seguridad | H72-H94 | RLS, política `app_acceso_total` y grants para `boticompara_app` en las 3 tablas y sus secuencias |

Estado derivado del usuario:

| Estado | Condición |
|---|---|
| ACTIVO | `activo and (suspendido_hasta is null or suspendido_hasta <= now())` |
| SUSPENDIDO | `activo and suspendido_hasta > now()` |
| BAJA | `not activo` |

## Antes de correr el script

| Comprobación | Resultado |
|---|---|
| Tablas `reserva`, `resena`, `usuario_accion` | No existían |
| `v_producto_busqueda` | 17 columnas, `security_invoker=true`, `SELECT` para `boticompara_app`, **16 filas** |
| `boticompara_app` puede leer `usuario` (necesario por `security_invoker`) | Sí, grant `SELECT` y política `app_acceso_total` |
| Usuarios | 6, todos con `password_hash` y `activo = true` |

## Prueba en seco

Se reemplazó el `commit;` final por `do $$ begin raise exception 'PRUEBA_EN_SECO'; end $$;`.

| Resultado | Detalle |
|---|---|
| Único error | `ERROR: P0001: PRUEBA_EN_SECO` |
| Se comprobó por MCP que no se aplicó nada | Sin `usuario.google_uid`, sin la tabla `reserva`, la vista con 16 filas |

## Corrida real

Script con `commit;` al final: **sin errores**.

## Verificación (solo lecturas por MCP)

### 1. Columnas de `usuario`

```sql
select column_name, data_type, character_maximum_length, is_nullable, column_default
from information_schema.columns
where table_schema = 'public' and table_name = 'usuario'
  and column_name in ('password_hash', 'activo', 'google_uid', 'suspendido_hasta', 'motivo_estado');
```

| Columna | Tipo | Nulo | Default |
|---|---|---|---|
| `password_hash` | varchar(255) | **YES** (antes NO) | — |
| `activo` | boolean | NO | true |
| `google_uid` | varchar(128) | YES | — |
| `suspendido_hasta` | timestamptz | YES | — |
| `motivo_estado` | text | YES | — |

### 2. Restricciones nuevas de `usuario`

```sql
select conname, pg_get_constraintdef(oid)
from pg_constraint
where conrelid = 'public.usuario'::regclass
  and conname in ('uq_usuario_google_uid', 'chk_usuario_credencial',
                  'chk_usuario_baja_sin_suspension', 'chk_usuario_motivo_suspension');
```

| Restricción | Definición |
|---|---|
| `uq_usuario_google_uid` | `UNIQUE (google_uid)` |
| `chk_usuario_credencial` | `CHECK (password_hash IS NOT NULL OR google_uid IS NOT NULL)` |
| `chk_usuario_baja_sin_suspension` | `CHECK (activo OR suspendido_hasta IS NULL)` |
| `chk_usuario_motivo_suspension` | `CHECK (suspendido_hasta IS NULL OR motivo_estado IS NOT NULL)` |

### 3. Columnas de las tablas nuevas

```sql
select table_name, column_name, data_type, is_nullable, column_default
from information_schema.columns
where table_schema = 'public' and table_name in ('reserva', 'resena', 'usuario_accion')
order by table_name, ordinal_position;
```

**reserva** (12 columnas)

| Columna | Tipo | Nulo | Default |
|---|---|---|---|
| `id` | bigint | NO | `nextval('reserva_id_seq')` |
| `codigo` | varchar(20) | NO | — |
| `usuario_id` | bigint | NO | — |
| `producto_id` | bigint | NO | — |
| `cantidad` | integer | NO | — |
| `precio_unitario` | numeric(10,2) | NO | — |
| `estado` | varchar(20) | NO | `'PENDIENTE'` |
| `motivo_rechazo` | text | YES | — |
| `vence_en` | timestamptz | NO | — |
| `recordatorio_enviado` | boolean | NO | false |
| `creado_en` | timestamptz | NO | now() |
| `actualizado_en` | timestamptz | NO | now() |

**resena** (11 columnas)

| Columna | Tipo | Nulo | Default |
|---|---|---|---|
| `id` | bigint | NO | `nextval('resena_id_seq')` |
| `botica_id` | bigint | NO | — |
| `usuario_id` | bigint | NO | — |
| `estrellas` | smallint | NO | — |
| `comentario` | varchar(500) | YES | — |
| `respuesta` | varchar(500) | YES | — |
| `respondida_en` | timestamptz | YES | — |
| `oculta` | boolean | NO | false |
| `motivo_ocultamiento` | text | YES | — |
| `creado_en` | timestamptz | NO | now() |
| `actualizado_en` | timestamptz | NO | now() |

**usuario_accion** (6 columnas)

| Columna | Tipo | Nulo | Default |
|---|---|---|---|
| `id` | bigint | NO | `nextval('usuario_accion_id_seq')` |
| `usuario_id` | bigint | NO | — |
| `accion` | varchar(20) | NO | — |
| `motivo` | text | YES | — |
| `realizado_por` | varchar(150) | NO | — |
| `fecha` | timestamptz | NO | now() |

### 4. Restricciones de las tablas nuevas

```sql
select conrelid::regclass, conname, contype, pg_get_constraintdef(oid)
from pg_constraint
where conrelid in ('public.reserva'::regclass, 'public.resena'::regclass, 'public.usuario_accion'::regclass);
```

| Tabla | Restricción | Definición |
|---|---|---|
| reserva | `reserva_pkey` | `PRIMARY KEY (id)` |
| reserva | `uq_reserva_codigo` | `UNIQUE (codigo)` |
| reserva | `chk_reserva_cantidad` | `CHECK (cantidad > 0)` |
| reserva | `chk_reserva_precio` | `CHECK (precio_unitario > 0)` |
| reserva | `chk_reserva_estado` | `estado` en PENDIENTE, CONFIRMADA, LISTA, ENTREGADA, RECHAZADA, CANCELADA, VENCIDA |
| reserva | `chk_reserva_motivo_rechazo` | `CHECK (estado <> 'RECHAZADA' OR motivo_rechazo IS NOT NULL)` |
| reserva | `reserva_usuario_id_fkey` | `FOREIGN KEY (usuario_id) REFERENCES usuario(id)` |
| reserva | `reserva_producto_id_fkey` | `FOREIGN KEY (producto_id) REFERENCES producto(id)` |
| resena | `resena_pkey` | `PRIMARY KEY (id)` |
| resena | `uq_resena_usuario_botica` | `UNIQUE (usuario_id, botica_id)` |
| resena | `chk_resena_estrellas` | `CHECK (estrellas >= 1 AND estrellas <= 5)` |
| resena | `chk_resena_respuesta` | `CHECK ((respuesta IS NULL) = (respondida_en IS NULL))` |
| resena | `chk_resena_motivo_ocultamiento` | `CHECK (NOT oculta OR motivo_ocultamiento IS NOT NULL)` |
| resena | `resena_botica_id_fkey` | `FOREIGN KEY (botica_id) REFERENCES botica(id)` |
| resena | `resena_usuario_id_fkey` | `FOREIGN KEY (usuario_id) REFERENCES usuario(id)` |
| usuario_accion | `usuario_accion_pkey` | `PRIMARY KEY (id)` |
| usuario_accion | `chk_usuario_accion_accion` | `accion` en SUSPENDER, REACTIVAR, DAR_BAJA |
| usuario_accion | `chk_usuario_accion_motivo` | `CHECK (accion = 'REACTIVAR' OR motivo IS NOT NULL)` |
| usuario_accion | `usuario_accion_usuario_id_fkey` | `FOREIGN KEY (usuario_id) REFERENCES usuario(id)` |

Las 5 claves foráneas quedan **sin `ON DELETE`** (no action): no se puede borrar un usuario, producto o botica que tenga reservas, reseñas o acciones.

### 5. Índices

```sql
select tablename, indexname, indexdef from pg_indexes
where schemaname = 'public'
  and (tablename in ('reserva', 'resena', 'usuario_accion') or indexname = 'uq_usuario_google_uid');
```

| Tabla | Índice | Definición |
|---|---|---|
| reserva | `idx_reserva_usuario` | btree (`usuario_id`) |
| reserva | `idx_reserva_producto` | btree (`producto_id`) |
| reserva | `idx_reserva_estado` | btree (`estado`) |
| reserva | `idx_reserva_vence_en` | btree (`vence_en`) **WHERE estado IN ('PENDIENTE', 'CONFIRMADA', 'LISTA')** |
| reserva | `uq_reserva_codigo` | único (`codigo`) |
| reserva | `reserva_pkey` | único (`id`) |
| resena | `idx_resena_botica` | btree (`botica_id`) |
| resena | `uq_resena_usuario_botica` | único (`usuario_id`, `botica_id`) |
| resena | `resena_pkey` | único (`id`) |
| usuario_accion | `idx_usuario_accion_usuario` | btree (`usuario_id`, `fecha`) |
| usuario_accion | `usuario_accion_pkey` | único (`id`) |
| usuario | `uq_usuario_google_uid` | único (`google_uid`) |

### 6. RLS, políticas, grants y triggers

```sql
select c.relname, c.relrowsecurity,
  (select string_agg(...) from pg_policies ...)                        as politicas,
  (select string_agg(privilege_type, ',') from information_schema.role_table_grants
     where table_name = c.relname and grantee = 'boticompara_app')    as grants_app,
  (select string_agg(distinct grantee, ',') from information_schema.role_table_grants
     where table_name = c.relname and grantee in ('anon', 'authenticated')) as grants_anon_auth,
  (select string_agg(tgname, ',') from pg_trigger where tgrelid = c.oid and not tgisinternal) as triggers
from pg_class c
where c.relnamespace = 'public'::regnamespace and c.relname in ('reserva', 'resena', 'usuario_accion');
```

| Tabla | RLS | Política | Grants `boticompara_app` | `anon` / `authenticated` | Trigger |
|---|---|---|---|---|---|
| reserva | true | `app_acceso_total` ALL `boticompara_app` using true check true | SELECT, INSERT, UPDATE, DELETE | sin acceso | `trg_reserva_actualizado` |
| resena | true | `app_acceso_total` ALL `boticompara_app` using true check true | SELECT, INSERT, UPDATE, DELETE | sin acceso | `trg_resena_actualizado` |
| usuario_accion | true | `app_acceso_total` ALL `boticompara_app` using true check true | SELECT, INSERT, UPDATE, DELETE | sin acceso | — (no tiene `actualizado_en`) |

### 7. Secuencias

```sql
select s, has_sequence_privilege('boticompara_app', 'public.' || s, 'USAGE'),
          has_sequence_privilege('boticompara_app', 'public.' || s, 'SELECT')
from unnest(array['reserva_id_seq', 'resena_id_seq', 'usuario_accion_id_seq']) s;
```

| Secuencia | USAGE | SELECT |
|---|---|---|
| `reserva_id_seq` | true | true |
| `resena_id_seq` | true | true |
| `usuario_accion_id_seq` | true | true |

### 8. Vista `v_producto_busqueda`

```sql
select reloptions from pg_class where oid = 'public.v_producto_busqueda'::regclass;
select count(*) from public.v_producto_busqueda;
select pg_get_viewdef('public.v_producto_busqueda'::regclass, true);
```

| Comprobación | Antes | Después |
|---|---|---|
| `reloptions` | `security_invoker=true` | `security_invoker=true` |
| Columnas | 17 | 17 (mismo nombre, orden y tipo) |
| `SELECT` para `boticompara_app` | sí | sí |
| Filas | 16 | **16** |
| `join usuario u` y filtro `u.activo and (u.suspendido_hasta is null or u.suspendido_hasta <= now())` | no | **sí** |

## Pendientes para el equipo

**Alexander (Spring Boot)**

- Revisar `suspendido_hasta` en el login (`AuthService`) y en `JwtAuthFilter`: hoy solo miran `activo`, así que un usuario suspendido todavía puede entrar.
- Poner `google_uid = null` en `UsuarioService.eliminar()`. Si no, el UNIQUE impide que esa cuenta de Google vuelva a registrarse.
- Las consultas que no usan la vista (detalle de producto, favoritos, reservas) no ocultan los productos de dueños suspendidos o dados de baja. Lo decide Alexander.

**Guillermo (Django)**

- Modelos `managed = False` de `Reserva`, `Resena` y `UsuarioAccion`, y los campos nuevos de `Usuario` (`google_uid`, `suspendido_hasta`, `motivo_estado`; `password_hash` con `null=True`).
- Al dar de baja a alguien suspendido, poner `suspendido_hasta = null` antes de `activo = false` (o en el mismo UPDATE), por el CHECK `chk_usuario_baja_sin_suspension`.
