# backend-admin

Panel de administración de BotiCompara: revisión de boticas pendientes (aprobar o rechazar con motivo) y consulta de usuarios y productos.

- **Responsable:** Guillermo
- **Tecnología:** Django 5.2 (Python 3.12+), Django Admin, psycopg 3, python-dotenv
- **Puerto local:** 8000 (`http://localhost:8000/admin/`)
- **Rama:** `feature/admin-boticas`

## Qué hace

| Sección | Permite |
|---|---|
| **Boticas** | Listar con filtros por estado y distrito, con columna **Licencia** (✓/✗); buscar por nombre, RUC o correo del dueño; acción en lote **"Aprobar boticas seleccionadas"**; rechazar desde el formulario (estado `RECHAZADO` + motivo obligatorio). En la ficha, **"Evidencia para la aprobación"**: enlace **"Ver licencia"** (URL firmada de Supabase Storage que vence en 5 minutos) y ubicación con enlace y mapa de OpenStreetMap. Los datos de la botica y sus productos se ven en solo lectura. No se pueden crear ni borrar boticas. Al aprobar o rechazar se envía un **correo al dueño** (solo si el estado cambió); si el envío falla, la revisión igual se guarda y se muestra un aviso. |
| **Usuarios** | Solo lectura. Nunca se muestra `password_hash`. |
| **Productos** | Solo lectura, con filtros por botica y estado. |

Los modelos `Usuario`, `Botica` y `Producto` usan `managed = False`: Django **no crea ni altera** esas
tablas, que se definen solo en [`database/schema.sql`](../database/schema.sql). Los administradores
entran con el login propio de Django (`createsuperuser`), no con la tabla `usuario`.

## Puesta en marcha

Requisito: el archivo `.env` en la **raíz del repo** (copia de [`.env.example`](../.env.example))
con los datos de Supabase: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_SSLMODE`.
Para ver las licencias: `SUPABASE_URL`, `SUPABASE_SECRET_KEY` y `SUPABASE_BUCKET_LICENCIAS` (por defecto `licencias`).
Para los correos al dueño: `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` (sandbox de Mailtrap, con TLS) y opcionalmente `MAIL_FROM`.
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

## Catálogo maestro (H29-H31)

La app `catalogo` administra el catálogo maestro definido en
[`database/v3_catalogo_maestro.sql`](../database/v3_catalogo_maestro.sql):

| Sección | Permite |
|---|---|
| **Categorías** | Crear, editar y buscar categorías, con el número de medicamentos de cada una. El nombre no se puede repetir (sin distinguir mayúsculas). |
| **Principios activos** | Igual que las categorías, con descripción opcional. |
| **Medicamentos** | Crear y editar el catálogo maestro: nombre comercial, presentación, concentración, principio activo, categoría, registro sanitario (único si se indica) y vencimiento del registro. Filtros por categoría y por `oculto` (lo retira de la búsqueda sin borrarlo). No deja cambiar el principio activo de un medicamento que está en un grupo de equivalencia: primero hay que sacarlo del grupo. |

En **Productos** se ve, en solo lectura, a qué medicamento del catálogo está enlazado cada uno.
No se puede borrar una categoría, un principio activo o un medicamento que esté en uso.

Al bajar esta versión, correr **una vez** (desde `backend-admin/`, con el entorno virtual activado):

```bash
python manage.py migrate
```

Solo registra la app `catalogo` y sus permisos: **no crea tablas**, porque los modelos usan
`managed = False` y las tablas ya existen en Supabase.

### Poblar los principios activos (H30)

`poblar_principios_activos` crea en `principio_activo` los nombres que ya usan los productos
**activos** de las boticas (normalizados con `initcap(trim(...))`), solo los que falten. Se puede
repetir sin duplicar nada.

```bash
# 1. Ver qué se crearía, sin escribir en la base de datos
python manage.py poblar_principios_activos --dry-run

# 2. Crear de verdad: muestra el mismo resumen y pide confirmación [s/N]
python manage.py poblar_principios_activos
```
