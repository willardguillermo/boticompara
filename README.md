# BotiCompara

Plataforma para comparar precios de medicamentos entre boticas. El comprador busca un medicamento
por nombre comercial o por principio activo y ve en qué boticas aprobadas hay stock y a qué precio.
Los dueños registran su botica (con RUC validado) y administran su catálogo. Un administrador
revisa y aprueba las boticas antes de que sus productos aparezcan en las búsquedas.

## Arquitectura

Monorepo con cuatro aplicaciones que comparten **una sola base PostgreSQL en Supabase**:

```
  movil (Kotlin)  ──────┐
                        ├──►  backend-usuario (Spring Boot, /api)  ──┐
  web-usuario (React) ──┘                                            ├──►  PostgreSQL (Supabase)
                                                                     │
  administrador  ─────────►  backend-admin (Django, /admin)  ────────┘
```

| Carpeta | Qué es | Tecnología | Responsable |
|---|---|---|---|
| [`backend-usuario/`](backend-usuario/) | API REST de compradores y dueños | Spring Boot (Maven), JWT | Colaborador 1 |
| [`backend-admin/`](backend-admin/) | Panel de administración (aprobar boticas) | Django | Guillermo |
| [`web-usuario/`](web-usuario/) | Web del dueño de botica | React + Vite | Guillermo |
| [`movil/`](movil/) | App del comprador | Kotlin + Jetpack Compose | Colaborador 2 |
| [`database/`](database/) | Esquema, datos de prueba y RLS | PostgreSQL (Supabase) | Guillermo |
| [`docs/`](docs/) | Contrato de la API | Markdown | Guillermo |

- La web y el móvil **solo** hablan con Spring Boot, siguiendo [`docs/API.md`](docs/API.md).
- No se usa la API automática de Supabase: todas las tablas tienen RLS activado sin políticas.
- La búsqueda del comprador lee de la vista `v_producto_busqueda`, que muestra solo productos
  activos, con stock y de boticas `APROBADO`.

## Reparto

- **Guillermo:** base de datos (Supabase), panel Django y web React.
- **Colaborador 1:** backend Spring Boot.
- **Colaborador 2:** app móvil Kotlin Jetpack Compose.

## Puesta en marcha

1. Copiar [`.env.example`](.env.example) como `.env` en el módulo que lo necesite y completar los
   datos de Supabase (Guillermo los comparte por privado). **Nunca subir `.env`.**
2. La BD ya está creada en Supabase. Para una BD nueva, el orden está en
   [`database/README.md`](database/README.md).
3. Cada módulo explica cómo levantarse en su propio README.

## Regla de la base de datos

**Nadie crea ni modifica tablas fuera de [`database/schema.sql`](database/schema.sql).**
Spring Boot usa `ddl-auto=validate` y Django usa modelos `managed = False` para las tablas del
proyecto. Si necesitas un cambio en la BD, abre un Pull Request a `schema.sql` y avisa a Guillermo,
que lo aplica en Supabase.

## Usuarios de prueba

Contraseña de todos: `Boti2026!`. El detalle está en [`docs/API.md`](docs/API.md#usuarios-de-prueba-seedsql).

| Correo | Rol | Situación |
|---|---|---|
| rosa@boticasalud.pe | DUENO_BOTICA | Botica Salud Total, APROBADO |
| jorge@farmavida.pe | DUENO_BOTICA | FarmaVida, APROBADO |
| lucia@boticanueva.pe | DUENO_BOTICA | Botica Nueva Era, PENDIENTE |
| carlos@correo.com | COMPRADOR | Para probar el móvil |

## Ramas

`main` siempre debe funcionar. Cada uno trabaja en su rama y entra a `main` por Pull Request.

| Rama | Contenido | Responsable |
|---|---|---|
| `feature/backend-auth` | Registro, login, JWT y perfil | Colaborador 1 |
| `feature/backend-catalogo` | Botica, validación de RUC, productos y búsqueda | Colaborador 1 |
| `feature/movil-auth` | Registro y login en la app | Colaborador 2 |
| `feature/movil-busqueda` | Búsqueda y comparación de precios | Colaborador 2 |
| `feature/admin-boticas` | Aprobación y rechazo de boticas en Django | Guillermo |
| `feature/web-catalogo` | Registro de botica y catálogo en la web | Guillermo |

## Formato de commits

En español, cortos y explicativos:

```
tipo(capa): qué se hizo
```

- **tipo:** `feat` (funcionalidad), `fix` (corrección), `docs` (documentación), `refactor`,
  `test`, `chore` (configuración o estructura)
- **capa:** `bd`, `api`, `backend`, `admin`, `web`, `movil`, `estructura`

Ejemplos:

```
feat(backend): agrega login con JWT
fix(movil): corrige orden de resultados por precio
docs(api): documenta errores de /productos
```
