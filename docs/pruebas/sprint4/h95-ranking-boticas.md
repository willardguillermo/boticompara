# Evidencia: ranking de boticas en el admin (H95)

- **Fecha:** 16/11/2026
- **Dónde:** servidor local de Django (`backend-admin/`, `http://127.0.0.1:8000/admin/reportes/ranking/`) contra la base de Supabase.
- **Rama:** `feature/admin-ranking`
- **Responsable:** Guillermo
- **Datos de prueba:** los que ya hay en la base de Supabase (sin script nuevo)

## Prueba manual

| # | Historia | Paso | Resultado |
|---|---|---|---|
| 1 | H95 | Abrir **Reportes → Ranking de boticas** con el periodo por defecto (17/10/2026 al 16/11/2026) | 1. Botica Salud Total: 12 productos actualizados, puntaje 12, calificación 5,0, veces comparado 25. 2. FarmaVida: 7 productos actualizados, puntaje 7, calificación "—" (su única reseña está oculta), veces comparado 24. Coincide con lo calculado directamente en Supabase |
| 2 | H95 | Revisar debajo de la tabla | Se ve la nota con la fórmula del puntaje |
| 3 | H95 | Pulsar **Exportar a Excel** | Mismas columnas que la tabla, calificación con 1 decimal, "—" alineada a la derecha y la nota con la fórmula combinada a lo ancho y legible |
| 4 | H95 | Filtrar del 01/01/2026 al 31/03/2026 | Aparece el mensaje de que ninguna botica aprobada tuvo actividad, con la opción "Limpiar" |
| 5 | H95 | En el panel principal, con el periodo 01/01/2026 al 31/03/2026, pulsar "Ver el ranking de boticas" | Se abre el ranking con las mismas fechas |

## Cubierto por pruebas automáticas

Casos que cubren las pruebas de `backend-admin/reportes/tests/test_ranking.py`:

| Caso | Prueba |
|---|---|
| Puntaje = 3 × pedidos entregados + 2 × reseñas respondidas + 1 × productos actualizados | `RankingTests.test_puntaje_suma_3_por_pedido_2_por_respuesta_y_1_por_producto` |
| Orden por puntaje de mayor a menor | `RankingTests.test_ordena_por_puntaje_de_mayor_a_menor` |
| Desempate: calificación promedio y luego nombre (sin calificación al final) | `RankingTests.test_empate_por_calificacion_y_luego_por_nombre` |
| Solo cuentan las boticas aprobadas | `RankingTests.test_solo_cuentan_las_boticas_aprobadas` |
| Solo cuentan las reservas ENTREGADA y las reseñas respondidas | `RankingTests.test_solo_cuentan_los_pedidos_entregados`, `RankingTests.test_resenas_sin_responder_no_suman` |
| Filtro del periodo: últimos 30 días por defecto y rango Desde/Hasta | `RankingTests.test_por_defecto_solo_los_ultimos_30_dias`, `RankingTests.test_con_fechas_cuenta_solo_ese_rango`, `PaginaDelRankingTests.test_con_fechas_filtra_y_las_conserva_en_el_excel`, `PaginaDelRankingTests.test_fecha_invalida_avisa_y_usa_los_ultimos_30_dias` |
| Las reseñas ocultas no cuentan en el promedio ("—" si solo tiene ocultas) | `RankingTests.test_las_resenas_ocultas_no_cuentan_en_el_promedio`, `RankingTests.test_solo_resenas_ocultas_es_sin_calificacion`, `PaginaDelRankingTests.test_sin_calificacion_muestra_raya` |
| "Veces comparado" es informativo y no suma al puntaje | `RankingTests.test_veces_comparado_no_suma_al_puntaje` |
| Estado vacío si ninguna botica tiene actividad | `RankingTests.test_sin_actividad_no_hay_filas`, `PaginaDelRankingTests.test_estado_vacio` |
| Acceso solo para staff (anónimo y no staff van al login, también en el Excel) | `PaginaDelRankingTests.test_anonimo_va_al_login`, `PaginaDelRankingTests.test_usuario_que_no_es_staff_va_al_login` |
| Tabla y nota con la fórmula en la página | `PaginaDelRankingTests.test_staff_ve_la_tabla_y_la_formula` |
| Enlace desde el panel con las mismas fechas y entrada en la app Reportes | `PaginaDelRankingTests.test_el_panel_enlaza_al_ranking_con_sus_fechas`, `PaginaDelRankingTests.test_aparece_en_la_app_reportes` |
| Excel: columnas, filas y fórmula al final | `PaginaDelRankingTests.test_exportar_a_excel` |
| Excel: calificación con 1 decimal, "—" a la derecha y nota combinada con texto ajustado | `PaginaDelRankingTests.test_formato_del_excel` |

Hoy no hay datos de prueba de pedidos entregados ni de reseñas respondidas, así que esas columnas con un valor
mayor que 0 solo las cubren las pruebas automáticas. Se mostrarán con los datos de la demo.

**Para UI/UX:** los campos Desde/Hasta aparecen vacíos cuando se muestran los últimos 30 días por defecto
(el filtro es compartido con los otros reportes).

## Pruebas automáticas

```
cd backend-admin
python manage.py test
```

Resultado (16/11/2026): **154 pruebas, OK**.
