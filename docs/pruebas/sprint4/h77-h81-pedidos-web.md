# Evidencia: H77-H81 · Pedidos del dueño en la web, con stock comprometido

- **Fechas:** 13/11/2026 (pantalla de pedidos) y 14/11/2026 (regla de stock comprometido)
- **Responsable:** Guillermo
- **Entorno:** `web-usuario/` local (`npm run dev`, puerto 5173) en **modo simulado** (`VITE_USE_MOCK=true` en el `.env` local). Las respuestas salen de `src/services/api.mock.js`, con la forma de la sección 10 de `docs/API.md` y los ajustes del contrato del PR #89.
- **Cuentas:** `rosa@boticompara.nissidev.dev` (Botica Salud Total) y `jorge@boticompara.nissidev.dev` (FarmaVida), contraseña `Boti2026!`.
- **Datos:** el mock trae un pedido de Botica Salud Total en cada uno de los 7 estados, y en FarmaVida un pedido `PENDIENTE` de Ibuprofeno Genérico (stock 0) que no alcanza para aceptarse.

> **Pendiente:** la prueba en modo real se hace cuando el backend tenga los endpoints de pedidos del dueño con la regla de stock comprometido (contrato del PR #89).

## Día 1 · 13/11/2026: pantalla de pedidos (H77, H78, H79, H80, H81)

| # | Historia | Caso | Resultado |
|---|---|---|---|
| 1 | H77 | Lista de pedidos de la botica en **Pedidos** del menú del panel | Correcto |
| 2 | H81 | Filtro por estado guardado en la URL (`/panel/pedidos?estado=...`): se conserva al recargar y al volver atrás | Correcto |
| 3 | H78, H79 | Flujo completo de **BC-7K2QXM** (Paracetamol Genérico): detalle, `PENDIENTE` → `CONFIRMADA` → `LISTA` → `ENTREGADA` | Correcto |
| 4 | H80 | Rechazo de un pedido con motivo | Correcto |
| 5 | — | Esc en modales apilados: cierra solo el de arriba y el foco vuelve al modal de abajo | Correcto |
| 6 | — | Modal del catálogo (editar y eliminar producto) sigue funcionando con los cambios de `Modal.jsx` | Correcto |

## Día 2 · 14/11/2026: stock comprometido (H79, H80)

| # | Cuenta | Caso | Resultado esperado | Resultado |
|---|---|---|---|---|
| 7 | Jorge | Pedido `PENDIENTE` sin stock suficiente | "Disponible" en rojo, **Confirmar** desactivado con el aviso "No hay stock suficiente para aceptar este pedido" y **Rechazar** disponible | Correcto |
| 8 | Rosa | Eliminar **Advil** (pedido `CONFIRMADA` de 1) | Modal "No se puede eliminar" con el mensaje del 409 y el botón **Ver pedidos**; el producto sigue en el catálogo | Correcto |
| 9 | Rosa | Eliminar **Losartán Genérico** (pedido `LISTA` de 2) | Igual que el caso 8 | Correcto |
| 10 | Rosa | Eliminar **Omeprazol Genérico** (solo un pedido `RECHAZADA`) | Se elimina normalmente | Correcto |
| 11 | Rosa | Editar Advil y bajar el stock por debajo de 1 | Debajo del campo stock: "Tienes 1 unidad comprometida en pedidos confirmados o listos para recoger; el stock no puede ser menor" | Correcto |
| 12 | Rosa | Editar Losartán Genérico y bajar el stock por debajo de 2 | Debajo del campo stock: "Tienes 2 unidades comprometidas en pedidos confirmados o listos para recoger; el stock no puede ser menor" | Correcto |

**Resultado:** 12 de 12 casos correctos.

## Verificación del build

- `npm run lint` y `npm run build` sin errores, después de actualizar la rama con `main`.
- `dist/` no contiene `boticomparaMock`: el simulador solo se carga en desarrollo (`import.meta.env.DEV`).
