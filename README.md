# BotiCompara

Plataforma para comparar precios de medicamentos entre boticas. El comprador busca un medicamento
por nombre comercial o por principio activo y ve en qué boticas aprobadas hay stock y a qué precio.
Los dueños registran su botica (RUC validado, ubicación y licencia de funcionamiento) y administran
su catálogo. Un administrador revisa y aprueba las boticas antes de que sus productos aparezcan en
las búsquedas.

## Arquitectura

Monorepo con cuatro aplicaciones que comparten **una sola base PostgreSQL en Supabase**:

```
  app-movil (Kotlin + Compose) ──┐
                                 ├──►  backend-usuario (Spring Boot, :8080/api) ──┐
  web-usuario (React, :5173) ────┘                                                ├──►  PostgreSQL + Storage
                                                                                  │     (Supabase)
  administrador ──────────────────►  backend-admin (Django, :8000/admin) ─────────┘
```

- La web y el móvil **solo** hablan con Spring Boot, siguiendo el contrato de [`docs/API.md`](docs/API.md).
- El administrador no usa la API: trabaja en el panel de Django, conectado directo a la BD.
- No se usa la API automática de Supabase: todas las tablas tienen RLS y solo el rol de las
  aplicaciones (`boticompara_app`) tiene acceso. Detalle en [`database/README.md`](database/README.md).
- La búsqueda del comprador lee de la vista `v_producto_busqueda`: solo productos activos, con stock
  y de boticas `APROBADO`.
- Las licencias de funcionamiento se guardan en el bucket **privado** `licencias` de Supabase Storage.
- Los correos (aprobación o rechazo de la botica, stock bajo) salen por SMTP; en desarrollo, el sandbox de Mailtrap.

## Carpetas y responsables

| Carpeta | Qué es | Tecnología | Puerto | Responsable |
|---|---|---|---|---|
| [`database/`](database/) | Esquema, migraciones, datos de prueba y RLS | PostgreSQL (Supabase) | — | Guillermo |
| [`backend-usuario/`](backend-usuario/) | API REST de compradores y dueños, JWT | Spring Boot 3 (Java 17, Maven) | 8080 | Alexander |
| [`backend-admin/`](backend-admin/) | Panel para aprobar o rechazar boticas | Django 5.2 (Python 3.12+) | 8000 | Guillermo |
| [`web-usuario/`](web-usuario/) | Panel web del dueño de botica | React 19 + Vite | 5173 | Guillermo |
| `app-movil/` | App Android del comprador | Kotlin + Jetpack Compose | — | Mijael |
| [`docs/`](docs/) | Contrato de la API y colección Postman | Markdown | — | Guillermo |

> **Móvil:** el código de la app va en `app-movil/` y llega a `main` con el PR de Mijael
> (ramas `feature/movil-auth` y `feature/movil-busqueda`). La carpeta [`movil/`](movil/) solo tiene su README.

## Cómo levantar el proyecto

La BD ya está creada en Supabase. Para una base nueva, el orden de los scripts está en
[`database/README.md`](database/README.md).

Las variables de entorno están en [`.env.example`](.env.example) (BD, `JWT_SECRET`, Supabase Storage
y correo). Copiarlo como `.env` donde indica cada módulo y completarlo con los datos que Guillermo
comparte por privado. **Nunca subir un `.env` ni claves al repositorio.**

### 1. API Spring Boot (`backend-usuario/`) → http://localhost:8080/api

Requisitos: Java 17+ y Maven 3.9+. El `.env` va **dentro de `backend-usuario/`**.

```bash
cd backend-usuario
cp ../.env.example .env        # y completarlo
mvn spring-boot:run            # o ./mvnw spring-boot:run
```

### 2. Panel de administración Django (`backend-admin/`) → http://localhost:8000/admin/

Requisitos: Python 3.12+. Lee el `.env` de la **raíz del repo**.

```bash
cp .env.example .env           # en la raíz, y completarlo
cd backend-admin
python -m venv venv
source venv/bin/activate       # Windows: venv\Scripts\activate
pip install -r requirements.txt
python manage.py migrate       # solo tablas internas de Django
python manage.py createsuperuser
python manage.py runserver
```

### 3. Web del dueño de botica (`web-usuario/`) → http://localhost:5173

Requisitos: Node.js 20+. El puerto 5173 es el origen permitido por CORS en la API.

```bash
cd web-usuario
npm install
cp .env.example .env           # VITE_USE_MOCK=false para usar la API real de :8080
npm run dev
```

Con `VITE_USE_MOCK=true` la web funciona sin backend, con datos simulados en memoria.

### 4. App móvil (`app-movil/`)

Abrir `app-movil/` en Android Studio y ejecutarla en un emulador. Desde el emulador, la API local
está en `http://10.0.2.2:8080/api`.

## Usuarios de prueba

Datos de [`database/seed.sql`](database/seed.sql). Contraseña de todos: **`Boti2026!`**

