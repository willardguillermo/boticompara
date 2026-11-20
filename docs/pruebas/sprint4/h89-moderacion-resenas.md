# Evidencia: moderación de reseñas en el admin (H89)

- **Fecha:** 13/11/2026
- **Dónde:** servidor local de Django (`backend-admin/`, `http://127.0.0.1:8000/admin/`) contra la base de Supabase.
- **Rama:** `feature/admin-resenas`
- **Responsable:** Guillermo
- **Datos de prueba:** `database/datos_prueba_resenas.sql` (2 reseñas de `carlos@boticompara.nissidev.dev`)

## Prueba manual

| # | Historia | Paso | Resultado |
|---|---|---|---|
| 1 | H89 | Abrir la lista de reseñas | Se ven la reseña 3 (Botica Salud Total, 5 estrellas, visible) y la reseña 4 (FarmaVida, 1 estrella, "Son unos estafadores…") |
| 2 | H89 | Revisar la reseña 4 en la lista | La reseña 4 aparece marcada por las palabras a revisar |
| 3 | H89 | Ocultar la reseña 4 con el motivo "Lenguaje ofensivo hacia la botica sin pruebas" | La reseña 4 queda oculta |

## Cubierto por pruebas automáticas

Casos que no se probaron a mano y que cubren las pruebas de `backend-admin/boticas/tests/test_resenas.py`:

| Caso | Prueba |
|---|---|
| Mostrar de nuevo una reseña oculta: deja de estar oculta y se borra el motivo | `MostrarTests.test_muestra_de_nuevo_y_borra_el_motivo` |
| La página de "Mostrar de nuevo" solo pide confirmar y enseña el motivo actual | `MostrarTests.test_get_solo_pide_confirmar` |
| Historial al ocultar: fila con el administrador y "Ocultó la reseña. Motivo: …" | `OcultarTests.test_oculta_con_motivo_y_deja_historial` |
| Historial al mostrar de nuevo: fila con el administrador y "Volvió a mostrar la reseña." | `MostrarTests.test_muestra_de_nuevo_y_borra_el_motivo` |
| Motivo vacío, de menos de 10 o de más de 300 caracteres: rechazado y sin historial | `OcultarTests.test_motivo_de_10_a_300_caracteres` |
| Nadie edita el texto ni las estrellas, ni borra ni crea reseñas (403) | `ListaResenasTests.test_nadie_edita_ni_borra_ni_crea` |
| Un administrador sin "Can change resena" no ve Ocultar / Mostrar de nuevo y recibe 403 | `BotonesYPermisosTests.test_solo_ver_no_tiene_botones_y_recibe_403` |
| Filtros de estado y de estrellas | `ListaResenasTests.test_filtros_de_estado_y_estrellas` |
| Filtro de botica y búsqueda por comentario o nombre de la botica | `ListaResenasTests.test_filtro_de_botica_y_busqueda` |

La reseña oculta deja de aparecer en la app/web: se prueba cuando exista la API de reseñas (H85–H87).

## Pruebas automáticas

```
cd backend-admin
python manage.py test
```

Resultado (16/11/2026, con `origin/main` ya fusionado en la rama): **152 pruebas, OK**.
