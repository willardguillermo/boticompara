# Evidencia: formulario de producto (D18 y D20)

- **Fecha:** 13/11/2026
- **Rama:** `feature/web-formulario-producto`
- **Archivo:** `web-usuario/src/components/ProductoFormModal.jsx`
- **Modo:** simulado (`VITE_USE_MOCK=true`), con `rosa@boticompara.nissidev.dev` (botica aprobada)
- **Responsable:** Guillermo
- **Tipo de prueba:** manual en el navegador

## D18: sugerencias al elegir el medicamento del catálogo

Al elegir un medicamento en el buscador, el formulario propone nombre, presentación y principio
activo sin pisar lo que el dueño ya escribió.

| Caso | Pasos | Resultado esperado | Resultado |
|---|---|---|---|
| Campos vacíos | Nuevo producto → buscar y elegir un medicamento | Se rellenan nombre, presentación y principio activo | ✅ Cumple |
| Cambio de medicamento sin editar | Elegir un medicamento, luego otro sin tocar los campos | La sugerencia se actualiza con el segundo medicamento | ✅ Cumple |
| Nombre propio | Escribir un nombre a mano y luego elegir un medicamento | El nombre escrito se conserva; la presentación sí cambia | ✅ Cumple |
| Precio y stock | Llenar precio y stock y luego elegir un medicamento | Precio y stock no cambian | ✅ Cumple |

## D20: accesibilidad del buscador de medicamentos

La ayuda y el mensaje de error del buscador van en elementos separados y quedan asociados al campo
con `aria-describedby`, para que el lector de pantalla los lea.

| Caso | Comprobación | Resultado esperado | Resultado |
|---|---|---|---|
| Sin error | Inspeccionar el `input` del buscador | `aria-describedby="buscar-medicamento-ayuda"` | ✅ Cumple |
| Texto de ayuda | Buscar el elemento con ese id | Existe `#buscar-medicamento-ayuda` con el texto de ayuda | ✅ Cumple |
| Con error | Provocar un error al buscar o enlazar | `aria-describedby="buscar-medicamento-ayuda buscar-medicamento-error"`, `aria-invalid` y `role="alert"` en el error | ⏳ Pendiente con el backend real |

## Pendientes

- Probar el caso con error de D20 contra el backend real (Spring Boot).
- El comentario original de Alexander sobre `aria-describedby` no está en el #75; se aplicó la
  práctica estándar (ayuda y error asociados al campo). Pendiente confirmar con él si pedía algo más.
