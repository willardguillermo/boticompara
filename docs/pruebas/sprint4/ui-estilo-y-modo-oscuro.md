# Evidencia: estilo suave y modo oscuro de la web del dueño

- **Fecha:** 22/11/2026
- **Dónde:** `web-usuario/` local en **modo simulado** (`VITE_USE_MOCK=true`), en Chromium con Playwright, a 1440 y 390 px. Solo la captura `12-inicio-real-sin-backend` es en modo real.
- **PR:** #114 (estilo suave y React Bits, fusionado), #126 (modo oscuro, fusionado) y #128 (contraste de un enlace, abierto al escribir esto)
- **Ramas:** `style/web-react-bits` y `style/web-modo-oscuro`
- **Responsable:** Guillermo

Las tablas de contraste son las de las descripciones de los PR. Los números son la relación de contraste de WCAG 2.x; AA pide 4,5:1 en texto y 3:1 en texto grande y bordes de controles.

## Qué se cambió

### Estilo suave (#114)

- Fondo gris muy claro con un tinte del verde de la marca (`#e8efed`); las tarjetas tienen el mismo tono y se separan con una sombra doble. Los campos van hundidos.
- Navegación en píldora. Los botones principales, las tablas y las etiquetas de estado y receta quedan planos y con borde.
- Colores y sombras solo en variables de `:root`.
- React Bits sin dependencias nuevas: Gradient Text, Spotlight Card, Shiny Text, Counter y Star Border. Respetan `prefers-reduced-motion`.
- Contorno de foco sólido de 3 px en todo lo enfocable.

### Modo oscuro (#126)

- Interruptor sol/luna en la cabecera. Sigue al sistema hasta que se elige; la elección se guarda en `localStorage`.
- `public/tema.js` pone el tema antes del primer pintado, sin script en línea.
- `:root[data-tema='oscuro']` solo redefine variables: superficies un tono más claras que el fondo y sin la sombra doble.
- El mapa se oscurece con un filtro y los controles de Leaflet usan las variables. Star Border y Shiny Text vuelven a leer el color al cambiar de tema.
- También en claro: el texto de ejemplo de los campos pasa a texto suave, y el enlace "Inicia sesión" del registro pasa de 4,34:1 a 6,02:1.

### Contraste del enlace de la licencia (#128)

- "elige uno", en la zona para subir la licencia (paso 4 del registro), usa `--color-primario-oscuro`.

## Contraste en claro (#114)

Fondo `#e8efed`, superficie plana `#f7faf9`, zona hundida `#dee7e4`.

| Par de colores | Contraste | Mínimo |
|---|---|---|
| Texto `#12302c` / fondo | 12,12:1 | 4,5 |
| Texto suave `#4d655f` / fondo | 5,39:1 | 4,5 |
| Texto suave / solo lectura (hundido) | 4,98:1 | 4,5 |
| Texto suave / cabecera de tabla `#edf2f1` | 5,56:1 | 4,5 |
| Texto suave / superficie plana | 5,98:1 | 4,5 |
| Texto suave / pedido cancelado o vencido `#e3eae8` | 5,15:1 | 4,5 |
| Enlace primario `#0f766e` / fondo | 4,69:1 | 4,5 |
| Pestaña activa `#115e59` / `#ccfbf1` | 6,73:1 | 4,5 |
| Blanco / botón primario | 5,47:1 | 4,5 |
| Blanco / botón de peligro `#b91c1c` | 6,47:1 | 4,5 |
| Éxito `#166534` / fondo · etiqueta | 6,11 · 6,49 | 4,5 |
| Aviso `#92400e` / fondo · etiqueta | 6,08 · 6,37 | 4,5 |
| Error `#b91c1c` / fondo | 5,55:1 | 4,5 |
| Texto deshabilitado `#50645f` / `#dfe6e4` | 4,98:1 | 4,5 |
| Cian del degradado `#0e7490` / fondo (texto grande) | 4,59:1 | 3 |
| Borde de campos y controles `#6c827c` / fondo | 3,51:1 | 3 |
| Borde de campos / zona hundida | 3,25:1 | 3 |
| Foco / fondo · superficie plana | 4,69 · 5,21 | 3 |
| Botón primario / fondo | 4,69:1 | 3 |

## Contraste en oscuro (#126)

