# database

Esquema único de la base PostgreSQL (Supabase) que comparten todos los módulos.
Estos archivos son los oficiales y **ya están aplicados en Supabase**.

- **Responsable:** Guillermo (único que crea o modifica tablas)
- **Tecnología:** PostgreSQL en Supabase, extensión `pgcrypto` (esquema `extensions`)

| Archivo | Uso |
|---|---|
| `schema.sql` | **Estructura completa** de la BD: tablas `usuario`, `botica`, `producto` y `token_usuario`, catálogo maestro (`categoria`, `principio_activo`, `medicamento`, `grupo_equivalencia`, `medicamento_grupo`), `consulta_comparacion` y `favorito`, tablas del Sprint 3 (`alerta_precio`, `busqueda`, `botica_revision`), restricciones (RUC, estado, motivo de rechazo, precio y stock), índices (incluidos los trigram de `pg_trgm` y los únicos `uq_categoria_nombre` sobre `lower(nombre)` y `uq_medicamento_registro`, parcial, sobre `registro_sanitario` no nulo), trigger de `actualizado_en`, vista `v_producto_busqueda` (`security_invoker`) y RLS. Una base nueva queda completa solo con este archivo. Se ejecuta **una sola vez** sobre una BD vacía: si se repite, falla al crear los triggers. |
| `seed.sql` | Datos de prueba: 4 usuarios, 3 boticas y 14 productos. Se puede re-ejecutar (hace `truncate ... restart identity` al inicio). Contraseña de todos: `Boti2026!` (BCrypt `$2a$`, compatible con Spring Security). |
| `v2_segunda_mitad.sql` | **Solo para actualizar la base que ya existe** (creada con una versión anterior de `schema.sql`): agrega la tabla `token_usuario` (H17, H20) con RLS y política para `boticompara_app`, y crea el bucket privado `licencias` en Storage (H4). Se puede re-ejecutar. |
| `v3_catalogo_maestro.sql` | **Solo para actualizar la base que ya existe** (H29-H32): extensión `pg_trgm`, tablas del catálogo maestro (`categoria`, `principio_activo`, `medicamento`, `grupo_equivalencia`, `medicamento_grupo`), `consulta_comparacion` y `favorito`, columna opcional `producto.medicamento_id`, nombre de categoría único sin distinguir mayúsculas (`uq_categoria_nombre`), registro sanitario único cuando existe (`uq_medicamento_registro`), índices trigram de búsqueda, RLS con política `app_acceso_total` para `boticompara_app` y `v_producto_busqueda` con las columnas nuevas `medicamento_id`, `principio_activo_id`, `registro_vigente`, `oculto`, `botica_latitud` y `botica_longitud` (H27) al final. Se puede re-ejecutar. |
| `v4_sprint3.sql` | **Solo para actualizar la base que ya existe** (Sprint 3), después de `v3_catalogo_maestro.sql`: columnas `forma_farmaceutica`, `titular` y `codigo_digemid` en `medicamento` (H46, con índice no único `idx_medicamento_codigo_digemid`), `precio_objetivo` y `ultimo_precio_avisado` en `favorito` (H60, H61), `notificaciones_precio` en `usuario` (H66), tablas `alerta_precio` (H59, H61), `busqueda` (H62) y `botica_revision` (H47) con RLS y política `app_acceso_total` para `boticompara_app`, carga inicial de `botica_revision` (una fila por botica ya aprobada o rechazada) y los correos `@boticompara.nissidev.dev` de los usuarios de prueba. Va en una transacción y trae al final consultas de verificación comentadas. Se puede re-ejecutar sin duplicar. |
| `seed_v3_catalogo.sql` | Datos de prueba del catálogo maestro (H32, H36): categorías con tilde, medicamentos TEST-004 a TEST-012 (Loratadina Genérica con registro vencido para H39), grupos de ibuprofeno, amoxicilina y losartán (omeprazol, Nexium, aspirina y loratadina quedan sin grupo para H41) y enlace `producto.medicamento_id` por nombre comercial. Se ejecuta después de `v3_catalogo_maestro.sql` y `seed.sql`. Se puede re-ejecutar sin duplicar. |
| `rls_todas_las_tablas.sql` | Activa RLS en todas las tablas de `public`, incluidas las que crea Django. Se puede ejecutar varias veces. |

Orden en una base nueva: `schema.sql` → `seed.sql` → (Django `python manage.py migrate`) → `rls_todas_las_tablas.sql`.
El bucket `licencias` no es una tabla: vive en Storage y se crea con la última sección de `v2_segunda_mitad.sql`
(también en una base nueva).

Base actual (ya tiene `schema.sql` aplicado): ejecutar `v2_segunda_mitad.sql`, luego `v3_catalogo_maestro.sql` y
luego `v4_sprint3.sql`.

## Rol de conexión y RLS

- Spring Boot y Django se conectan con el rol **`boticompara_app`** (en el pooler de Supabase el
  usuario es `boticompara_app.<ref-del-proyecto>`). La contraseña la comparte Guillermo por privado
  y va solo en el `.env`, nunca en el repositorio.
- `usuario`, `botica` y `producto` pertenecen a `postgres`. Tienen RLS activado y una política
  `app_acceso_total` (`for all`) **solo para `boticompara_app`**. Es intencional: esa política no está en
  `schema.sql` y se administra en Supabase. Las excepciones son `token_usuario`, las tablas del catálogo
  maestro (H29-H32) y las del Sprint 3 (`alerta_precio`, `busqueda`, `botica_revision`): su política sí está en `schema.sql`, dentro de un bloque que solo se aplica si el
  rol `boticompara_app` ya existe.
- Las tablas internas de Django (`auth_*`, `django_*`) las crea `migrate` y **pertenecen a
  `boticompara_app`**, por eso las puede usar sin políticas aunque tengan RLS.
- `anon` y `authenticated` (la API automática de Supabase) no tienen acceso a ninguna tabla ni a la vista.
- `rls_todas_las_tablas.sql` debe ejecutarlo alguien con permisos sobre **todas** las tablas (por
  ejemplo `postgres` desde el SQL Editor de Supabase). Con `boticompara_app` falla, porque ese rol no
  es dueño de `usuario`, `botica` ni `producto`.

Comprobación rápida después del seed:

```sql
select nombre_comercial, precio, botica_nombre
from v_producto_busqueda
where lower(principio_activo) like '%paracetamol%'
order by precio;
-- 4 filas; la primera: Paracetamol Genérico, 2.20, FarmaVida
```

**Regla:** ninguna tabla se crea o modifica fuera de `schema.sql`. Los cambios se proponen por Pull Request a este archivo.
