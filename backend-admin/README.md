# backend-admin

Panel de administración: revisión de boticas pendientes (aprobar o rechazar con motivo) y consulta de usuarios y productos.

- **Responsable:** Guillermo
- **Tecnología:** Django (Python), Django Admin, psycopg
- **Puerto local:** 8000 (`/admin`)
- **Rama:** `feature/admin-boticas`

Notas:
- Los administradores usan el login propio de Django (`createsuperuser`), no la tabla `usuario`.
- Los modelos de `usuario`, `botica` y `producto` deben ser `managed = False`: las tablas las define
  [`database/schema.sql`](../database/schema.sql).
- Al rechazar una botica, `motivo_rechazo` es obligatorio (lo exige un `check` en la BD).
- Después del primer `migrate` hay que ejecutar
  [`database/rls_todas_las_tablas.sql`](../database/rls_todas_las_tablas.sql) en Supabase.