| Color | Fondo | Superficie | Superficie plana | Hundido | Fila con puntero | Sin leer | Sin leer con puntero |
|---|---|---|---|---|---|---|---|
| Texto | 15,2 | 13,3 | 12,0 | 15,9 | 10,5 | 11,3 | 9,7 |
| Texto suave | 8,9 | 7,8 | 7,0 | 9,3 | 6,1 | 6,6 | 5,6 |
| Primario | 9,6 | 8,4 | 7,6 | 10,0 | 6,6 | 7,1 | 6,1 |
| Error | 9,4 | 8,2 | 7,5 | 9,8 | 6,5 | 7,0 | 6,0 |
| Borde de controles (3:1) | 5,7 | 5,0 | 4,5 | 6,0 | 4,0 | 4,2 | 3,6 |

| Etiqueta o botón | Contraste | | Etiqueta o botón | Contraste |
|---|---|---|---|---|
| Pendiente | 9,5 | | Requiere receta | 8,4 |
| Confirmado | 7,8 | | Botón principal | 9,1 |
| Listo para recoger y pestaña activa | 7,9 | | Botón de peligro y contador | 8,9 |
| Entregado | 9,9 | | Desactivado | 5,2 |
| Rechazado | 8,0 | | Borde de control sobre primario suave (3:1) | 3,8 |
| Cancelado y vencido | 6,3 | | | |

## Contraste del enlace de la licencia (#128)

| Tema | Antes | Ahora |
|---|---|---|
| Claro | 4,34:1 | 6,02:1 |
| Oscuro | — | 12,62:1 |

## Prueba en el navegador (modo simulado)

