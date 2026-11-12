# Evidencia: H69 · Chat del asistente en la web (dueño de botica)

- **Fecha:** 12/11/2026
- **Responsable:** Guillermo
- **Entorno:** `web-usuario/` local (`npm run dev`, puerto 5173) en **modo simulado** (`VITE_USE_MOCK=true` en el `.env` local). Las respuestas salen de `src/services/api.mock.js`, con la forma de `POST /chat` de `docs/API.md` (sección 8) y una espera de 2 s.
- **Cuentas:** `rosa@boticompara.nissidev.dev` (Botica Salud Total) y `jorge@boticompara.nissidev.dev` (FarmaVida), contraseña `Boti2026!`.
- **Alcance:** la web es solo para dueños de botica (H18). El chat del comprador es H70, en la app móvil.

> **Pendiente:** la prueba con el backend real (Spring Boot + Gemini) se hace cuando se fusione el PR #82 (H68).

## Casos probados a mano

| # | Cuenta | Caso | Qué se hizo | Resultado esperado | Resultado |
|---|---|---|---|---|---|
| 1 | Rosa | Abrir el panel | Clic en **Asistente** en la cabecera | Se abre el panel sin salir de la página, con el aviso "No escribas datos personales…" arriba, los ejemplos para el dueño y el foco en la caja de texto | Correcto |
| 2 | Rosa | Catálogo propio | "¿A cuánto tengo la amoxicilina?" | Indicador "El asistente está buscando…" y botón desactivado; luego solo Amoxil de Botica Salud Total, con enlace **Ver en mi catálogo** a `/panel/catalogo` | Correcto |
| 3 | Jorge | Stock bajo | "¿Qué productos tengo con stock bajo?" | Ibuprofeno Genérico con la etiqueta "Agotado (mínimo 5)" y enlace a su catálogo | Correcto |
| 4 | Rosa | Otras boticas | "¿A cuánto venden el paracetamol otras boticas?" | Precios escritos como se dicen ("2 soles con 20") y tarjetas de Paracetamol Genérico y Panadol de FarmaVida y Botica Salud Total, con presentación, precio, botica y distrito, sin enlace | Correcto |
| 5 | Rosa | Respaldo | "paracetamol respaldo" | Texto corto, las tarjetas y debajo, en gris, "Respuesta resumida: el asistente estaba ocupado." | Correcto |
| 6 | Rosa | Ayuda (filtro de crisis) | "Últimamente pienso en hacerme daño" | Mensaje destacado (borde y fondo de aviso, ícono de teléfono), sin tarjetas; 106 y 113 son enlaces `tel:` | Correcto |
| 7 | Rosa | Error 429 | "error429" | El mensaje queda marcado como no enviado, con el texto del cuerpo completo (termina con los números de ayuda, 106 y 113 como enlaces) y el botón **Reintentar** | Correcto |
| 8 | Rosa | Error 503 | "error503" | Igual que el 429, con el texto del 503 | Correcto |
| 9 | Rosa | Error 504 | "error504" | Igual que el 429, con el texto del 504 | Correcto |
| 10 | Rosa | Teclado: Enter | Escribir una pregunta y presionar Enter | Se envía | Correcto |
| 11 | Rosa | Teclado: Shift+Enter | Shift+Enter dentro de la caja | Hace un salto de línea, no envía | Correcto |
| 12 | Rosa | Teclado: Esc | Esc con el panel abierto | Se cierra el panel y el foco vuelve al botón **Asistente**; al reabrirlo la conversación sigue | Correcto |

**Resultado:** 12 de 12 casos correctos.

## Palabras del modo simulado

Para repetir la prueba: `error429`, `error503` y `error504` devuelven ese error; `error401` cierra la sesión; `respaldo` devuelve `"respaldo": true`; las frases del filtro de crisis de `docs/API.md` (por ejemplo "hacerme daño" o "me falta el aire") devuelven `"ayuda": true`.
