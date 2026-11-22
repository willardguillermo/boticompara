# Web del dueño en Vercel

Guía para desplegar `web-usuario/` en Vercel (plan Hobby). La configuración está en
[`web-usuario/vercel.json`](../../web-usuario/vercel.json). El backend va en Render: ver [`docker.md`](docker.md).

**Orden:** primero el backend en Render (da su URL), luego la web en Vercel (da la suya) y al final `CORS_ORIGINS` en Render.

---

## 1. Antes de desplegar

- [ ] Backend en Render respondiendo: `https://TU-BACKEND.onrender.com/api`.
- [ ] **Poner la URL de Render en la CSP** (sección 4). Sin esto la web carga, pero ningún pedido llega al backend.
- [ ] **Cambiar la contraseña de las cuentas de prueba** (`rosa@`, `jorge@`, `lucia@`, `carlos@`) en la base que usará producción:
      el repositorio es público y la contraseña está escrita en `CLAUDE.md` y `docs/API.md`.

## 2. Crear el proyecto

1. En Vercel: **Add New → Project** → importar `willardguillermo/boticompara` desde GitHub.
2. **Root Directory:** `web-usuario` (ahí está `vercel.json`).
3. Lo demás lo toma de `vercel.json`; solo confírmalo:

| Opción | Valor |
|---|---|
| Framework | Vite |
| Install Command | `npm ci` |
| Build Command | `npm run build` |
| Output Directory | `dist` |
| Node.js | 22.x o 24.x (Vite 8 pide 20.19 o superior) |

4. Cargar las variables de la sección 3 **antes** de pulsar **Deploy**.

La rama de producción es `main`. Cada PR genera además una vista previa con otra URL (ver sección 5).

## 3. Variables de entorno

| Variable | Valor | ¿Obligatoria? |
|---|---|---|
| `VITE_API_URL` | `https://TU-BACKEND.onrender.com/api` (con `/api`, sin `/` al final) | **Sí.** Sin ella la web llama a `http://localhost:8080/api` |
| `VITE_USE_MOCK` | `false` | No. En un build de producción el simulador nunca entra, aunque diga `true`; se pone para que quede claro |

- Se leen **al construir**: si cambias una, hay que volver a desplegar (**Deployments → ⋯ → Redeploy**).
- Todo lo que empieza con `VITE_` queda visible en el navegador: **nunca** pongas aquí claves ni contraseñas. La web no necesita ninguna.

## 4. CSP: qué cambiar cuando exista la URL de Render

En `web-usuario/vercel.json`, en `Content-Security-Policy`, cambia el **primer** origen de `connect-src`:

```
connect-src https://backend-pendiente.invalid https://nominatim.openstreetmap.org
            └── cambiar por https://TU-BACKEND.onrender.com
```

Solo el origen: `https://`, sin `/api` y sin `/` al final. `vercel.json` no puede leer variables de entorno, así que se
cambia con un commit. Mantén el backend en el primer lugar: `npm run preview` lo reemplaza por el de tu `.env`.

| Directiva | Permite | Por qué |
|---|---|---|
| `default-src 'none'` | Nada | Lo que no está en la lista se bloquea (fuentes, marcos, objetos, medios) |
| `script-src 'self'` | Los JS del build | No hay scripts en línea ni `eval` |
| `style-src 'self'` | El CSS del build | React, React Bits y Leaflet cambian estilos desde JavaScript (`element.style`), que la CSP no bloquea: no hace falta `'unsafe-inline'` |
| `img-src 'self' data: https://*.tile.openstreetmap.org` | Ícono, imágenes `data:` y mapa | `data:` por la textura de Spotlight Card y los íconos de Leaflet que Vite mete en el CSS; el último, por los cuadros del mapa |
| `connect-src` | Backend y Nominatim | La API, y la dirección aproximada del punto del mapa (H3) |
| `base-uri 'none'`, `form-action 'self'` | — | Evitan que un HTML inyectado cambie la base de los enlaces o envíe un formulario afuera |
| `frame-ancestors 'none'` | — | Nadie puede mostrar la web dentro de un marco |

Otras cabeceras:
- `Referrer-Policy: strict-origin-when-cross-origin`. **No poner `no-referrer`:** OpenStreetMap y Nominatim piden saber de qué sitio viene la petición y pueden bloquear el mapa.
- `Permissions-Policy`: solo la ubicación (`geolocation=(self)`), para "Usar mi ubicación" en el mapa.
- `X-Content-Type-Options: nosniff`.

La regla de `rewrites` manda todas las rutas a `index.html`, menos `/assets/`: así recargar `/panel/pedidos` funciona y un archivo que
no existe da 404 en lugar de HTML.

## 5. CORS en el backend

En Render, variable `CORS_ORIGINS` con la URL que dé Vercel (sin `/` al final) y volver a desplegar el backend:

```
CORS_ORIGINS=https://TU-WEB.vercel.app
```

- Con varios orígenes, separados por coma. Si usas un dominio propio, agrégalo también.
- Las vistas previas de los PR tienen otra URL y **no** podrán entrar al backend. Es lo esperado: se prueban en local.

## 6. Comprobar el despliegue

```bash
curl -sI https://TU-WEB.vercel.app/panel/pedidos        # 200 y las cabeceras de seguridad
curl -s -o /dev/null -w '%{http_code}\n' https://TU-WEB.vercel.app/assets/no-existe.js   # 404
```

En el navegador, con la consola abierta: login, Inicio, catálogo, pedidos, asistente y el mapa del registro, y recargar (F5) en `/panel/pedidos`.

| Si ves esto | Revisa |
|---|---|
| `violates the following Content Security Policy directive: "connect-src..."` | La URL del backend en `vercel.json` (sección 4) |
| `blocked by CORS policy` | `CORS_ORIGINS` en Render (sección 5) |
| "No se pudo conectar con el servidor" | Cualquiera de las dos anteriores, `VITE_API_URL`, o Render dormido: la primera petición tarda |
| La consola menciona `vercel.live` en una vista previa | Es la barra de Vercel, bloqueada por la CSP; no afecta a la web |

## 7. Probar la CSP en local

```bash
cd web-usuario
npm run build
npm run preview      # http://localhost:4173
```

`npm run preview` manda las mismas cabeceras de `vercel.json`, con el backend de `VITE_API_URL` en `connect-src`. Para entrar, el backend
debe aceptar ese origen: `CORS_ORIGINS=http://localhost:5173,http://localhost:4173`.
