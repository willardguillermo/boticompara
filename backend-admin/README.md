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

## Historial de revisiones (H47)

**Boticas → Historial de revisiones** lista cada aprobación y rechazo, guardados en la tabla
`botica_revision` (definida en [`database/schema.sql`](../database/schema.sql)): fecha y hora (de Lima),
botica (enlace a su ficha), RUC, estado (verde aprobado, rojo rechazado), administrador que la hizo
y motivo del rechazo (recortado a 80 caracteres; "—" si no hay).

De dónde salen las filas:

- **Formulario de la botica:** al guardar, si el estado cambió a `APROBADO` o `RECHAZADO` se crea una
  revisión con el estado, el motivo (solo si es rechazo) y el usuario de Django que la hizo.
- **Acción en lote "Aprobar boticas seleccionadas":** una revisión por cada botica que realmente cambió
  (las que ya estaban aprobadas no).
- En ambos casos el cambio de estado y su revisión se guardan en la **misma transacción**: si falla
  el historial, la botica no queda cambiada. El correo al dueño (H6) sigue saliendo después del commit.
- Las **4 revisiones iniciales** se cargaron con la BD y no tienen revisor: se muestran como
  "Sin registro (carga inicial)".

Filtros, combinables entre sí y con la búsqueda (nombre de la botica, RUC o administrador):

- **Por estado**, en la barra lateral.
- **Por rango de fechas**, con los campos **Desde** y **Hasta** arriba de la lista. Los días se toman en
  hora de Lima e incluyen los dos extremos. Si "Desde" es posterior a "Hasta" o una fecha no es válida,
  se muestra un error y no se filtra por fecha.

El historial es de **solo lectura**: no se pueden crear, editar ni borrar revisiones. En la ficha de cada
botica se ve también su **Historial de revisiones**.

Al bajar esta versión, correr una vez `python manage.py migrate`: solo registra el modelo y sus
permisos (para que un administrador que no es superusuario pueda ver el historial); **no crea la
tabla**, que ya existe en Supabase.

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

## Carga del catálogo DIGEMID (H46)

Carga masiva del catálogo maestro desde un Excel (`.xlsx`, primera hoja) o CSV (separado por `,` o `;`,
en UTF-8 o en la codificación de Excel en Windows) con las columnas del catálogo de DIGEMID en la fila 1:

| Columna | Campo del medicamento | Obligatoria |
|---|---|---|
| Código | `codigo_digemid` (se lee como texto: `00101` conserva los ceros) | No |
| Nombre Producto | `nombre_comercial` | Sí |
| Principio Activo | `principio_activo` | Sí |
| Concentración | `concentracion` | No |
| Forma Farmacéutica | `forma_farmaceutica` | No |
| Presentación | `presentacion` | Sí |
| Reg Sanitario | `registro_sanitario` (clave de la carga) | Sí |
| Fec Expiración | `fecha_vencimiento_registro` (fecha de Excel, `dd/mm/aaaa` o `aaaa-mm-dd`; vacía = sin fecha) | No |
| Titular | `titular` | No |

Los encabezados se reconocen sin distinguir mayúsculas, tildes, espacios dobles ni puntos
(`Fec. Expiración` = `FEC EXPIRACION`). Las columnas Código y Titular pueden faltar; si falta
cualquier otra, no se importa nada.

Reglas:

- **Clave = registro sanitario** (sin distinguir mayúsculas). Si ya existe, se **actualiza** el
  medicamento (nombre, presentación, concentración, forma, titular, código, fecha y principio activo;
  no se tocan la categoría ni `oculto`). Si no existe, se **crea sin categoría** (DIGEMID no la trae).
  Un registro repetido dentro del archivo: vale la primera fila y las siguientes son error.
- **Principio activo** sin distinguir mayúsculas **ni tildes**: `LOSARTAN` usa el `Losartán` que ya
  existe. Si no existe se crea (si viene todo en mayúsculas, se guarda como `Losartan` con `str.title()`).
- Un medicamento que está en un grupo de equivalencia no puede cambiar de principio activo: esa fila
  es error ("sácalo del grupo primero").
- **Las filas con error no detienen la carga**: se informan con número de fila de Excel y motivo.
  Cada fila se guarda en su propia transacción.
- Los de fecha pasada quedan como **Vencido** en el filtro de vigencia (H34).

**Desde el admin:** Medicamentos → botón **"Importar catálogo DIGEMID"** (requiere permiso para añadir
y cambiar medicamentos). Se sube el archivo (máximo 5 MB) y, si se marca **"Solo simular (no guardar)"**,
se hace toda la carga y se deshace al final. Se muestran tarjetas con Creados, Actualizados y Filas con
error, los principios activos nuevos y la tabla de errores (Fila, Reg Sanitario, Motivo).

**Desde la consola** (en `backend-admin/`, con el entorno virtual activado):

```bash
# 1. Probar sin guardar nada
python manage.py importar_digemid catalogo/muestras/catalogo-digemid-muestra.xlsx --simulacion

# 2. Guardar de verdad
python manage.py importar_digemid ruta/al/catalogo.xlsx
```

**Archivos de muestra** en [`catalogo/muestras/`](catalogo/muestras/):

- `catalogo-digemid-muestra.xlsx`: 50 filas (44 válidas y 6 con error a propósito). Los registros
  sanitarios y códigos son **FICTICIOS**; la hoja **Leeme** explica qué debe pasar en cada fila.
  Con la BD del seed da: 41 creados, 3 actualizados (`TEST-001`, `TEST-005`, `TEST-010`), 6 filas con
  error y 21 principios activos nuevos.
- `catalogo-digemid-muestra.csv`: 10 filas, separado por `;`, en cp1252 (1 error: fecha 31/02/2029).

Pruebas del lector (sin BD):

```bash
python manage.py test catalogo
```