| Correo | Rol | Situación |
|---|---|---|
| rosa@boticasalud.pe | DUENO_BOTICA | Botica Salud Total, APROBADO |
| jorge@farmavida.pe | DUENO_BOTICA | FarmaVida, APROBADO (tiene un producto agotado: stock bajo) |
| lucia@boticanueva.pe | DUENO_BOTICA | Botica Nueva Era, PENDIENTE (para probar Django) |
| carlos@correo.com | COMPRADOR | Para probar el móvil |

Los administradores de Django no están en el seed: se crean con `createsuperuser`.

## Estado del Sprint 1

| Historia | Qué | Estado |
|---|---|---|
| H1, H2 | Registro del dueño y de su botica, con validación de RUC | Hecho (API y web) |
| H3 | Ubicación de la botica en un mapa | Hecho (API, web y admin) |
| H4 | Licencia de funcionamiento en Storage privado | Hecho (BD, API, web y admin) |
| H5 | Estado de la solicitud de la botica | Hecho (API y web) |
| H6 | Aviso al dueño cuando su botica se aprueba o se rechaza | Hecho (correo desde el admin y aviso en la web) |
| H7 | Corregir y reenviar una botica rechazada | Hecho (API y web) |
| H8, H9, H10 | Agregar, editar y eliminar productos | Hecho (API y web) |
| H11 | Carga masiva de productos por CSV | Hecho (API y web) |
| H12 | Stock mínimo, filtro y correo diario de stock bajo | Hecho (BD, API y web) |
| H13, H14 | Ver el catálogo propio y buscar en él | Hecho (API y web) |
| H15, H18 | Registro e inicio de sesión del comprador | API hecha; móvil hecho en `feature/movil-auth`, pendiente de integrar |
| H16 | Ver y editar el perfil | API hecha; móvil hecho en `feature/movil-auth`, pendiente de integrar |
| H17 | Recuperar la contraseña con código de 6 dígitos | Hecho en `feature/backend-cuentas`, pendiente de integrar (la BD ya está lista) |
| H19 | Eliminar la cuenta | **Pendiente** (`DELETE /usuarios/me` aún no está en la API) |
| H20 | Verificación del correo al registrarse | Hecho en `feature/backend-cuentas`, pendiente de integrar (la BD ya está lista) |
| H21 | No pedir datos de salud | Hecho (ver [Privacidad](#privacidad-h21)) |
| H22, H23, H24 | Buscar por nombre o principio activo y ordenar por precio | API hecha; móvil hecho en `feature/movil-busqueda`, pendiente de integrar |

## Privacidad (H21)

BotiCompara **no pide datos de salud ni historial clínico**. Verificado en el Sprint 1:

- **Web (`web-usuario/`):** el registro de cuenta pide solo nombre, correo, teléfono (opcional) y
  contraseña; el registro de botica, solo datos comerciales (nombre, RUC, razón social, dirección,
  distrito, teléfono, ubicación y licencia de funcionamiento). Debajo del registro se muestra
  "BotiCompara no te pide datos de salud ni historial clínico."
- **API ([`docs/API.md`](docs/API.md)):** `POST /auth/registro` recibe nombre, correo, contraseña,
  teléfono y rol, y deja escrito que nunca se piden datos de salud.
- **Base de datos:** la tabla `usuario` de [`database/schema.sql`](database/schema.sql) no tiene
  columnas de salud.
- **Móvil:** se verificará cuando el código de `app-movil/` se integre a `main`.

## Regla de la base de datos

**La estructura de la BD se define solo en [`database/schema.sql`](database/schema.sql)**, que es la
estructura completa para una base nueva. Los cambios sobre la base que ya existe van en un script de
migración aparte (por ejemplo `database/v2_segunda_mitad.sql`). Spring Boot usa `ddl-auto=validate` y
Django usa modelos `managed = False`, así que ninguno crea ni altera tablas. Si necesitas un cambio en
la BD, abre un Pull Request y avisa a Guillermo, que lo aplica en Supabase.

## Ramas

`main` siempre debe funcionar. Cada grupo de historias se trabaja en una rama `feature/...` y entra a
`main` por Pull Request; nunca hay commits directos a `main`.

Ramas con trabajo pendiente de integrar:

| Rama | Contenido | Responsable |
|---|---|---|
| `feature/backend-cuentas` | Recuperación de contraseña (H17) y verificación de correo (H20) | Alexander |
| `feature/movil-auth` | Registro y login en la app | Mijael |
| `feature/movil-busqueda` | Búsqueda y comparación de precios | Mijael |

## Formato de commits

En español, cortos y explicativos:

```
tipo(capa): qué se hizo
```

- **tipo:** `feat` (funcionalidad), `fix` (corrección), `docs` (documentación), `style` (formato o estilo),
  `refactor`, `test`, `build` (dependencias o configuración). Nunca usar `chore`.
- **capa:** `bd`, `api`, `backend`, `admin`, `web`, `movil`, `estructura`

Ejemplos:

```
feat(backend): agrega login con JWT
fix(movil): corrige orden de resultados por precio
docs(api): documenta errores de /productos
```
