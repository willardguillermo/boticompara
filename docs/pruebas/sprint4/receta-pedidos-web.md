# Evidencia: H72 · Aviso de receta en los pedidos del dueño (web)

- **Fecha:** 20/11/2026
- **Responsable:** Guillermo
- **Rama:** `feature/web-receta`
- **Entorno:** `web-usuario/` local (`npm run dev`, puerto 5173) en **modo real**, contra el backend local de la rama `feature/backend-receta` (PR #102), con el contrato del PR #101 (sección 10 de `docs/API.md`, campo `condicionVenta` del pedido).
- **Cuentas:** `jorge@boticompara.nissidev.dev` (FarmaVida), contraseña `Boti2026!`. El pedido lo reservó `carlos@boticompara.nissidev.dev` desde la app.
- **Datos:** `database/datos_prueba_receta.sql` (la amoxicilina es `CON_RECETA`).

## Prueba manual

| # | Cuenta | Caso | Resultado esperado | Resultado |
|---|---|---|---|---|
| 1 | Jorge | Lista de **Pedidos** con el pedido de Amoxicilina Genérica que reservó carlos@ | Debajo del producto, la etiqueta "Requiere receta" y el recordatorio "Pide la receta médica antes de entregar" | Correcto |
| 2 | Jorge | Detalle del mismo pedido | Aviso amarillo "Pide la receta médica antes de entregar" junto a los botones de acción | Correcto |
| 3 | Jorge | Confirmar el pedido y pasarlo a **Lista para recoger** | Cambia de estado normalmente; la etiqueta y el recordatorio se mantienen | Correcto |
| 4 | Jorge | Pulsar **Entregado** y revisar el diálogo de confirmación (se canceló sin entregar) | En el diálogo sale el recordatorio de la receta; al cancelar, el pedido sigue en `LISTA` | Correcto |
| 5 | Jorge | Texto de la etiqueta en el detalle y en la tabla del mismo pedido de Amoxicilina Genérica (después del ajuste del texto) | En el detalle: "Requiere receta médica"; en la tabla: "Requiere receta" (versión corta por espacio) | Correcto |

**Resultado:** 5 de 5 casos correctos.

> **No probado a mano:** el **modo simulado** (`VITE_USE_MOCK=true`). En `src/services/api.mock.js` los
> pedidos traen `condicionVenta` según el medicamento enlazado (los mismos valores que
> `datos_prueba_receta.sql`) y `boticomparaMock.condicionVenta(id, condicion)` permite probar
> "Receta retenida" desde la consola, pero no se revisó en el navegador. Tampoco se vio "Receta retenida"
> en modo real, porque `POST /reservas` ya no permite crear esos pedidos.

## Verificación del build

- `npm run lint` y `npm run build` sin errores (en `web-usuario/`).
