# BotiCompara

Plataforma para comparar precios de medicamentos entre boticas. El comprador busca por nombre
comercial o principio activo y ve en qué boticas aprobadas hay stock y a qué precio. Los dueños
registran su botica (RUC validado, ubicación opcional y licencia de funcionamiento) y administran
su catálogo. Un administrador aprueba o rechaza las boticas antes de que sus productos aparezcan
en las búsquedas.

## Monorepo

Todas las aplicaciones comparten **una sola base PostgreSQL en Supabase**.

| Carpeta | Qué es | Tecnología | Responsable |
|---|---|---|---|
| `database/` | `schema.sql`, `seed.sql`, `rls_todas_las_tablas.sql` | PostgreSQL (Supabase) | Guillermo (único que la modifica) |
| `backend-admin/` | Panel para aprobar/rechazar boticas (`/admin`, puerto 8000) | Django 5.2, modelos `managed = False` | Guillermo |
| `web-usuario/` | Panel del dueño de botica (puerto 5173) | React 19 + Vite + React Router | Guillermo |
| `backend-usuario/` | API REST `/api` (puerto 8080), JWT, `ddl-auto=validate` | Spring Boot (Maven) | Alexander |
| `movil/` y `app-movil/` | App del comprador (`app-movil/` llegará con el PR de Mijael, con el código Kotlin) | Kotlin + Jetpack Compose | Mijael |
| `docs/` | Contrato de la API (`API.md`) y colección Postman | Markdown | Guillermo |

- La web y el móvil **solo** hablan con Spring Boot. El administrador no usa la API, usa Django.
- No se usa la API automática de Supabase: RLS activado; solo el rol `boticompara_app` tiene acceso.
- La búsqueda del comprador lee de la vista `v_producto_busqueda` (productos activos, con stock,
  de boticas `APROBADO`).
- Usuarios de prueba (contraseña `Boti2026!`): `rosa@boticasalud.pe` (aprobada),
  `jorge@farmavida.pe` (aprobada), `lucia@boticanueva.pe` (pendiente), `carlos@correo.com` (comprador).

## Reglas

- **Yo soy Guillermo: trabajo SOLO en `database/`, `backend-admin/` y `web-usuario/`.**
  No modificar `backend-usuario/` (Alexander) ni `movil/` ni `app-movil/` (Mijael).
- Ninguna tabla se crea o modifica fuera de `database/schema.sql`.
- El contrato de la API es `docs/API.md`; la web debe seguirlo **exactamente** (rutas, campos en
  camelCase, códigos y formato de error). Si un endpoint cambia, primero se actualiza `API.md`.
- La web tiene modo simulado (`src/services/api.mock.js`) y real (`src/services/api.real.js`),
  elegidos con `VITE_USE_MOCK` en `src/services/index.js`. **Todo endpoint nuevo se agrega en los dos.**
  Las pantallas solo importan `api` desde `services/index.js`.
- Commits en español, cortos, formato `tipo(capa): qué se hizo (Hxx)`, por ejemplo
  `feat(web): agrega carga de licencia (H4)`. Tipos: `feat`, `fix`, `docs`, `refactor`, `test`,
  `chore`. Capas: `bd`, `api`, `backend`, `admin`, `web`, `movil`, `estructura`.
  **Sin `Co-Authored-By` ni firmas.**
- Una rama por grupo de historias; nunca commits directos a `main`. Se integra por Pull Request.
- Nunca subir `.env` ni claves (contraseñas de BD, `JWT_SECRET`, claves de Supabase).
- Antes de cada commit en la web: `npm run lint` y `npm run build` (en `web-usuario/`) sin errores.
- Explícame en español qué cambiaste y por qué.
