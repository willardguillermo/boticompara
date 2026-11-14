# Evidencia: gestión de usuarios en el admin (H90-H94)

- **Fecha:** 13/11/2026
- **Dónde:** servidor local de Django (`backend-admin/`, `http://127.0.0.1:8000/admin/`) contra la base de Supabase.
- **Rama:** `feature/admin-usuarios`
- **Responsable:** Guillermo
- **Cuenta de prueba:** `carlos@boticompara.nissidev.dev` (comprador)

## Prueba manual

| # | Historia | Paso | Resultado |
|---|---|---|---|
| 1 | H90 | Abrir la lista de usuarios | Se ve la columna de estado y los filtros por rol y por estado |
| 2 | H91 | Suspender a carlos@ con la fecha de hoy | Rechazado: la fecha debe ser futura |
| 3 | H91 | Suspender con un motivo de menos de 10 caracteres | Rechazado: el motivo es muy corto |
| 4 | H91 | Suspender hasta el 14/11/2026 con un motivo válido | Mensaje verde; estado "Suspendido hasta 14/11/2026" |
| 5 | H94 | Ver el historial en la ficha | Fila de suspensión con el administrador `admin`, 12:51 |
| 6 | H90 | Filtro de estado "Suspendido" | La lista muestra a carlos@ |
| 7 | H92 | Reactivar la cuenta | Vuelve a "Activo" |
| 8 | H94 | Ver el historial en la ficha | 2 filas: la reactivación (12:52) arriba y la suspensión (12:51) abajo |

> **Aclaración:** el motivo de la suspensión guardado en la base dice "(12/11)" por error al escribirlo;
> la prueba se hizo el **13/11/2026**.

**H93 (dar de baja) no se probó a mano** porque es irreversible: la cuenta no puede volver a entrar y no
hay acción para reactivarla. La cubren las pruebas automáticas: formulario y confirmación, que solo el
segundo envío da de baja, que una cuenta ya dada de baja no se suspende ni se reactiva, y la fila en el historial.

## Revisión visual

Revisado en modo claro y modo oscuro: **correcto**.

| Pantalla | URL |
|---|---|
| Suspender | `/admin/boticas/usuario/<id>/suspender/` |
| Dar de baja (formulario y confirmación, sin confirmar) | `/admin/boticas/usuario/<id>/dar-baja/` |
| Ficha del usuario (fechas en hora de Lima, historial, botones según el estado) | `/admin/boticas/usuario/<id>/change/` |

## Pruebas automáticas

```
cd backend-admin
python manage.py test
```

Resultado: **131 pruebas, OK**.
