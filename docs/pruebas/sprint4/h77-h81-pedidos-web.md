# Evidencia: H77-H81 · Pedidos del dueño en la web, con stock comprometido

- **Fechas:** 13/11/2026 (pantalla de pedidos) y 14/11/2026 (regla de stock comprometido)
- **Responsable:** Guillermo
- **Entorno:** `web-usuario/` local (`npm run dev`, puerto 5173) en **modo simulado** (`VITE_USE_MOCK=true` en el `.env` local). Las respuestas salen de `src/services/api.mock.js`, con la forma de la sección 10 de `docs/API.md` y los ajustes del contrato del PR #89.
- **Cuentas:** `rosa@boticompara.nissidev.dev` (Botica Salud Total), contraseña `Boti2026!`.
- **Datos:** el mock trae un pedido de Botica Salud Total en cada uno de los 7 estados.

> **Pendiente:** la prueba en modo real se hace cuando el backend tenga los endpoints de pedidos del dueño con la regla de stock comprometido (contrato del PR #89).

## Día 1 · 13/11/2026: pantalla de pedidos (H77, H78, H79, H80, H81)

| # | Historia | Caso | Resultado |
|---|---|---|---|
| 1 | H77 | Lista de pedidos de la botica en **Pedidos** del menú del panel | Correcto |
| 2 | H81 | Filtro por estado: queda en la URL (`/panel/pedidos?estado=...`) y se conserva al recargar con F5 | Correcto |
| 3 | H78, H79 | Flujo completo de **BC-7K2QXM** (Paracetamol Genérico): detalle, `PENDIENTE` → `CONFIRMADA` → `LISTA` → `ENTREGADA` | Correcto |
| 4 | H80 | Rechazo con motivo de **BC-H4NP2T** (Rosa): un motivo corto da error, se ve el contador de caracteres y Esc cierra solo el modal de rechazo | Correcto |
| 5 | — | Esc en modales apilados: cierra solo el modal de arriba (rechazo) y deja abierto el detalle | Correcto |

## Día 2 · 14/11/2026 (modal del catálogo y regla de stock comprometido)

| # | Cuenta | Caso | Resultado esperado | Resultado |
|---|---|---|---|---|
| 6 | Rosa | Modal del catálogo (editar y eliminar producto) sigue funcionando con los cambios de `Modal.jsx` | El modal de edición guarda los cambios y el de eliminación quita el producto del catálogo, sin errores. | Correcto |
| 7 | Rosa | Pedido `PENDIENTE` de 2 Paracetamol Genérico sin stock suficiente | "Disponible: 1" en rojo, **Confirmar** desactivado con el aviso "No hay stock suficiente para aceptar este pedido" y **Rechazar** disponible | Correcto |
| 8 | Rosa | Eliminar **Advil** (pedido `CONFIRMADA` de 1) | Modal "No se puede eliminar" con el mensaje del 409 y el botón **Ver pedidos**; el producto sigue en el catálogo | Correcto |
| 9 | Rosa | Eliminar **Losartán Genérico** (pedido `LISTA` de 2) | Igual que el caso 8 | Correcto |
| 10 | Rosa | Eliminar **Omeprazol Genérico** (solo un pedido `RECHAZADA`) | Se elimina normalmente | Correcto |
| 11 | Rosa | Editar Advil y bajar el stock por debajo de 1 | Debajo del campo stock: "Tienes 1 unidad comprometida en pedidos confirmados o listos para recoger; el stock no puede ser menor" | Correcto |
| 12 | Rosa | Editar Losartán Genérico y bajar el stock por debajo de 2 | Debajo del campo stock: "Tienes 2 unidades comprometidas en pedidos confirmados o listos para recoger; el stock no puede ser menor" | Correcto |

**Resultado:** 12 de 12 casos correctos.

## Verificación del build

- `npm run lint` y `npm run build` sin errores, después de actualizar la rama con `main`.
- `dist/` no contiene `boticomparaMock`: el simulador solo se carga en desarrollo (`import.meta.env.DEV`).
