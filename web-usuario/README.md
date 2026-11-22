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
| `npm test` | Pruebas de las funciones puras con `node --test` |

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
| `rosa@boticompara.nissidev.dev` | Botica **aprobada**: catálogo completo (agregar, editar, eliminar, buscar). Tiene 3 notificaciones y a los 60 s de entrar le llega una reserva nueva (H103) |
| `jorge@boticompara.nissidev.dev` | Botica **aprobada** con un producto agotado: etiqueta y filtro de **stock bajo** (H12) |
| `lucia@boticompara.nissidev.dev` | Botica **rechazada** con motivo: catálogo bloqueado; prueba la corrección y el reenvío (H7) |
| `carlos@boticompara.nissidev.dev` | Comprador: muestra que debe usar la app móvil |

- Los datos viven en memoria: **al recargar la página vuelven a los de prueba**, y las cuentas creadas
  se pierden (la sesión guardada deja de valer y te envía al login).
- Para imitar al administrador de Django, usa la consola del navegador y luego pulsa **"Actualizar estado"**:

  ```js
  boticomparaMock.boticas()                    // lista id, nombre y estado
  boticomparaMock.aprobar(3)                   // aprueba Botica Nueva Era
  boticomparaMock.rechazar(3, 'La dirección no coincide con la ficha RUC')
  boticomparaMock.pendiente(3)
  boticomparaMock.nuevaReserva(1)              // H103: reserva del producto 1 y su notificación
  boticomparaMock.cancelarPedido(1)            // H103: el comprador cancela y llega la notificación
  ```

  Para probar la corrección (H7): entra con `lucia@boticompara.nissidev.dev` (Botica Nueva Era ya viene
  rechazada) y pulsa **"Corregir y reenviar"**. Para verla pendiente o aprobada, usa `boticomparaMock.pendiente(3)`
  o `boticomparaMock.aprobar(3)` y pulsa **"Actualizar estado"**.

## Pantallas e historias de usuario

| Ruta | Pantalla | Historias |
|---|---|---|
| `/login` | Inicio de sesión del dueño | H18 |
| `/registro` | Crear cuenta de dueño (rol `DUENO_BOTICA`) | H1 |
| `/panel/botica/registro` | Registro de botica con validación de RUC en vivo y confirmación con `GET /boticas/validar-ruc/{ruc}` | H1, H2 |
| `/panel/inicio` | Solo con la botica **aprobada** (es la primera pantalla al entrar): saludo según la hora de Lima, pedidos pendientes, por recoger hoy, stock bajo, calificación promedio y las 5 últimas notificaciones. Con otro estado redirige a `/panel/estado` | H103 |
| `/panel/estado` | Estado de la solicitud (pendiente, aprobada o rechazada con motivo), botón para actualizar y carga o reemplazo de la licencia de funcionamiento (PDF, JPG o PNG, máx. 5 MB) | H4, H5 |
| `/panel/botica/corregir` | Solo con la botica **rechazada**: muestra el motivo, el formulario precargado (mismas validaciones, RUC en vivo y mapa) y, si falta, la carga de la licencia. Al reenviar vuelve a `PENDIENTE`. Con otro estado redirige a `/panel/estado` | H7 (y H4) |
| `/panel/catalogo` | Catálogo: tabla, buscador, agregar, editar (con stock mínimo) y eliminar con confirmación. Etiqueta "Stock bajo" (stock ≤ stock mínimo) y filtro "Solo stock bajo" combinable con la búsqueda. Se bloquea si la botica no está aprobada | H8, H9, H10, H12, H13, H14 |
| `/panel/catalogo/carga-masiva` | Carga masiva por CSV: descarga de plantilla, subida del archivo (solo `.csv`, máx. 5 MB) y reporte con creados, actualizados y errores por fila. Solo con la botica **aprobada**; si no, redirige a `/panel/estado` | H11 |

En la cabecera del panel, la **campana** (H103) consulta el contador cada 30 s solo con la pestaña visible, abre la
lista de notificaciones y avisa cuando llega una nueva. `/panel/pedidos?pedido=15` abre el detalle de ese pedido.

**Modo oscuro:** el interruptor de la cabecera (sol/luna) recuerda la elección; sin elección sigue al sistema. `public/tema.js`
pone el tema antes del primer pintado y `index.css` solo redefine las variables en `:root[data-tema='oscuro']`.

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

Animaciones de [React Bits](https://reactbits.dev) (variante JS-CSS, copiadas en `src/components/reactbits/`, licencia MIT + Commons Clause en su `LICENSE.md`): Counter (resumen de la carga masiva y tarjetas del Inicio), Shiny Text (asistente escribiendo), Gradient Text (marca del login y registro), Spotlight Card (tarjeta del login) y Star Border (botón principal de Mi botica). No agregan dependencias y respetan `prefers-reduced-motion`.
