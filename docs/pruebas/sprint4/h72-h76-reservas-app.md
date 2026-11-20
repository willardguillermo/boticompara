# Evidencia: reservas del comprador en la app (H72–H76)

- **Fecha:** 20/11/2026
- **Dónde:** emulador de Android con la app (`app-movil/`) contra `backend-usuario` local (rama `main`, `http://10.0.2.2:8080/api/`). Web del dueño (`web-usuario/`) en modo real (`VITE_USE_MOCK=false`) con `jorge@boticompara.nissidev.dev`.
- **Rama:** `feature/movil-reservas`
- **Responsable:** Guillermo
- **Datos de prueba:** comprador `carlos@boticompara.nissidev.dev` y los productos de `database/seed.sql` (FarmaVida)

## Prueba manual

| # | Historia | Paso | Resultado |
|---|---|---|---|
| 1 | H72, H73, H74 | Como `carlos@`, abrir **Paracetamol Genérico** de FarmaVida, pulsar **Reservar**, subir la cantidad a 2 y confirmar | Se crea la reserva con el código **BC-J6U73V** en grande, total **S/ 4.40** y "Válida hasta" 24 h después en hora de Lima |
| 2 | H72 | Reservar otra vez el mismo producto | Sale el mensaje del servidor "Ya tienes una reserva activa de este producto" |
| 3 | H74 | Abrir la hoja de **Nexium** con 5 unidades; desde la web, como `jorge@`, bajar el stock a 2; volver a la app y confirmar | Sale "Solo hay 2 unidades disponibles de este producto"; la etiqueta pasa a "Quedan 2", la cantidad baja a 2 y el botón **+** queda desactivado |
| 4 | H75, H76 | Desde **Perfil → Mis reservas**, abrir dos reservas activas y cancelarlas confirmando el diálogo | **BC-55QZ4D** y **BC-3LMFSP** quedan en CANCELADA |
| 5 | H76 | Desde la web, como `jorge@`, rechazar **BC-J6U73V** con el motivo "Nos quedamos sin esa presentación por hoy"; en la app, deslizar para actualizar y abrir la reserva | Chip **Rechazada**, se ve el motivo, la tarjeta del código está en gris con "Esta reserva fue rechazada" y no aparece el botón **Cancelar reserva** |

## Condición de venta (20/11/2026)

- **Dónde:** el mismo emulador, con `backend-usuario` local de la rama `feature/backend-receta` (PR #102) y la web del dueño en modo real.
- **Datos de prueba:** `database/v6_receta.sql` y `database/datos_prueba_receta.sql` (PR #101) aplicados en Supabase.

| # | Historia | Paso | Resultado |
|---|---|---|---|
| 6 | H72 | Como `carlos@`, buscar "paracetamol" y abrir **Paracetamol Genérico** (`SIN_RECETA`) | Sin etiqueta de receta en la tarjeta ni en el detalle |
| 7 | H72 | Buscar "amoxicilina" y abrir **Amoxicilina Genérica** de FarmaVida (`CON_RECETA`) | Etiqueta "Requiere receta médica" en la tarjeta y en el detalle |
| 8 | H72 | Pulsar **Reservar** en Amoxicilina Genérica, dejar la cantidad en 1 y confirmar | La hoja muestra "Presenta tu receta médica al recoger; sin ella la botica no puede entregarlo". La reserva se crea y su detalle muestra el mismo aviso debajo de la tarjeta del código |
| 9 | H72 | Buscar "clonazepam" y abrir **Clonazepam Genérico** de Botica Salud Total (`RECETA_RETENIDA`) | Etiqueta "Receta retenida". En lugar del botón Reservar sale "Este medicamento se vende con receta retenida: acércate a la botica con tu receta médica" |

**409 de receta retenida con la hoja abierta:** no se probó en el emulador, porque la app ya no muestra el botón Reservar con `RECETA_RETENIDA`.
- **Pruebas automáticas de la app:** `CondicionVentaTest.seReservaSiHayStockYNoEsRecetaRetenida` comprueba que no se puede reservar.
- **Backend (PR #102):** sus pruebas automáticas y su prueba en vivo comprueban que `POST /reservas` responde 409 con el mensaje del contrato y no crea nada.
- **Mensaje en la hoja:** que la hoja muestre el mensaje del servidor en un 409 se comprobó en los pasos 2 y 3.

## Cubierto por pruebas automáticas

Casos que cubren las pruebas de `app-movil/app/src/test/java/com/boticompara/app/ReservasTest.kt`:

| Caso | Prueba |
|---|---|
| Cantidad de 1 a 10 y botones − y + en los límites (H74) | `ReservasTest.laCantidadVaDe1a10`, `ReservasTest.losBotonesRespetanLosLimites` |
| La cantidad no pasa del stock (H74) | `ReservasTest.laCantidadNoPasaDelStock` |
| Total = cantidad × precio unitario con 2 decimales (H74) | `ReservasTest.elTotalTieneDosDecimales` |
| Nombre en español de los 7 estados (H76) | `ReservasTest.losSieteEstadosTienenNombreEnEspanol` |
| Color del chip de cada estado (H76) | `ReservasTest.cadaEstadoTieneSuColor` |
| Texto de la tarjeta del código según el estado (H73, H76) | `ReservasTest.elTextoDeLaTarjetaDependeDelEstado` |
| La tarjeta usa el color de éxito solo si la reserva está activa; si no, fondo neutro (H73, H76) | `ReservasTest.laTarjetaUsaColorDeExitoSoloSiEstaActiva` |
| Solo se cancela en PENDIENTE, CONFIRMADA o LISTA, y antes de `venceEn` (H75) | `ReservasTest.soloSeCancelaPendienteConfirmadaOLista`, `ReservasTest.noSeCancelaSiYaVencio` |
| Fechas en hora de Lima, con microsegundos y desfases (H73, H76) | `ReservasTest.formateaEnHoraDeLima`, `ReservasTest.leeFraccionesYDesfases` |
| Historial de la más reciente a la más antigua (H76) | `ReservasTest.ordenaDeLaMasRecienteALaMasAntigua` |

Casos de la condición de venta que cubren las pruebas de `app-movil/app/src/test/java/com/boticompara/app/CondicionVentaTest.kt`:

| Caso | Prueba |
|---|---|
| Texto de la etiqueta: "Requiere receta médica", "Receta retenida" o sin etiqueta (`SIN_RECETA`, `null` o un valor desconocido) (H72) | `CondicionVentaTest.textoDeLaEtiquetaSegunLaCondicion` |
| Se reserva si hay stock y no es `RECETA_RETENIDA` (H72) | `CondicionVentaTest.seReservaSiHayStockYNoEsRecetaRetenida` |
| El aviso de la receta solo es para `CON_RECETA` (H72) | `CondicionVentaTest.elAvisoDeRecetaSoloEsParaConReceta` |
| En la reserva, el aviso solo sale si está PENDIENTE, CONFIRMADA o LISTA (H72) | `CondicionVentaTest.elAvisoEnLaReservaSoloSiEstaActiva` |
| Textos exactos de los dos avisos (H72) | `CondicionVentaTest.textosDeLosAvisos` |

## Pruebas automáticas

```
cd app-movil
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Resultado (20/11/2026, después de la condición de venta): **29 pruebas, 0 fallos** (13 de `ReservasTest` y 5 de `CondicionVentaTest`) y `assembleDebug` **BUILD SUCCESSFUL**.
