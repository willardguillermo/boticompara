# database

Esquema único de la base PostgreSQL (Supabase) que comparten todos los módulos.
Estos archivos son los oficiales y **ya están aplicados en Supabase**.

- **Responsable:** Guillermo (único que crea o modifica tablas)
- **Tecnología:** PostgreSQL en Supabase, extensión `pgcrypto` (esquema `extensions`)

| Archivo | Uso |
|---|---|
| `schema.sql` | Tablas `usuario`, `botica` y `producto`, restricciones (RUC, estado, motivo de rechazo, precio y stock), índices, trigger de `actualizado_en`, vista `v_producto_busqueda` (`security_invoker`) y RLS. Se ejecuta **una sola vez** sobre una BD vacía: si se repite, falla al crear los triggers. |
| `seed.sql` | Datos de prueba: 4 usuarios, 3 boticas y 14 productos. Se puede re-ejecutar (hace `truncate ... restart identity` al inicio). Contraseña de todos: `Boti2026!` (BCrypt `$2a$`, compatible con Spring Security). |
| `rls_todas_las_tablas.sql` | Activa RLS en todas las tablas de `public`, incluidas las que crea Django. Se puede ejecutar varias veces. |

Orden: `schema.sql` → `seed.sql` → (Django `python manage.py migrate`) → `rls_todas_las_tablas.sql`.

Comprobación rápida después del seed:

```sql
select nombre_comercial, precio, botica_nombre
from v_producto_busqueda
where lower(principio_activo) like '%paracetamol%'
order by precio;
-- 4 filas; la primera: Paracetamol Genérico, 2.20, FarmaVida
```

**Regla:** ninguna tabla se crea o modifica fuera de `schema.sql`. Los cambios se proponen por Pull Request a este archivo.
