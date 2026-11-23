# Evidencia: H103 · Notificaciones e Inicio del dueño

- **Fecha:** 22/11/2026, cerca de la 1:15 p. m. (hora de Lima)
- **Dónde:** `backend-usuario` de la rama del PR #122 en **Docker**, contra la base de Supabase; web del PR #119 en **modo real** (`VITE_USE_MOCK=false`); app del comprador en el emulador de Android.
- **Ramas:** `feat/backend-notificaciones` (PR #122) y `feat/web-notificaciones` (PR #119)
- **Responsable:** Guillermo
- **Cuentas:** `rosa@boticompara.nissidev.dev` (dueña de Botica Salud Total, en la web) y `carlos@boticompara.nissidev.dev` (comprador, en la app)
- **Contrato:** `docs/API.md`, sección 12

## Prueba manual

| # | Paso | Resultado | ¿Cumple? |
|---|---|---|---|
| 1 | Otra reserva, anterior a la del paso 2 | A rosa@ le llegó "Nueva reserva BC-Y2WR8C · Paracetamol Genérico x 4 · S/ 8.80" | Sí |
| 2 | Como `carlos@`, reservar desde la app **Panadol x 1** en Botica Salud Total (**BC-7ZV62H**) | A rosa@ le llegó "Nueva reserva BC-7ZV62H · Panadol x 1 · S/ 8.20" en la campana y en **Actividad reciente**, y "Pedidos pendientes" se actualizó | Sí |
| 3 | Como `carlos@`, cancelar **BC-7ZV62H** desde la app | A rosa@ le llegó "Reserva cancelada BC-7ZV62H" | Sí |

### Inicio sin el backend de notificaciones

Mismo día, antes de levantar el #122: web del #119 en modo real contra el backend de Docker, que aún no tenía notificaciones ni reseñas.

| # | Paso | Resultado | ¿Cumple? |
|---|---|---|---|
| 4 | Entrar como `rosa@` y abrir el Inicio | 3 tarjetas (sin Calificación promedio, porque `GET /boticas/{id}/resenas` responde 404) | Sí |
| 5 | Ver **Actividad reciente** | "No pudimos cargar la actividad reciente." con **Reintentar**, sin el mensaje técnico del servidor | Sí |
| 6 | Quedarse en el panel con las consultas de notificaciones respondiendo 404 | La sesión no se cierra | Sí |

**Resultado:** 6 de 6 casos correctos.

## Probado en modo simulado

Web del #119 con `VITE_USE_MOCK=true`, en Chromium con Playwright (22/11/2026). Son comprobaciones en el navegador, no pruebas del repositorio.

| Caso | Resultado |
|---|---|
| El contador se consulta cada 30 s, no se consulta con la pestaña oculta y se consulta al volver | Correcto |
| Con un 401 deja de consultar y vuelve al login con aviso | Correcto |
| Al subir el contador: aviso flotante en una zona `aria-live="polite"` y título "(3) BotiCompara"; en la primera carga, no | Correcto |
| Pulsar una notificación la marca como leída y abre ese pedido | Correcto |
| "Marcar todas como leídas"; si el servidor falla, vuelven a verse sin leer | Correcto |
| Escape cierra el panel y el foco vuelve a la campana; con el asistente abierto cierra solo el panel | Correcto |
| Lista vacía, máximo 50 y "9+" | Correcto |
| Las tarjetas de pedidos se vuelven a pedir solo cuando sube el contador | Correcto |
| Con la botica rechazada (`lucia@`) no hay Inicio: queda en Mi botica | Correcto |
| Sin scroll horizontal, de 320 a 1024 px, en el Inicio | Correcto |

## Cubierto por pruebas automáticas

### Backend (PR #122), en `backend-usuario/src/test/java/com/boticompara/usuario/`

| Caso | Prueba |
|---|---|
| Sin token 401; comprador 403 (los 4 endpoints) | `NotificacionSeguridadTest.sinTokenDa401`, `NotificacionSeguridadTest.compradorDa403` |
| Marcar la propia da 204 sin cuerpo | `NotificacionSeguridadTest.marcarLaPropiaDa204SinCuerpo` |
| La de otro dueño y una inexistente dan el mismo 404 | `NotificacionSeguridadTest.marcarLaDeOtroDuenoYUnaInexistenteDanElMismo404`, `NotificacionServiceTest.marcarLeida_queNoExisteODeOtroDuenoDaElMismo404` |
| Cada dueño solo lista, cuenta y marca las suyas | `NotificacionSeguridadTest.cadaDuenoSoloListaCuentaYMarcaLasSuyas` |
| Parámetros inválidos dan 400 | `NotificacionSeguridadTest.parametrosInvalidosDan400`, `NotificacionServiceTest.listar_soloNoLeidasInvalidoDa400SinConsultar` |
| Lista: de la más reciente a la más antigua, máximo 50 | `NotificacionServiceTest.listar_soloLasDelDuenoDeLaMasRecienteALaMasAntiguaMaximo50` |
| Filtro `soloNoLeidas` y formato del contrato | `NotificacionServiceTest.listar_soloNoLeidas`, `NotificacionServiceTest.listar_soloNoLeidasFalseEsLoMismoQueNoEnviarlo`, `NotificacionServiceTest.listar_devuelveElFormatoDelContrato` |
| El total cuenta todas, sin el límite de 50 | `NotificacionServiceTest.totalNoLeidas_cuentaTodasLasDelDuenoSinLimite` |
| Marcar una y marcar todas, solo las propias | `NotificacionServiceTest.marcarLeida_soloLaPropia`, `NotificacionServiceTest.marcarTodasLeidas_soloLasDelDuenoYSinErrorSiNoHabia` |
| Títulos; la receta solo se avisa en la reserva nueva | `NotificacionServiceTest.titulos_comoElContrato`, `NotificacionServiceTest.mensaje_nuevaReservaConRecetaLoAvisa`, `NotificacionServiceTest.mensaje_nuevaReservaSinRecetaOSinDatoNoAgregaNada`, `NotificacionServiceTest.mensaje_canceladaNuncaAgregaLaReceta` |
| No se lee ningún dato del comprador | `NotificacionServiceTest.crear_noLeeNingunDatoDelComprador` |
| La reserva y la cancelación crean su notificación para el dueño de la botica | `NotificacionTransaccionTest.unaReservaCreaSuNuevaReservaParaElDuenoDeLaBotica`, `NotificacionTransaccionTest.unaCancelacionCreaSuReservaCanceladaParaElDuenoDeLaBotica` |
| Se guarda después del commit, en otra transacción | `NotificacionTransaccionTest.seGuardaDespuesDelCommitEnOtraTransaccionYEnElMismoHilo` |
| Si la reserva no se guarda o la cancelación se rechaza, no hay notificación | `NotificacionTransaccionTest.siLaReservaNoSeGuardaNoHayNotificacion`, `NotificacionTransaccionTest.siLaCancelacionSeRechazaNoHayNotificacion` |
| Si falla guardar la notificación, la reserva y la cancelación responden igual | `NotificacionTransaccionTest.siFallaElInsertLaReservaYLaCancelacionRespondenIgualYElLogNoTraeDatosDelComprador`, `NotificacionTransaccionTest.siNoSePuedeAbrirLaTransaccionNuevaRespondenIgual`, `NotificacionTransaccionTest.siFallaElCommitDeLaNotificacionRespondenIgual` |
| Un duplicado se ignora | `NotificacionTransaccionTest.unDuplicadoSeIgnora` |
| Cancelar publica `ReservaCancelada` | `ReservaServiceTest.cancelarUnaReservaPendienteLaPasaACancelada` |

### Web (PR #119), en `web-usuario/src/utils/`

| Caso | Prueba |
|---|---|
| Tiempo relativo, con sus límites y la fecha de Lima | `notificaciones.test.js` · "tiempoRelativo: menos de un minuto o una fecha futura es "hace un momento"", "tiempoRelativo: minutos y horas, con sus límites", "tiempoRelativo: días en singular y plural, y desde una semana la fecha de Lima", "tiempoRelativo acepta los microsegundos que manda la API" |
| Contador "9+" y nombre accesible de la campana | `notificaciones.test.js` · "textoContador: 0 no se muestra y más de 9 es "9+"", "etiquetaCampana dice el número real aunque se vea "9+"" |
| Título de la pestaña y cuándo avisar | `notificaciones.test.js` · "tituloPestana pone el contador y vuelve al título de siempre con 0", "subioContador no avisa en la primera carga, ni si baja o queda igual" |
| Ruta del pedido e ícono por tipo | `notificaciones.test.js` · "rutaPedido e iconoNotificacion" |
| Saludo según la hora de Lima y primer nombre | `inicio.test.js` · "saludo según la hora de Lima, con sus límites", "primerNombre" |
| "Por recoger hoy" con el día de Lima | `inicio.test.js` · "esHoyEnLima usa el día de Lima, no el de UTC", "porRecogerHoy cuenta solo los que vencen hoy" |

La campana, el panel, el aviso y las tarjetas **no tienen pruebas automáticas** en el repositorio; se revisaron en el navegador.

## Por confirmar

Sin evidencia registrada en modo real, con el backend del #122:

- Marcar una notificación y marcar todas como leídas.
- Abrir el pedido desde la notificación.
- El aviso flotante y el título de la pestaña.
- "· Requiere receta médica" en una reserva `CON_RECETA`.
- Que `jorge@` no vea las notificaciones de `rosa@`.
- La tarjeta de calificación con reseñas (el backend aún no las tenía).
- `RESERVA_VENCIDA`: hoy no se crea ninguna, porque falta la tarea de H84.

## Pruebas automáticas

```
cd backend-usuario && ./mvnw clean test
cd web-usuario && npm run lint && npm run build && npm test
```

- **Backend:** repetidas el 22/11/2026 sobre el commit `9e588f0` de la rama, **sin base de datos**: 612 pruebas, 0 fallos y 2 errores, los dos de `CorsConfigTest`, que arranca la aplicación completa y necesita la conexión (`DB_HOST`). Las demás, **610, pasan**. El PR #122 declara 612 pruebas y 0 fallos en su entorno.
- **Web:** **17 pruebas, 0 fallos**, con lint y build sin errores (PR #119).
