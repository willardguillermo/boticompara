# Escaneo final de seguridad del Sprint 4

- **Fecha:** 2026-11-24
- **Commit de `main` escaneado:** `c6d3372` (Merge pull request #137). La rama `docs/escaneo-sprint4` sale de ahí.
- **Modo:** solo lectura. No se cambió código; este informe es lo único que agrega el PR.
- **Alcance:** `backend-usuario/` (Spring Boot), `backend-admin/` (Django), `web-usuario/` (React), `app-movil/` (Kotlin),
  `database/`, `render.yaml` y `web-usuario/vercel.json`. Se tomaron como base `docs/API.md`, `docs/seguridad/decisiones.md`,
  `docs/seguridad/escaneo-sprint3.md`, `docs/despliegue/render.md` y `vercel.md`.
- **Secretos:** este informe no contiene ningún valor de clave, token o contraseña: solo archivo, línea y tipo.
- **Estados:** **OK** (revisado, correcto), **HALLAZGO** (con gravedad Alta, Media o Baja), **NO APLICA** y **Por confirmar**
  (no se puede asegurar desde el código; se indica cómo confirmarlo).

## Herramientas

| Herramienta | Versión | Uso |
|---|---|---|
| gitleaks | 8.30.1 | Historial completo (`--log-opts="--all"`, 486 commits) |
| `git log -G` / `--name-only` | git del sistema | Búsqueda acotada de patrones de claves y de archivos sensibles alguna vez versionados |
| osv-scanner | 2.7.0 | `backend-usuario/pom.xml`, `backend-admin/requirements.txt`, `web-usuario/package-lock.json` |
| pip-audit | 2.10.1 | `backend-admin/requirements.txt` |
| npm audit | npm del sistema | `web-usuario/` |
| `vite build` | 8.3.6 | Build de producción **fuera del repo** (carpeta temporal) con `VITE_USE_MOCK=true`, para comprobar que el simulador no entra |
| `mvnw dependency:tree` | Maven wrapper del repo | Origen de las dependencias transitivas con avisos |

## Resumen

| Gravedad | Hallazgos nuevos |
|---|---|
| Alta | 1 |
| Media | 8 |
| Baja | 11 |
| **Total** | **20** |

Además quedan abiertos SEC-11, SEC-12, SEC-13, SEC-15 y SEC-19 del Sprint 3 (ver sección 11). Sin secretos en el historial.

| ID | Gravedad | Hallazgo | Archivo:línea | Arreglo propuesto | Responsable |
|---|---|---|---|---|---|
| S4-01 | **Alta** | La tarea de H84 que pasa reservas a `VENCIDA` no existe. Las reservas siguen `PENDIENTE`/`CONFIRMADA`/`LISTA` después de `venceEn` y siguen apartando stock para siempre: unas pocas cuentas verificadas pueden dejar un producto "sin unidades" de forma permanente, y el mismo comprador ya no puede volver a reservarlo (409) | `backend-usuario/.../repository/ReservaRepository.java:28-31`, `.../service/ReservaService.java:85-89`; el único `@Scheduled` es `.../job/AlertaStockJob.java:19`; `database/schema.sql:429` | Implementar el proceso de H84 tal como está en `API.md` (sección 10). Mientras tanto, agregar `and r.venceEn > :ahora` a `unidadesApartadas` y a `existsByUsuarioIdAndProductoIdAndEstadoIn` | Alexander |
| S4-02 | Media | Cuando un **dueño** elimina su cuenta (H19), su botica sigue `APROBADO` (retiene el RUC por `uq_botica_ruc_aprobado`) y sus reservas activas quedan sin atender hasta vencer (y por S4-01 nunca vencen). Igual con "Dar de baja" en Django | `.../service/UsuarioService.java:61-82` (solo `desactivarPorBotica`, línea 78); `database/schema.sql:98-99`; `backend-admin/boticas/usuarios.py:124-133` | Ver detalle en la sección 6: pasar la botica a `RECHAZADO` con motivo, rechazar sus reservas activas con correo al comprador, y lo mismo en Django | Alexander (API), Guillermo (Django y `API.md`) |
| S4-03 | Media | La cuota de correo (100 al día) no tiene tope global. `POST /reservas` no limita reservas activas ni por día, y cada una envía un correo al dueño; registro y recuperar solo limitan por IP. Una cuenta verificada (o IP rotativas) agota los correos de verificación y recuperación de todos | `.../service/ReservaService.java:57-110`, `.../service/PedidoCorreoService.java:44-61`, `.../limite/LimitesPorIp.java:37-48` | Máximo de reservas activas por comprador (por ejemplo 5) y por día; contador global diario en `CorreoService` que priorice verificación y recuperación | Alexander |
| S4-04 | Media | El tope global de SUNAT (10 al día) se puede gastar a propósito con 2 cuentas de dueño (5 por hora cada una). Desde ahí, todo registro de botica pasa solo con la validación local: sin revisar estado ni condición en SUNAT y sin la regla del titular (A7) | `.../service/RucSunatService.java:110-112`, `.../limite/CuotaSunat.java:62-64`, `.../service/BoticaService.java:110` | Guardar de dónde salió la validación (S4-05) para que el revisor lo vea; opcional: tope global por cuentas nuevas (no por todas) | Alexander, Guillermo |
| S4-05 | Media | A6: el revisor no puede saber si el RUC y la razón social los confirmó SUNAT. Si SUNAT no respondió, `razon_social` es la que escribió el dueño, y la tabla no guarda la fuente | `.../service/BoticaService.java:140-143`; `database/schema.sql:70-91`; `backend-admin/boticas/admin.py:640-647` | Columna `ruc_fuente` (`SUNAT`/`LOCAL`) en `schema.sql`, llenarla en `BoticaService` y mostrarla en "Evidencia para la aprobación" con un aviso si es `LOCAL` | Guillermo (BD, admin), Alexander (API) |
| S4-06 | Media | A7 se puede saltar: el 400 dice el nombre exacto del titular según SUNAT y el dueño puede ponerse ese nombre con `PUT /usuarios/me` antes de reenviar. No prueba identidad | `.../service/BoticaService.java:113-117`, `.../service/UsuarioService.java:40-46` | Tratar A7 como ayuda, no como control: en el admin, comparar el nombre de la licencia con el titular de SUNAT (S4-05). No repetir el nombre completo en el mensaje del 400 | Alexander, Guillermo |
| S4-07 | Media | La licencia se puede reemplazar **después** de aprobada (o mientras el revisor la mira): `POST /boticas/mia/licencia` no revisa el estado. Lo que queda guardado puede no ser lo que se aprobó | `.../service/LicenciaService.java:25-49`; `docs/API.md:302` | Aceptar la subida solo en `PENDIENTE` o `RECHAZADO` (409 en `APROBADO`), con el cambio primero en `API.md` | Alexander, Guillermo (`API.md`) |
| S4-08 | Media | Django aprueba boticas **sin licencia** (ni la acción masiva ni el botón lo exigen), y la acción masiva también aprueba boticas `RECHAZADO` sin pasar por `PENDIENTE` | `backend-admin/boticas/admin.py:696-704` (sin `desde`), `admin.py:815`, `backend-admin/boticas/revision.py:51-92` | En `revision.revisar` al aprobar: exigir `licencia_url` y `desde=PUEDE_APROBAR` también en la acción masiva | Guillermo |
| S4-09 | Media | En `main` no está `SERVER_TOMCAT_REMOTEIP_REMOTEIPHEADER=CF-Connecting-IP` y `API.md` sigue diciendo `X-Forwarded-For`. Sin esa variable, en Render los límites por IP (login, registro, recuperar) no bloquean (prueba del 23/11 en el PR #139) | `render.yaml` (no está la variable), `docs/API.md:124`, `backend-usuario/src/main/resources/application.properties:71` | Integrar el PR #139. **Por confirmar** que la variable esté en el panel de Render (Environment) | Guillermo, revisa Alexander |
| S4-10 | Media | Dependencias que trae `firebase-admin` 9.12.0 con avisos conocidos: netty 4.1.135 (hasta CVSS 9.1), guava 33.6.0 (8.2), httpcore5 5.3.6 (7.5), opentelemetry 1.49.0. Llegan con `google-cloud-firestore`, que la API no usa. Siguen además pgjdbc 42.7.11, commons-lang3 3.17.0 y log4j-api 2.24.3 | `backend-usuario/pom.xml:40-50` | Excluir `google-cloud-firestore` (como ya se excluye `google-cloud-storage`), fijar `postgresql.version` 42.7.12 y volver a pasar osv-scanner. **Por confirmar** si el código usa la parte vulnerable (sección 10) | Alexander |
| S4-11 | Baja | Carrera en el contador de intentos de `POST /auth/restablecer`: sin bloqueo de fila ni `@Version`, peticiones en paralelo leen el mismo `intentos` y pasan de 5 por código. El endpoint no tiene límite por IP | `.../service/RecuperacionService.java:101-120`, `.../entity/TokenUsuario.java:37-39` | `select ... for update` de los códigos del usuario (como `bloquearPropia`) o `@Version`; y límite por IP como en recuperar | Alexander |
| S4-12 | Baja | Login: sin límite por IP entre correos distintos (cada fallo con correo real ejecuta BCrypt, con 0,1 CPU en Render). Las entradas bloqueadas nunca se purgan y la limpieza recorre el mapa en cada fallo pasadas las 10 000 claves | `.../auth/LimitadorLogin.java:48-66` | Límite por IP sola (por ejemplo 30 fallos en 15 min) y purgar también los bloqueos vencidos | Alexander |
| S4-13 | Baja | `POST /auth/login` no tiene `@Valid`: con `correo` o `password` nulos responde 500 y escribe la traza en el log, en vez de 400 | `.../controller/AuthController.java:61` | Agregar `@Valid` (el DTO ya tiene `@NotBlank`) | Alexander |
| S4-14 | Baja | Datos personales en los logs: el correo del destinatario cuando falla el envío; el RUC (un RUC 10 contiene el DNI); y el detalle de las restricciones de la BD, que puede incluir el valor (por ejemplo el correo) | `.../service/CorreoService.java:44`, `.../service/RucSunatService.java:119`, `.../service/DecolectaRucClient.java:44,60,68,76,82`, `.../error/GlobalExceptionHandler.java:79` | Registrar el id del usuario en vez del correo, el RUC enmascarado (`10****1234`) y solo el nombre de la restricción | Alexander |
| S4-15 | Baja | El filtro de crisis no reconoce frases de sobredosis ("sobredosis", "tomarme todas las pastillas", "dosis letal"), que son el riesgo más propio de una app de medicamentos | `.../chat/FiltroCrisis.java:27-29` | Agregar esas frases y pruebas en `FiltroCrisisTest` | Guillermo |
| S4-16 | Baja | `POST /chat` no limita cuántos elementos trae `historial` antes de validarlos (recién después se queda con 10), ni el tamaño del JSON | `.../chat/ChatService.java:212-229` | 400 si `historial` trae más de 20 elementos | Guillermo |
| S4-17 | Baja | Lo que el usuario escribe en el chat (puede incluir datos de salud) llega a Google. No hay aviso al usuario y depende del plan de Gemini si se usa para mejorar sus productos | `.../chat/gemini/GeminiRestCliente.java:66-73` | **Por confirmar** el plan de la clave en Google AI Studio; poner un aviso corto en el chat ("no escribas datos personales") | Guillermo |
| S4-18 | Baja | En el móvil, el texto plano hacia `10.0.2.2` está permitido también en el build release, porque la regla está en `src/main` | `app-movil/app/src/main/res/xml/network_security_config.xml:6-8` | Mover esa regla a `app/src/debug/res/xml/` | Mijael |
| S4-19 | Baja | El JWT lleva el correo en claro, aunque el servidor no lo lee al validar | `.../security/JwtService.java:37` | Quitar el claim `correo` (los clientes lo tienen en la respuesta del login) | Alexander |
| S4-20 | Baja | `GET /medicamentos` funciona para cualquier rol aunque `API.md` lo pone en la sección del dueño; el admin de reseñas muestra el correo del autor, que el moderador no necesita | `.../security/SecurityConfig.java:58`, `docs/API.md:330`; `backend-admin/boticas/admin.py:351` | Regla `hasRole("DUENO_BOTICA")` para `/medicamentos/**` o aclarar el contrato; mostrar solo el nombre en el admin de reseñas | Alexander, Guillermo |

---

## 1. Autenticación y sesión

| Punto | Estado | Evidencia |
|---|---|---|
| Firma del JWT | OK | HMAC con la clave de `JWT_SECRET`; `verifyWith(key)` rechaza `alg: none` y otra firma (`security/JwtService.java:28,47`) |
| Expiración | OK | 24 h (`application.properties:21`, `JwtService.java:39`) |
| Secreto desde variable | OK | `jwt.secret=${JWT_SECRET}` sin valor por defecto (`application.properties:20`); no arranca con menos de 32 bytes (`JwtService.java:25-26`) |
| Cuenta activa en cada petición | OK | `JwtAuthFilter.java:33` (`puedeEntrar`: activo y no suspendido) |
| Revocación al cambiar la contraseña o cerrar sesión | HALLAZGO Baja (SEC-11, abierto) | No hay versión de sesión; el token anterior sigue valiendo hasta 24 h |
| Límite del login (SEC-05) | OK en el código; **Por confirmar** en Render (S4-09) | 5 fallos por correo + IP bloquean 15 min (`auth/LimitadorLogin.java:17-19`). La IP es `getRemoteAddr()` (`AuthController.java:62`) con `server.forward-headers-strategy=native` (`application.properties:71`). En Render solo es la IP real con `CF-Connecting-IP` (PR #139, abierto) |
| Validación del login | HALLAZGO Baja (S4-13) | Sin `@Valid` (`AuthController.java:61`) |
| Tiempo de respuesta según el correo exista | HALLAZGO Baja (SEC-12, abierto) | `AuthService.java:64`: sin BCrypt si el correo no existe |
| Contador en memoria | HALLAZGO Baja (S4-12) | Ver resumen. Se pierde al reiniciar (aceptado en SEC-05) |
| Google: firma, emisor, audiencia y expiración | OK | `FirebaseAuth.verifyIdToken` con `setProjectId(FIREBASE_PROJECT_ID)` (`security/FirebaseVerificadorGoogle.java:47-51,67`); sin configuración responde 503 |
| Google: `email_verified` | OK | Sin correo o con `email_verified` distinto de `true` → 401 (`FirebaseVerificadorGoogle.java:75-79`) |
| Google: no vincula cuentas existentes | OK | 409 si el correo ya tiene cuenta (`AuthService.java:100-102`); siempre crea `COMPRADOR` (`:107`) |
| Recuperar: código | OK | 6 dígitos con `SecureRandom`, guardado como HMAC-SHA256, 15 min, un solo uso (`RecuperacionService.java:81-87,128-137`) |
| Recuperar: límites | OK | 1 por minuto, 3 por hora por cuenta (`:67-76`), 5 por hora por IP (`LimitesPorIp.java:46-48`) y siempre 200 |
| Restablecer: intentos | HALLAZGO Baja (S4-11) | 5 por código, comparación en tiempo constante (`:139-141`), pero sin bloqueo de fila |
| Verificación de correo | OK | 32 bytes aleatorios, HMAC, 48 h, un solo uso (`VerificacionService.java:58-67,101-107`). La página HTML no refleja nada del usuario (`AuthController.java:90-117`) |
| Clave HMAC separada | HALLAZGO Baja (SEC-13, abierto) | `RecuperacionService.java:47,52` y `VerificacionService.java:38,43` usan `jwt.secret` |
| Contraseñas | OK | BCrypt (`SecurityConfig.java:65`), 8 a 72 caracteres en registro y restablecer |

## 2. Autorización e IDOR

**Reglas por rol** (`security/SecurityConfig.java:43-58`), comparadas con cada 🔒 de `API.md`:

| Rutas | Regla | Contrato | Estado |
|---|---|---|---|
| `/auth/**`, `GET /salud`, `GET /productos/{id}`, `GET /productos/{id}/alternativas`, `GET /boticas/*/resenas` | Públicas | Sin 🔒 | OK |
| `/boticas/**` (incluye `validar-ruc`, A1), `/productos/**`, `/pedidos/**`, `/notificaciones/**` | `DUENO_BOTICA` | Secciones 3, 4, 10, 12 | OK |
| `PUT /boticas/*/resenas/mia` | `COMPRADOR` (va antes de `/boticas/**`) | Sección 11 | OK |
| `/reservas/**` | `COMPRADOR` | Sección 9 | OK |
| `/usuarios/me`, `/productos/buscar`, `/favoritos/**`, `/alertas/**`, `/chat` | Cualquier rol con sesión | Secciones 2, 5, 6, 7, 8 ("cualquier usuario con sesión") | OK |
| `/medicamentos` | Cualquier rol con sesión | Está en la sección 4 (dueño) | HALLAZGO Baja (S4-20). Solo lee el catálogo de DIGEMID |
| `PUT /resenas/{id}/respuesta` (H88) | No existe en el código | Sección 11 | NO APLICA (no implementado) |

El id del usuario sale siempre del token (`@AuthenticationPrincipal`); ningún endpoint lo acepta en el cuerpo ni en la URL.

**Cada uno solo ve y modifica lo suyo:**

| Recurso | Consulta | Estado |
|---|---|---|
| Productos | Editar y borrar: `bloquearPorId(id).filter(p -> boticaId del token)` (`ProductoService.java:99-103`); desenlazar: `findByIdAndBoticaIdAndActivoTrue` (`:93-96`); crear y cargar exigen botica `APROBADO` (`:83-90`, `CargaMasivaService.java:135-139`) | OK |
| Pedidos | `pedidoDeBotica` y `bloquearPedidoDeBotica` filtran por la botica del token (`ReservaRepository.java:51-54,64-68`, `PedidoService.java:151-167`). Al dueño solo le llega el nombre corto del comprador (`PedidoResponse.java:36`) | OK |
| Reservas | `findByIdAndUsuarioId` y `bloquearPropia` (`ReservaRepository.java:25,71-73`) | OK |
| Notificaciones | `where id = :id and usuario_id = :usuario` en todo (`NotificacionService.java:41,57,65,74`) | OK |
| Reseñas | Autor = token; exige reserva `ENTREGADA` en esa botica (`ResenaService.java:92-94`); la lista pública solo trae nombre corto (`:63`) | OK |
| Favoritos | `usuario_id = :usuario` en listar, agregar, cambiar y quitar (`FavoritoService.java:31,42-45,64,82`) | OK |
| Alertas | `usuario_id = :usuario` en todo (`AlertaService.java:35,58,66,75,93`) | OK |
| Botica y licencia | Solo `findByUsuarioId` del token (`BoticaService.java:89-92`); ruta en Storage fija por id (`LicenciaService.java:37`) | OK |

**Sin IDOR.** Probar el id de otro devuelve el mismo 404 que un id inexistente. No hay SQL concatenado con datos del usuario
(los únicos agregados son constantes, como en el Sprint 3).

## 3. Abuso de cuotas (A1–A8 del 23/11)

| Punto | Estado | Evidencia |
|---|---|---|
| A1 `validar-ruc` | OK | Exige `DUENO_BOTICA` (`SecurityConfig.java:54`, `RucController.java:24-27`); 5 por hora por usuario y 10 por día globales (`limite/CuotaSunat.java:51-68`); la caché de 24 h y los RUC mal escritos no gastan (`RucSunatService.java:76-79,96-99`) |
| Tope global de SUNAT como palanca | HALLAZGO Media (S4-04) | Ver resumen |
| A2 chat: acceso | OK | Dueño con botica `APROBADO`, comprador con correo verificado, antes del cuerpo y de los límites (`chat/AccesoChat.java:29-45`, `ChatController.java:25`) |
| A2 chat: límites | OK | 3 por minuto, 15 por día por usuario, 150 por día globales (`chat/LimitadorChat.java:76-106`, `application.properties:67-69`). Residual aceptado: unas 10 cuentas verificadas pueden gastar el tope global del día |
| A3 registro | OK | 5 por hora por IP; un 400 o 409 devuelve el cupo (`AuthController.java:39-51`) |
| A3 recuperar | OK | 5 por hora por IP, cuentan todas (`AuthController.java:66-72`) |
| Tope global de correos | HALLAZGO Media (S4-03) | Ver resumen |
| A4 reservas con correo verificado | OK | 403 antes de todo lo demás salvo la cantidad (`ReservaService.java:63-66`) |
| Reservas sin vencer | HALLAZGO **Alta** (S4-01) | Ver resumen |
| A5 RUC solo único entre aprobadas | OK | Índice parcial (`database/schema.sql:98-99`), `existsByRucAndEstado` (`BoticaService.java:98-102`), Django bloquea y rechaza las pendientes del mismo RUC (`revision.py:69-92`) |
| A6 qué ve el revisor | HALLAZGO Media (S4-05, S4-08) | Ver sección 8 |
| A7 titular de un RUC de persona | HALLAZGO Media (S4-06) | Ver resumen |
| A8 | **Por confirmar** | El repositorio no documenta un A8: `API.md` y los PR #136 y #137 solo nombran A1 a A5 y A7. Se confirma con la lista original del 23/11 |
| Contadores en memoria al reiniciar | Aceptado | `LimitadorLogin`, `LimitadorPorClave`, `CuotaSunat` y `LimitadorChat` viven en memoria. Al reiniciar (deploy, cambio de variable, o el plan gratis al dormirse a los 15 min, `render.md` sección 8) **todos vuelven a cero**: se libera el bloqueo del login y vuelven los cupos diarios de SUNAT (10) y del chat (150). Riesgo real: un atacante no puede forzar el reinicio, pero el plan gratis se duerme solo. Si hiciera falta, el tope diario de SUNAT es el que más conviene guardar en la BD (la cuota de Decolecta es mensual) |
| Límites con IP rotativas | Aceptado, con S4-03 | `LimitadorPorClave` vacía el mapa al llegar a 10 000 claves (`LimitadorPorClave.java:72-80`): con muchas IP (IPv6) se reinician los contadores. El tope global de correos de S4-03 lo cubre |

## 4. Validación de entrada y salida

| Punto | Estado | Evidencia |
|---|---|---|
| `@Valid` en los cuerpos | OK salvo login | Registro, perfil, botica, productos, reservas, favoritos, reseñas, pedidos, recuperar y restablecer lo tienen. Login no (S4-13). `/auth/google` y `/chat` validan en el servicio (`FirebaseVerificadorGoogle.java:59-61`, `ChatService.java:197-251`) |
| Longitudes | OK | Coinciden con las columnas: botica (`BoticaRequest.java:12-41`), producto (`ProductoRequest.java:13-36`), perfil (`PerfilRequest.java:8-16`), reseña 500 (`ResenaService.java:86-88`), motivo de rechazo 10–300 (`PedidoService.java:100-101`), chat 500 y 1000 por mensaje del historial (`ChatService.java:45-46`) |
| Historial del chat | HALLAZGO Baja (S4-16) | Sin tope de elementos antes de validar |
| Carga masiva: tamaño | OK | `max-file-size=5MB` y `max-request-size=6MB` (`application.properties:32-33`); 1000 filas (`CsvProductoParser.java:21,102-103`) |
| Carga masiva: tipo | OK | Por contenido: ZIP → `.xlsx`, `.xls` rechazado, lo demás CSV (`CsvProductoParser.java:59-64`, `XlsxProductoLector.java:24`). Apache POI 5.5.1 con protección contra zip bombs |
| Licencia: tipo y tamaño | OK | Firma de bytes PDF/JPG/PNG (`util/TipoArchivo.java:8-23`), 5 MB (`LicenciaService.java:15,31-33`) |
| Licencia: ruta en Storage | OK | `botica-{id}/licencia.{ext}`, nunca el nombre que manda el usuario (`LicenciaService.java:37`); bucket privado; el admin la abre con URL firmada (`backend-admin/boticas/storage.py:33-44`) |
| Licencia: cuándo se puede cambiar | HALLAZGO Media (S4-07) | Ver resumen |
| Reseñas y chat escapados en la web | OK | Ningún `dangerouslySetInnerHTML`, `innerHTML`, `eval` ni `new Function` en `web-usuario/src`. El único enlace dinámico es `tel:` con 106 o 113 fijos (`utils/chat.js:48`, `components/AsistenteChat.jsx:27`). La respuesta del chat llega ya sin markdown ni enlaces (`chat/LimpiadorTexto.java:52-56`) |
| Reseñas en el admin | OK | Todo pasa por `format_html` (`admin.py:351,355`); ni `mark_safe`, `\|safe` ni `autoescape off` en `boticas/`, `catalogo/`, `reportes/` ni `config/` |
| Correos | OK | Todos en texto plano (`CorreoService.java:36-40`): un nombre o motivo con HTML no se interpreta |

## 5. Chat con IA

| Punto | Estado | Evidencia |
|---|---|---|
| Inyección directa (el usuario) | OK | Las herramientas reciben `ContextoChat` con el id y el rol **del token** (`ChatService.java:98`); el modelo no puede pedir datos de otro usuario. El historial lo manda el cliente y puede traer turnos de "asistente" inventados, pero solo afecta su propia conversación |
| Inyección indirecta (nombres de productos y boticas) | OK con riesgo residual | Las instrucciones dicen que los datos de las herramientas no son órdenes (`resources/chat/instrucciones-comprador.txt:47-49`) y se borran los enlaces de la respuesta (`LimpiadorTexto.java:55`). Un dueño podría poner un nombre de producto engañoso; lo peor es un texto raro, sin acceso a datos |
| Sin datos personales al modelo | OK | `ChatService` no usa `UsuarioRepository` (`:37-39`); `AccesoChat` solo lee el estado de la botica y si el correo está verificado (`AccesoChat.java:31-41`); lat y lng no van a Gemini (`ChatRequest.java:5`) |
| Lo que escribe el usuario | HALLAZGO Baja (S4-17) | Llega a Google tal cual |
| Herramientas solo de lectura y por rol | OK | Se declaran y ejecutan solo las del rol (`herramienta/RegistroHerramientas.java:28-39`); el catálogo propio usa el id del token (`CatalogoPropio.java:35,43`); los precios salen de `v_producto_busqueda` (`BuscarPrecios.java:26`). Ninguna escribe |
| Límites de rondas y tiempo | OK | 2 rondas, 3 herramientas por ronda, 25 s por llamada y 50 s en total (`ChatService.java:45-52`) |
| Filtro de crisis | OK, con HALLAZGO Baja (S4-15) | Responde antes de Gemini (`ChatService.java:91-96`); todos los errores terminan con SAMU 106 y Línea 113 (`LimitadorChat.java:30-35`). Falta sobredosis |
| Logs sin datos sensibles | OK | Solo rol, rondas, nombres de herramientas, tarjetas y ms; nunca el texto ni el id (`ChatService.java:94,117,130`). Errores de Gemini: solo el código HTTP y el estado (`GeminiRestCliente.java:76`) |
| Clave de Gemini | OK | En la cabecera `x-goog-api-key`, no en la URL (`GeminiRestCliente.java:69`) |

## 6. Datos personales (Ley 29733)

| Punto | Estado | Evidencia |
|---|---|---|
| Logs | HALLAZGO Baja (S4-14) | Correo del destinatario, RUC y detalle de las restricciones. Lo demás registra ids, no nombres |
| Correos al dueño | OK | Nueva reserva: nombre corto del comprador, sin correo ni teléfono (`PedidoCorreoService.java:30-33,79`) |
| Correos al comprador | OK | Solo datos de la botica (`PedidoCorreoService.java:35-38,138-140`) |
| Notificaciones del dueño | OK | La consulta no pasa por `usuario`: ningún dato del comprador (`NotificacionService.java:29-33`) |
| Pedidos del dueño | OK | Nombre corto (`PedidoResponse.java:36`, `NombreCorto.java:17-26`) |
| JWT | HALLAZGO Baja (S4-19) | Lleva el correo |
| Admin | HALLAZGO Baja (S4-20) | El moderador de reseñas ve el correo del autor |
| Eliminación de cuenta (H19) | OK para el comprador; HALLAZGO Media (S4-02) para el dueño | Se anonimizan nombre, correo, teléfono y dirección, la contraseña deja de servir, se quita `google_uid` y se borran favoritos, alertas y códigos (`UsuarioService.java:61-82`) |

### El caso del dueño que elimina su cuenta: **confirmado**

En `UsuarioService.eliminar` (`backend-usuario/.../service/UsuarioService.java:61-82`), lo único que se hace con la botica es
`productos.desactivarPorBotica(b.getId())` (línea 78). Por lo tanto:

1. **La botica sigue `APROBADO`.** `botica.estado` no se toca. Como `uq_botica_ruc_aprobado` es único donde `estado = 'APROBADO'`
   (`database/schema.sql:98-99`), **el RUC queda retenido**: si el dueño vuelve con otra cuenta, o la botica pasa a otra persona,
   `POST /boticas` responde 409 "El RUC ya está registrado" (`BoticaService.java:98-102`) y Django tampoco deja aprobar otra
   (`revision.py:48`). La botica también sigue mostrando y aceptando reseñas, porque `ResenaService.boticaAprobada` solo mira el
   estado (`ResenaService.java:111-116`). Si la razón social es de un RUC 10, queda el nombre de una persona en una botica de una
   cuenta que pidió irse.
2. **Sus reservas activas quedan sin atender.** No se toca ninguna reserva `PENDIENTE`, `CONFIRMADA` ni `LISTA`. El dueño ya no
   puede entrar a rechazarlas y el comprador no recibe aviso. Deberían vencer a las 24 h (`ReservaService.java:104`), pero por
   S4-01 nunca pasan a `VENCIDA`: el comprador las ve activas sin fecha de fin y no puede volver a reservar ese producto.
   Los correos que salgan a esa cuenta (por ejemplo, el de una reserva creada justo antes) van a `eliminado-{id}@boticompara.local`,
   que rebota y gasta la cuota.
3. **Lo mismo pasa con "Dar de baja" en Django** (`backend-admin/boticas/usuarios.py:124-133`): solo cambia `usuario.activo`. Los
   productos dejan de verse porque `v_producto_busqueda` exige `u.activo` (`schema.sql:492-493`), pero la botica y las reservas
   quedan igual.

**Arreglo propuesto** (primero `API.md`, sección 2, `DELETE /usuarios/me`):

- **API (Alexander), en la misma transacción de `eliminar`:**
  - Si tiene botica: `estado = 'RECHAZADO'` y `motivo_rechazo = 'La cuenta del dueño fue eliminada'`. Cumple
    `chk_motivo_rechazo` (`schema.sql:89-90`) y libera el RUC, porque el índice parcial solo cuenta `APROBADO`. No hace falta tocar
    `schema.sql`. (Si se prefiere que quede en el historial de revisiones, agregar una fila en `botica_revision` con
    `revisado_por = 'sistema'`.)
  - Sus reservas `PENDIENTE`, `CONFIRMADA` y `LISTA` pasan a `RECHAZADA` con el motivo "La botica dejó de atender en BotiCompara",
    publicando `EstadoPedidoCambiado` para que cada comprador reciba el correo de H83 (`PedidoCorreoService.java:91-99`).
  - Para el comprador que se elimina: sus reservas activas pasan a `CANCELADA` (libera las unidades) publicando `ReservaCancelada`.
  - Opcional, por minimización: si el RUC es 10 o 15, reemplazar `razon_social` y `telefono` de la botica.
  - Pruebas en `UsuarioServiceTest`: botica `RECHAZADO` con motivo, otra cuenta puede registrar el mismo RUC, reservas rechazadas
    con evento.
- **Django (Guillermo):** `dar_baja` (`usuarios.py:124`) llama a `revision.revisar([botica.pk], RECHAZADO, ..., motivo=...)` y
  rechaza las reservas activas de esa botica, con su prueba.
- **Web (Guillermo):** el aviso de "Eliminar cuenta" (PR #141, abierto) debería decir que la botica deja de estar aprobada y que sus
  pedidos pendientes se rechazan.

## 7. Configuración y despliegue

| Punto | Estado | Evidencia |
|---|---|---|
| CORS | OK | Orígenes desde `CORS_ORIGINS`, sin comodines, sin `allowCredentials`, solo `Authorization` y `Content-Type` (`SecurityConfig.java:31,70-81`). SEC-10 cerrado |
| CSRF en la API | OK, NO APLICA | `csrf.disable()` (`SecurityConfig.java:37`): la API no usa cookies; el token va en `Authorization` (`decisiones.md`, SEC-06) |
| CSP de la web | OK | `default-src 'none'`, `script-src 'self'`, `connect-src` solo la API de Render y Nominatim, `frame-ancestors 'none'`, `base-uri 'none'`, `form-action 'self'` (`web-usuario/vercel.json:14`) |
| Otras cabeceras | OK | `nosniff`, `Referrer-Policy`, `Permissions-Policy` (`vercel.json:16-21`) |
| HSTS en la web | **Por confirmar** | No está en `vercel.json`. Vercel la agrega en sus dominios; se confirma con `curl -sI https://TU-WEB.vercel.app \| grep -i strict-transport` |
| Secretos en el repo | OK | Solo `.env.example` y `web-usuario/.env.example` versionados, con las claves vacías. Sin `google-services.json`, `local.properties`, llaves ni JSON de Firebase (`git ls-files`); los dos primeros están en `.gitignore` (`app-movil/app/.gitignore:2`, `app-movil/.gitignore:15`). `git grep` sin claves de Google, Supabase, Resend, JWT ni llaves privadas |
| Secretos en el historial | OK | gitleaks: 3 coincidencias, las 3 falsos positivos (`docs/API.md:71` y `:97` en `e5a6b0da` y `b97af979`: token de ejemplo truncado; `docs/pruebas/sprint3/H59.md:17` en `3707196b`: el texto "sin token, \`soloNoLeidas=quizas\`"). `git log -G` con asignaciones de `JWT_SECRET`, `DB_PASSWORD`, `SUPABASE_SECRET_KEY`, `MAIL_PASSWORD`, `GEMINI_API_KEY`, `RUC_API_TOKEN` y `DJANGO_SECRET_KEY`: nada. Un `.env` estuvo versionado entre `1554670` (30/10) y `43f9024` (31/10): era una copia del `.env.example` **con todas las claves vacías**. No hay que rotar nada por esto |
| Secretos en Render | OK | Todas las variables con `sync: false` (`render.yaml`); Firebase como Secret File, fuera de la imagen (`render.md` sección 5) |
| Simulador fuera del build | OK, comprobado | `import()` solo con `import.meta.env.DEV` (`services/index.js:14-21`, `config.js:4`). Build de producción con `VITE_USE_MOCK=true` en una carpeta temporal: el JS no tiene `apiMock`, `registrarAyudaConsola` ni la contraseña de prueba |
| Actuator u otros endpoints | OK | No hay `spring-boot-starter-actuator` (`pom.xml`) ni propiedades `management.*`. Lo público es `/auth/**`, `/salud` y tres GET de solo lectura. El usuario en memoria de Spring está apagado (`application.properties:24`) |
| Errores sin trazas | OK | `GlobalExceptionHandler.java:93-97` responde un mensaje genérico y deja la traza solo en el log; 401 y 403 con el formato del contrato (`SecurityErrorHandler.java:26-33`); integridad sin SQL (`:77-81`) |
| Imagen Docker | OK | Usuario sin privilegios (`Dockerfile`: `USER boticompara`), compilación en otra etapa |
| IP real en Render | HALLAZGO Media (S4-09) | Ver resumen |
| Contraseña de las cuentas de prueba | **Por confirmar** | `Boti2026!` está en `CLAUDE.md` y `docs/API.md` (repo público). Se confirma que se cambió en la base de producción con `POST /auth/login` de `rosa@` contra Render: debe dar 401 |

## 8. Admin de Django

| Punto | Estado | Evidencia |
|---|---|---|
| Autenticación | OK | Solo `admin/` (`config/urls.py`); todas las vistas propias van por `admin_site.admin_view` (`admin.py:145,377,756`) y revisan `has_change_permission` (`admin.py:774,799`) |
| Límite de intentos | OK (SEC-14) | django-axes, 5 fallos por usuario + IP, 15 min (`settings.py:189-201`). Caché en memoria: se reinicia con el proceso |
| CSRF | OK | `CsrfViewMiddleware` (`settings.py:74`), sin `csrf_exempt`; aprobar, rechazar, suspender, reactivar, dar de baja, ocultar y mostrar solo actúan con POST (`admin.py:228,243,260,448,460,837,863`) |
| Acciones masivas | HALLAZGO Media (S4-08) | Una sola: "Aprobar boticas seleccionadas" (`admin.py:695-704`). Bloquea las filas, impide dos del mismo RUC (`revision.py:69-92,95-106`) y deja historial, pero no exige licencia ni que la botica esté `PENDIENTE` |
| Qué ve el revisor (A6) | HALLAZGO Media (S4-05) | Ve nombre, RUC, razón social, dirección, teléfono, licencia (URL firmada), mapa y correo del dueño (`admin.py:640-647`). No ve si SUNAT confirmó el RUC ni el estado y condición de SUNAT |
| Ajustes de producción | HALLAZGO Baja (SEC-15, abierto) / NO APLICA hoy | Sin `SECURE_*`, `SESSION_COOKIE_SECURE` ni `CSRF_COOKIE_SECURE` en `settings.py`. El admin no se despliega en este sprint (`render.md` solo trae la API); aplica en cuanto se publique |

## 9. App Android

| Punto | Estado | Evidencia |
|---|---|---|
| Token cifrado (SEC-09) | OK, **cerrado** | AES-GCM con clave de Android Keystore (`data/local/TokenManager.kt:84-118`), PR #124 |
| Copias de seguridad | OK | `allowBackup="false"` y `datastore/` excluido del respaldo y de la transferencia (`AndroidManifest.xml:18-21`, `res/xml/data_extraction_rules.xml`, `res/xml/backup_rules.xml`) |
| Network security config (SEC-07) | OK, **cerrado**; HALLAZGO Baja (S4-18) | HTTPS obligatorio salvo `10.0.2.2` (`res/xml/network_security_config.xml:4-8`); esa excepción también entra al release |
| `BASE_URL` | OK | Desde `local.properties` (ignorado), con `http://10.0.2.2:8080/api/` por defecto (`app/build.gradle.kts:7,29`). **Por confirmar** que el APK de la demo use la URL HTTPS de Render: `aapt dump` o revisar `BuildConfig` del APK |
| Logs | OK | Sin `HttpLoggingInterceptor`; un solo `Log.w` con el código de error del reconocimiento de voz (`ui/chat/VozManager.kt:218`) |
| Claves en el código | OK | `google-services.json` no está versionado; sin claves en `app/src` |
| Actividades exportadas | OK | Solo `MainActivity` con el lanzador (`AndroidManifest.xml:27-38`) |

## 10. Dependencias

| Componente | Versión | Avisos | Estado |
|---|---|---|---|
| Spring Boot | 3.5.16 (`pom.xml:9`), Tomcat 10.1.60 (`:22`), Jackson 2.21.7 (`:23`) | Sin avisos en Spring Boot, Spring Security, Tomcat ni Jackson | OK |
| `spring-webmvc` | 6.2.19 | GHSA-pc63-qcmh-9cmg y GHSA-j9f9-w8pj-32f8 (9.8): `XsltView` y fragmentos SSE, que la API no usa (no hay `XsltView` ni `SseEmitter`). Solo se corrigen en Spring 7 | Aceptado (igual que en el Sprint 3) |
| `firebase-admin` | 9.12.0 (`pom.xml:42`) | Trae netty 4.1.135 (GHSA-c4c3-7fpv-j4q5 9.1, GHSA-558v-64gr-wgg4 8.7, GHSA-jppx-w49h-x2qq 8.7 y 8 más), guava 33.6.0 (GHSA-xxph-c9ww-hj94 8.2), httpclient5 5.5.2 / httpcore5 5.3.6 (GHSA-hf6x-8p5f-cgmf, GHSA-v3jc-474w-2wm6 7.5) y opentelemetry 1.49.0 (5.3). Según `mvnw dependency:tree`, opentelemetry llega por `google-cloud-firestore`; netty, guava y httpclient5, directo de `firebase-admin` | HALLAZGO Media (S4-10). La API solo usa `verifyIdToken`. **Por confirmar** si alguna de esas rutas se ejecuta: revisar con qué transporte HTTP se descargan las llaves públicas de Google |
| `postgresql` | 42.7.11 | GHSA-j92g-9f8w-j867 (8.2), de `channelBinding`, que no se configura | Pendiente de SEC-01 (S4-10) |
| `commons-lang3` / `log4j-api` | 3.17.0 / 2.24.3 | GHSA-j288-q9x7-2f5v (6.5) / GHSA-qv9r-c865-cp47 (6.3); llegan con Apache POI | Pendiente de SEC-01 (S4-10) |
| Django | 5.2.18 (`requirements.txt`) | pip-audit y osv-scanner: ninguno | OK |
| django-axes, psycopg, openpyxl, defusedxml, python-dotenv | Versiones fijas (`requirements.txt`) | Ninguno | OK |
| React / React Router | 19.3.0 (rango `^19.2.8`) / 7.18.4 | npm audit y osv-scanner: 0 | OK |
| Vite | 8.3.6 | 0 (SEC-02 cerrado) | OK |
| Android (AGP 9.3.3, Kotlin 2.2.10, Compose BOM 2026.02.01) | `app-movil/gradle/libs.versions.toml` | No se pudo escanear: no hay `gradle.lockfile` | HALLAZGO Baja (SEC-19, abierto) |

Los identificadores GHSA son los que dio osv-scanner 2.7.0 el 24/11; no se agregó ningún CVE que la herramienta no reportara.

## 11. Pendientes del Sprint 3 que siguen abiertos

| ID | Gravedad | Estado al 24/11 | Evidencia |
|---|---|---|---|
| SEC-06 | Media | Decisión aceptada (22/11) | `decisiones.md` |
| SEC-07, SEC-09, SEC-10, SEC-14, SEC-20 | — | Cerrados | Secciones 7, 8 y 9 |
| SEC-11 | Baja | Abierto | El JWT no se revoca |
| SEC-12 | Baja | Abierto | `AuthService.java:64` |
| SEC-13 | Baja | Abierto | `RecuperacionService.java:52`, `VerificacionService.java:43` |
| SEC-15 | Baja | Abierto, aplica al desplegar el admin | `backend-admin/config/settings.py` |
| SEC-19 | Informativa | Abierto | Sin `gradle.lockfile` |

## Orden sugerido

1. **S4-01** antes de la demo: sin esa tarea, una reserva olvidada aparta stock para siempre.
2. **S4-09** (integrar #139) y **S4-02**, junto con el PR #141 de "Mi cuenta".
3. **S4-08** y **S4-05** en el admin (Guillermo), **S4-07** y **S4-03** en la API (Alexander).
4. El resto, como bajas del Sprint 5.
