# web-usuario

Aplicación web del **dueño de botica**: crea su cuenta, registra su botica (con validación de RUC), sigue el estado de aprobación y gestiona su catálogo.

- **Responsable:** Guillermo
- **Tecnología:** React 19 + Vite + React Router
- **Puerto local:** 5173 (es el origen permitido por CORS en la API)
- **Consume:** API Spring Boot según [`docs/API.md`](../docs/API.md)
- **Rama:** `feature/web-catalogo`

## Requisitos

- Node.js 20 o superior (con npm)

## Instalación y ejecución

Desde la carpeta `web-usuario/`:

```bash
# 1. Dependencias
npm install

# 2. Configuración: copiar el ejemplo y ajustarlo
cp .env.example .env          # Windows PowerShell: Copy-Item .env.example .env

# 3. Servidor de desarrollo -> http://localhost:5173
npm run dev
```

Otros comandos:

| Comando | Para qué |
|---|---|
| `npm run build` | Genera la versión de producción en `dist/` |
| `npm run preview` | Sirve localmente lo generado en `dist/` |
| `npm run lint` | Revisa el código con oxlint |

## Configuración (`.env`)

| Variable | Valor por defecto | Descripción |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8080/api` | URL base de la API Spring Boot |
| `VITE_USE_MOCK` | `true` | `true`: API **simulada en memoria**, no necesita backend. `false`: usa el backend real de `VITE_API_URL` |

El archivo `.env` no se sube al repositorio. Si cambias el `.env`, reinicia `npm run dev`.

### Modo simulado (`VITE_USE_MOCK=true`)

Responde con el mismo contrato de `docs/API.md` (campos, códigos y formato de error) y trae los datos de
`database/seed.sql`. Contraseña de todos: `Boti2026!`

| Correo | Qué permite probar |
|---|---|
| `rosa@boticasalud.pe` | Botica **aprobada**: catálogo completo (agregar, editar, eliminar, buscar) |
| `lucia@boticanueva.pe` | Botica **pendiente**: catálogo bloqueado |
| `carlos@correo.com` | Comprador: muestra que debe usar la app móvil |

- Los datos viven en memoria: **al recargar la página vuelven a los de prueba**, y las cuentas creadas
  se pierden (la sesión guardada deja de valer y te envía al login).
- Para imitar al administrador de Django, usa la consola del navegador y luego pulsa **"Actualizar estado"**:

  ```js
  boticomparaMock.boticas()                    // lista id, nombre y estado
  boticomparaMock.aprobar(3)                   // aprueba Botica Nueva Era
  boticomparaMock.rechazar(3, 'La dirección no coincide con la ficha RUC')
  boticomparaMock.pendiente(3)
  ```

  Para probar la corrección (H7): rechaza Botica Nueva Era, entra con `lucia@boticanueva.pe`, pulsa
  **"Actualizar estado"** y luego **"Corregir y reenviar"**.

## Pantallas e historias de usuario

| Ruta | Pantalla | Historias |
|---|---|---|
| `/login` | Inicio de sesión del dueño | H18 |
| `/registro` | Crear cuenta de dueño (rol `DUENO_BOTICA`) | H1 |
| `/panel/botica/registro` | Registro de botica con validación de RUC en vivo y confirmación con `GET /boticas/validar-ruc/{ruc}` | H1, H2 |
| `/panel/estado` | Estado de la solicitud (pendiente, aprobada o rechazada con motivo), botón para actualizar y carga o reemplazo de la licencia de funcionamiento (PDF, JPG o PNG, máx. 5 MB) | H4, H5 |
| `/panel/botica/corregir` | Solo con la botica **rechazada**: muestra el motivo, el formulario precargado (mismas validaciones, RUC en vivo y mapa) y, si falta, la carga de la licencia. Al reenviar vuelve a `PENDIENTE`. Con otro estado redirige a `/panel/estado` | H7 (y H4) |
| `/panel/catalogo` | Catálogo: tabla, buscador, agregar, editar y eliminar con confirmación. Se bloquea si la botica no está aprobada | H8, H9, H10, H13, H14 |

Navegación:
- Sin sesión no se entra a `/panel`.
- Si el dueño aún no registró su botica, se le lleva directo al registro de botica.
- Si la API responde **401**, se cierra la sesión y se vuelve al login con un aviso.

## Estructura

```
src/
  services/      capa de acceso a la API
    index.js       elige la implementación con VITE_USE_MOCK y cierra sesión ante un 401
    api.real.js    fetch al backend con el token Bearer
    api.mock.js    simulación en memoria con los datos de prueba
    sesion.js      token y usuario en localStorage
  context/       sesión (AuthContext), botica del dueño (BoticaContext) y mensajes (ToastContext)
  components/    piezas reutilizables (cabecera del panel, modales, campos, guardias de rutas)
  pages/         una pantalla por archivo
  utils/         validación de RUC, formato de soles y validaciones de formularios
```

Las pantallas solo importan `api` desde `services/index.js`, así que cambiar entre la API simulada y la
real no requiere tocarlas.