| # | Caso | Resultado |
|---|---|---|
| 1 | Contraste medido en oscuro sobre los elementos de login, registro, Inicio, campana, aviso, pedidos y su detalle, catálogo, formulario con errores, carga masiva, Mi botica (aprobada y rechazada), chat y mapa, a 1440 y 390 px | 0 fallos. Mínimo medido: texto 6,3:1 y bordes 3,8:1 |
| 2 | La misma medición en claro | 1 fallo: el enlace "Inicia sesión" del registro, 4,34:1. Corregido en el #126: 6,02:1 en claro y 12,62:1 en oscuro |
| 3 | Enlace "elige uno" de la licencia, en el paso 4 del registro | 6,02:1 en claro y 12,62:1 en oscuro (#128) |
| 4 | Sin elección guardada: sigue al sistema, también si cambia con la página abierta | Correcto |
| 5 | Interruptor con el teclado (Espacio); la elección se recuerda al recargar y manda sobre el sistema | Correcto |
| 6 | Cambiar el tema en otra pestaña | Cambia en las dos |
| 7 | `localStorage` bloqueado | Sigue al sistema, sin errores |
| 8 | Build de producción sin que cargue el JavaScript de la aplicación | El fondo ya es el oscuro: no parpadea |
| 9 | Build con la CSP del #123, en el login | 0 violaciones |
| 10 | Inicio a 320, 360, 375, 390, 430, 480, 721 y 1024 px | Sin scroll horizontal |

## Cubierto por pruebas automáticas

En `web-usuario/src/utils/`:

| Caso | Prueba |
|---|---|
| La elección manda y sin elección sigue al sistema | `tema.test.js` · "resolverTema: la elección manda y sin elección sigue al sistema" |
| El script inicial elige lo mismo que la aplicación | `tema.test.js` · "public/tema.js elige lo mismo que resolverTema" |
| Si `localStorage` falla, sigue al sistema | `tema.test.js` · "public/tema.js sigue al sistema si localStorage falla" |
| "S/" y el precio no se parten entre líneas en el chat (#114) | `chat.test.js` · "unirPrecios une "S/" y el monto con un espacio de no separación", "unirPrecios cambia todos los precios del texto, también en listas", "unirPrecios no toca otros espacios ni "S/" sin número" |
| El historial que se envía no cambia (#114) | `chat.test.js` · "el historial que se envía conserva el texto original, sin el espacio de no separación" |

El contraste **no tiene prueba automática** en el repositorio: se midió en el navegador.

## Por confirmar

- Modo oscuro en modo real con el backend.
- Otros navegadores (Safari y Firefox) y un celular físico: solo se usó Chromium.
- Lector de pantalla.

## Capturas

Están fuera del repositorio, en `~/Documentos/boticompara-ui/`. El nombre de cada archivo lleva delante el ancho: `1440-` o `390-`.

### `capturas-suave/` (20 archivos)

| Archivo | Anchos | Pantalla |
|---|---|---|
| `01-login.png` | 1440 y 390 | Login (Spotlight y degradado de la marca) |
| `02-mi-botica.png` | 1440 y 390 | Mi botica (Star Border y navegación en píldora) |
| `02b-foco-teclado.png` | 1440 y 390 | Foco de teclado en la navegación |
| `03-productos.png` | 1440 y 390 | Catálogo |
| `04-pedidos.png` | 1440 y 390 | Pedidos |
| `05-carga-masiva.png` | 1440 y 390 | Carga masiva con el resumen (Counter) |
| `06-chat-escribiendo.png` | 1440 y 390 | Chat con el aviso de escribiendo (Shiny Text) |
| `07-chat-saludo-y-lista.png` | 1440 y 390 | Saludo del asistente al dueño |
| `08-chat-consejo-invierno.png` | 1440 y 390 | Lista de 8 líneas con "- " |
| `09-chat-en-tu-botica.png` | 1440 y 390 | "En tu botica: S/ 2.50" sin cortar el precio |

### `capturas-notificaciones/` (22 archivos)

| Archivo | Anchos | Pantalla |
|---|---|---|
| `01-inicio-esqueletos.png` | 1440 y 390 | Inicio, esqueletos de carga * |
| `02-inicio-primera-visita.png` | 1440 y 390 | Inicio con el aviso de botica aprobada * |
| `03-inicio.png` | 1440 y 390 | Inicio |
| `04-campana-panel.png` | 1440 y 390 | Campana con el panel abierto |
| `05-aviso-flotante.png` | 1440 y 390 | Aviso flotante |
| `06-pedido-desde-aviso.png` | 1440 y 390 | Pedido abierto desde el aviso |
| `07-pedidos-pildoras.png` | 1440 y 390 | Pedidos con filtros en píldora |
| `08-pedidos-filtro-entregados.png` | 1440 y 390 | Pedidos filtrados por entregados |
| `09-campana-todo-leido.png` | 1440 y 390 | Campana con todo leído * |
| `10-inicio-sin-notificaciones.png` | 390 | Inicio de jorge@, sin notificaciones * |
| `11-campana-vacia.png` | 390 | Campana vacía * |
| `12-inicio-real-sin-backend.png` | 1440 y 390 | Inicio en modo real, sin el backend de notificaciones |

### `capturas-oscuro/` (34 archivos)

| Archivo | Anchos | Pantalla |
|---|---|---|
| `01-login.png` | 1440 y 390 | Login |
| `02-inicio-esqueletos.png` | 1440 y 390 | Inicio, esqueletos de carga |
| `03-inicio.png` | 1440 y 390 | Inicio |
| `04-campana.png` | 1440 y 390 | Campana con el panel abierto |
| `05-aviso.png` | 1440 y 390 | Aviso flotante |
| `06-pedidos.png` | 1440 y 390 | Pedidos |
| `07-pedidos-estados.png` | 1440 y 390 | Pedidos en los 7 estados y con receta |
| `08-pedido-detalle.png` | 1440 y 390 | Detalle de un pedido |
| `09-catalogo.png` | 1440 y 390 | Catálogo |
| `10-producto-errores.png` | 1440 y 390 | Formulario de producto con errores |
| `11-carga-masiva.png` | 1440 y 390 | Carga masiva |
| `12-mi-botica.png` | 1440 y 390 | Mi botica aprobada (Star Border) |
| `13-chat-escribiendo.png` | 1440 y 390 | Chat con el aviso de escribiendo (Shiny Text) |
| `14-chat.png` | 1440 y 390 | Chat: lista, ayuda y tarjeta |
| `15-chat-error.png` | 1440 y 390 | Chat con error |
| `16-rechazada.png` | 1440 y 390 | Mi botica rechazada (lucia@) |
| `17-mapa.png` | 1440 y 390 | Mapa de Leaflet |

\* Tomadas antes de suavizar el fondo de las notificaciones sin leer, pasar a "9+" y ajustar los textos del Inicio; no se repitieron. Las `03`, `04` y `05` sí son posteriores.

## Pruebas automáticas

```
cd web-usuario
npm run lint
npm run build
npm test
```

Resultado (22/11/2026, rama `style/web-modo-oscuro`): **20 pruebas, 0 fallos**, con lint y build sin errores.
