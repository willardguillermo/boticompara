# Evidencia: auditoría de UI/UX del admin, tanda 2 (H5, H6, H63, H64, H95)

- **Fecha:** 21/11/2026
- **Dónde:** servidor local de Django (`backend-admin/`, `http://127.0.0.1:8000/admin/`) contra la base de Supabase.
- **Rama:** `style/admin-auditoria`
- **Responsable:** Guillermo
- **Origen:** informe de auditoría de UI/UX, sección "Admin de Django" (puntos A-R1, A-B1, A-U1, A-U2, A-B2, A-R2, A-R5).

## Prueba manual

No se confirmó ninguna aprobación ni ningún rechazo: escriben en la base real y envían el correo al dueño.

| # | Historia | Paso | Resultado |
|---|---|---|---|
| 1 | H64 | Abrir el panel (`/admin/`) sin elegir fechas | Desde y Hasta vienen llenos con los últimos 30 días |
| 2 | H95 | Abrir el ranking de boticas sin elegir fechas | Desde y Hasta vienen llenos con los últimos 30 días |
| 3 | H63 | Filtrar con otras fechas y pulsar "Limpiar" | Vuelve a los últimos 30 días |
| 4 | H5, H6 | Abrir la ficha de Mi Salud (pendiente) | Se ven los botones Aprobar y Rechazar |
| 5 | H5 | Pulsar Aprobar en Mi Salud | Se abre la página de confirmación (no se confirmó) |
| 6 | H6 | Pulsar Rechazar en Mi Salud y enviar sin motivo | Se muestra el error "Indica el motivo del rechazo." y no se rechaza |
| 7 | H5, H6 | Abrir la ficha de Nueva Era (rechazada) | No hay botones de revisión |
| 8 | H6 | Abrir la ficha de Salud Total (aprobada) | Solo aparece Rechazar |
| 9 | H90, H47 | Abrir las listas de usuarios y de boticas | El estado se ve como etiqueta de color |

## Cubierto por pruebas automáticas

Casos que no se probaron a mano:

| Caso | Prueba |
|---|---|
| Aprobar desde la ficha: estado APROBADO, fila en `botica_revision` con el administrador y correo al dueño | `boticas/tests/test_revision.py` · `AprobarTests.test_aprueba_deja_historial_y_avisa_por_correo` |
| Rechazar con motivo (pendiente o aprobada): estado RECHAZADO, motivo guardado, historial y correo con el motivo | `RechazarTests.test_rechaza_con_motivo_deja_historial_y_avisa_por_correo` |
| Por URL directa no se aprueba una aprobada o rechazada, ni se rechaza una rechazada | `AprobarTests.test_solo_se_aprueba_una_pendiente`, `RechazarTests.test_una_rechazada_no_se_vuelve_a_rechazar` |
| Revisada por otro administrador mientras la página estaba abierta: no cambia nada | `AprobarTests.test_aprobada_por_otro_admin_mientras_tanto` |
| Si falla el correo, la revisión igual se guarda y se avisa | `AprobarTests.test_si_falla_el_correo_la_aprobacion_igual_se_guarda` |
| Sin "Can change botica": no hay botones y las URL dan 403; con el permiso sí se revisa | `PermisosTests` |
| La acción masiva y el select "estado" siguen usando la misma lógica (historial, correo, solo las que cambian) | `MismaLogicaEnLosTresLugaresTests` |
| Aprobar en verde como acción principal y Rechazar en rojo (ajuste posterior a la prueba manual) | `BotonesDeLaFichaTests.test_botones_segun_el_estado` |
| Desde/Hasta con fechas, con dd/mm/aaaa, con un solo lado y con error, en el panel, más buscados y ranking | `reportes/tests/test_periodo.py` |
| Un solo naranja y contraste AA de las etiquetas; etiquetas en las listas de boticas y usuarios | `boticas/tests/test_etiquetas.py` |

## Pruebas automáticas

```
cd backend-admin
python manage.py test
```

Resultado (21/11/2026): **206 pruebas, OK**.
