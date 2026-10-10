# backend-admin

Panel de administración de BotiCompara: revisión de boticas pendientes (aprobar o rechazar con motivo) y consulta de usuarios y productos.

- **Responsable:** Guillermo
- **Tecnología:** Django 5.2 (Python 3.12+), Django Admin, psycopg 3, python-dotenv
- **Puerto local:** 8000 (`http://localhost:8000/admin/`)
- **Rama:** `feature/admin-boticas`

## Qué hace

| Sección | Permite |
|---|---|
| **Boticas** | Listar con filtros por estado y distrito, con columna **Licencia** (✓/✗); buscar por nombre, RUC o correo del dueño; acción en lote **"Aprobar boticas seleccionadas"**; rechazar desde el formulario (estado `RECHAZADO` + motivo obligatorio). En la ficha, **"Evidencia para la aprobación"**: enlace **"Ver licencia"** (URL firmada de Supabase Storage que vence en 5 minutos) y ubicación con enlace y mapa de OpenStreetMap. Los datos de la botica y sus productos se ven en solo lectura. No se pueden crear ni borrar boticas. |
| **Usuarios** | Solo lectura. Nunca se muestra `password_hash`. |
| **Productos** | Solo lectura, con filtros por botica y estado. |

Los modelos `Usuario`, `Botica` y `Producto` usan `managed = False`: Django **no crea ni altera** esas
tablas, que se definen solo en [`database/schema.sql`](../database/schema.sql). Los administradores
entran con el login propio de Django (`createsuperuser`), no con la tabla `usuario`.

## Puesta en marcha

Requisito: el archivo `.env` en la **raíz del repo** (copia de [`.env.example`](../.env.example))
con los datos de Supabase: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_SSLMODE`.
Para ver las licencias: `SUPABASE_URL`, `SUPABASE_SECRET_KEY` y `SUPABASE_BUCKET_LICENCIAS` (por defecto `licencias`).
Opcionales: `DJANGO_SECRET_KEY` (obligatoria fuera de desarrollo) y `DJANGO_DEBUG` (`True` por defecto).

Desde la carpeta `backend-admin/`:

```bash
# 1. Entorno virtual
python -m venv venv
venv\Scripts\activate          # Windows (PowerShell: venv\Scripts\Activate.ps1)
source venv/bin/activate       # Linux / macOS

# 2. Dependencias
pip install -r requirements.txt

# 3. Tablas internas de Django (auth_user, django_session, django_admin_log, ...).
#    No toca usuario, botica ni producto.
python manage.py migrate

# 4. Usuario administrador del panel
python manage.py createsuperuser

# 5. Servidor
python manage.py runserver
```

Abrir `http://localhost:8000/admin/`.

> **Importante:** después del **primer** `migrate`, ejecutar
> [`database/rls_todas_las_tablas.sql`](../database/rls_todas_las_tablas.sql) en el SQL Editor de
> Supabase. Django crea sus tablas sin RLS y quedarían expuestas por la API automática de Supabase.
> Repetirlo si una versión nueva de Django o una app nueva agrega tablas.
