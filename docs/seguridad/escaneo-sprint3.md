# Escaneo de seguridad del Sprint 3

- **Fecha:** 2026-11-07
- **Commit de `main` escaneado:** `8ac1ffc` (Merge pull request #65)
- **Rama de correcciones:** `fix/escaneo-seguridad`
- **Alcance:** `database/`, `backend-admin/` (Django), `web-usuario/` (React), `backend-usuario/` (Spring Boot) y `app-movil/` (Kotlin).
  En `backend-usuario/` y `app-movil/` solo se revisó; las correcciones quedan para su responsable.
- **Secretos:** ningún valor de clave, token o contraseña aparece en este informe; se indica solo archivo, línea y tipo.

## Herramientas

| Herramienta | Versión | Uso |
|---|---|---|
| gitleaks | 8.30.1 | Secretos en el historial (`--log-opts="--all"`) y en los archivos actuales |
| osv-scanner | 2.7.0 | Dependencias con vulnerabilidades (`pom.xml`, `requirements.txt`, `package-lock.json`) |
| npm audit | npm 11.20.0 (Node v26.11.1) | Dependencias de `web-usuario/` (D8) |
| pip-audit | 2.10.1 | Dependencias de `backend-admin/` |
| semgrep | 1.184.0 (`--config auto`, 595 reglas) | Análisis estático de las cuatro aplicaciones |
| bandit | 1.9.4 | Análisis estático de Python en `backend-admin/` (sin `venv/` ni pruebas) |

Todas se instalaron solo en la Mac del responsable (gitleaks, osv-scanner y semgrep con brew; bandit y pip-audit
en `backend-admin/venv`). No se agregó ninguna al `requirements.txt`, `package.json` ni `pom.xml`.

## Resumen

| Gravedad | Hallazgos | Corregidos | Pendientes | Aceptados |
|---|---|---|---|---|
| Crítica | 0 | 0 | 0 | 0 |
| Alta | 2 | 1 | 1 | 0 |
| Media | 6 | 3 | 2 | 1 |
| Baja | 7 | 0 | 7 | 0 |
| Informativa | 5 | 0 | 2 | 3 |
| **Total** | **20** | **4** | **12** | **4** |

Sin secretos reales en el historial ni en los archivos versionados. Los falsos positivos de las herramientas no
se cuentan como hallazgos; se explican en cada fase.

## Tabla de hallazgos

| ID | Gravedad | Capa | Descripción | Archivo:línea | Responsable | Estado |
|---|---|---|---|---|---|---|
| SEC-01 | Alta | backend | Spring Boot 3.3.5 trae dependencias con vulnerabilidades conocidas (Tomcat 10.1.31, Spring Framework 6.1.14, Spring Security 6.3.4, pgjdbc 42.7.4, Jackson 2.17.2…). Ver Fase 3. | `backend-usuario/pom.xml:9` | Alexander | Pendiente |
| SEC-02 | Alta | web | D8: vite 8.3.2 con 6 avisos (`npm audit` la marca alta). Solo afecta al servidor de desarrollo, no al `dist/` que se publica. | `web-usuario/package-lock.json` | Guillermo | Corregido en `7072e46` |
| SEC-03 | Media | admin | `DEBUG` era `True` por defecto, `SECRET_KEY` tenía un valor inseguro por defecto que también valía en producción y `ALLOWED_HOSTS` estaba fijo en el código. | `backend-admin/config/settings.py:33` | Guillermo | Corregido en `01ba8cb` |
| SEC-04 | Media | admin | `defusedxml` no estaba en `requirements.txt`: sin él openpyxl no rechaza "XML bombs" en el Excel de DIGEMID. En la Mac solo estaba instalado porque `pip-audit` lo arrastró. | `backend-admin/requirements.txt:5` | Guillermo | Corregido en `9b72c81` |
| SEC-05 | Media | backend | Sin límite de intentos por IP o cuenta en `POST /auth/login`, `/auth/recuperar` y `/auth/restablecer`. La recuperación limita 5 intentos por código y 1 código por minuto, pero eso permite unos 7 200 intentos al día sobre un código de 6 dígitos. | `backend-usuario/.../service/AuthService.java:44`, `RecuperacionService.java:31` | Alexander | Pendiente |
| SEC-06 | Media | web | El JWT se guarda en `localStorage`: un XSS podría leerlo. Riesgo real bajo: no hay `dangerouslySetInnerHTML` ni `innerHTML`, React escapa todo, el token vence en 24 h y se borra al cerrar sesión o con un 401. | `web-usuario/src/services/sesion.js:26` | Guillermo | Aceptado: moverlo a una cookie `HttpOnly` exige cambiar la API (Alexander); se reevalúa antes de producción |
| SEC-07 | Media | móvil | `android:usesCleartextTraffic="true"` para toda la app. Sirve para `http://10.0.2.2` en desarrollo, pero en producción permitiría enviar el token sin cifrar. | `app-movil/app/src/main/AndroidManifest.xml:10` | Mijael | Pendiente |
| SEC-08 | Media | admin | Inyección de fórmulas en la exportación a Excel (por ejemplo `=HYPERLINK(...)` en un nombre comercial). Se detectó en la revisión de código del PR #63. | `backend-admin/reportes/exportar.py:36` | Guillermo | Corregido en `d45af3d` (PR #63) |
| SEC-09 | Baja | móvil | El token se guarda en DataStore en texto plano y `allowBackup="true"` sin reglas de exclusión: el token puede ir en las copias de seguridad de Android. | `app-movil/.../data/local/TokenManager.kt:28`, `AndroidManifest.xml:11` | Mijael | Pendiente |
| SEC-10 | Baja | backend | CORS solo permite `http://localhost:5173`, fijo en el código: en producción habrá que leerlo del entorno. No es un riesgo hoy. | `backend-usuario/.../security/SecurityConfig.java:64` | Alexander | Pendiente |
| SEC-11 | Baja | backend | El JWT no se puede revocar: al cambiar la contraseña o cerrar sesión, el token anterior sigue valiendo hasta 24 h (sí se invalida si la cuenta se elimina, H19). | `backend-usuario/src/main/resources/application.properties:20` | Alexander | Pendiente |
| SEC-12 | Baja | backend | El login responde más rápido cuando el correo no existe (no ejecuta BCrypt): permite adivinar qué correos están registrados midiendo tiempos. | `backend-usuario/.../service/AuthService.java:45` | Alexander | Pendiente |
| SEC-13 | Baja | backend | `JWT_SECRET` se usa también como clave HMAC de los tokens de verificación y recuperación. Si se filtra, compromete las dos cosas. | `backend-usuario/.../service/VerificacionService.java:43` | Alexander | Pendiente |
| SEC-14 | Baja | admin | El login del admin de Django no limita intentos. | `backend-admin/config/urls.py` | Guillermo | Pendiente (para producción: `django-axes` o límite en el proxy) |
| SEC-15 | Baja | admin | Faltan los ajustes de producción: cookies seguras, HSTS y redirección a HTTPS (`check --deploy` da W004, W008, W012 y W016). | `backend-admin/config/settings.py` | Guillermo | Pendiente (documentado en Recomendaciones) |
| SEC-16 | Informativa | bd | El linter de Supabase da 10 avisos INFO "RLS enabled no policy" en las tablas internas de Django (`auth_*`, `django_*`). | `database/rls_todas_las_tablas.sql` | Guillermo | Aceptado: no es un riesgo (ver Fase 5) |
| SEC-17 | Informativa | bd | `seed.sql` crea usuarios de prueba con una contraseña conocida y documentada. | `database/seed.sql:4` | Guillermo | Aceptado: solo para desarrollo; no cargar en producción |
| SEC-18 | Informativa | backend | En la búsqueda, `%` y `_` del texto del usuario funcionan como comodines de `LIKE`. No es inyección (va como parámetro); solo amplía resultados. | `backend-usuario/.../service/BusquedaService.java:70` | Alexander | Aceptado |
| SEC-19 | Informativa | móvil | Las dependencias de Gradle no se pudieron escanear: osv-scanner necesita un archivo de bloqueo (`gradle.lockfile`). | `app-movil/gradle/libs.versions.toml` | Mijael | Pendiente |
| SEC-20 | Informativa | admin | `requirements.txt` solo fija versiones mínimas (`>=`): dos instalaciones pueden tener versiones distintas. | `backend-admin/requirements.txt` | Guillermo | Pendiente |

## Fase 1: herramientas

Las cinco herramientas se instalaron sin problemas (versiones en la tabla de arriba).

## Fase 2: secretos

**Historial de git** (`gitleaks git --log-opts="--all"`, 228 commits): 2 coincidencias, las dos falsos positivos.

| Archivo:línea | Commit | Regla | Motivo del descarte |
|---|---|---|---|
| `docs/API.md:97` | `b97af979` | generic-api-key | Token de ejemplo truncado (`eyJ…` seguido de `...`): no es un JWT válido |
| `docs/API.md:71` | `e5a6b0da` | generic-api-key | Mismo ejemplo truncado, en otra versión del contrato |

**Sin secretos reales en el historial.** No hizo falta rotar ninguna clave.

**Archivos actuales** (`gitleaks dir`): 13 coincidencias.
- `docs/API.md:74`: el mismo ejemplo truncado (falso positivo).
- 12 en `.env`, `.env.bak-mailtrap`, `backend-usuario/.env` y `backend-usuario/.env.bak-mailtrap` (tipo generic-api-key: contraseña
  de BD, `JWT_SECRET`, clave de Supabase y API key de correo). Son los archivos locales con las claves reales: **ninguno está
  versionado** (`git ls-files` no los lista) y `.gitignore` los cubre (`.env` y `.env.*`).

**`.gitignore`:** se comprobó con `git check-ignore` que están ignorados `.env` en la raíz y en las cinco carpetas, `.env.*`,
`venv/`, `node_modules/`, `local.properties` (raíz y `app-movil/`), `*.jks` y `*.keystore`. `git ls-files` no devuelve ningún `.env`,
llave ni `local.properties`; solo los dos `.env.example`.

**`.env.example`:** el de la raíz y `web-usuario/.env.example` solo tienen valores de ejemplo; todas las claves secretas
(`DB_PASSWORD`, `JWT_SECRET`, `SUPABASE_SECRET_KEY`, `MAIL_PASSWORD`, `RUC_API_TOKEN` y la nueva `DJANGO_SECRET_KEY`) están vacías.

**Variables `VITE_`:** la web solo usa `VITE_API_URL` (URL pública de la API) y `VITE_USE_MOCK` (`true`/`false`), en
`web-usuario/src/config.js:2-3`. Ninguna es secreta, así que no importa que terminen en el JavaScript público.

## Fase 3: dependencias con vulnerabilidades conocidas

### `web-usuario/` (D8)

`npm audit` y osv-scanner coinciden: **vite 8.3.2**, gravedad alta, 6 avisos:
GHSA-698v-4r3x-3vcv, GHSA-r825-w8mm-5pxw y GHSA-587h-7wmj-h5pm (en `launch-editor`, que vite incluye), GHSA-9jrq-w75r-8gcw,
GHSA-rq7h-c2jc-7f22 y GHSA-vfpm-58rq-9qcg (lectura de archivos fuera de `server.fs.allow`).

- **¿Se usa la parte vulnerable?** Solo en el servidor de desarrollo (`npm run dev`) y sobre todo si se expone a la red
  (`--host`); `vite.config.js` no lo hace. El `dist/` que se publica no contiene vite. Riesgo real: bajo.
- **Corrección:** `npm audit fix` sin `--force` subió vite a **8.3.6** (mismo major, solo cambia `package-lock.json`).
  Después: `npm audit` → 0 vulnerabilidades, `npm run lint` (oxlint) sin errores, `npm run build` correcto. Commit `7072e46`.

### `backend-admin/`

`pip-audit` sobre `requirements.txt` y sobre el venv completo: **sin vulnerabilidades conocidas**. osv-scanner tampoco
encontró nada en `requirements.txt`.

### `backend-usuario/` (SEC-01, responsable Alexander)

osv-scanner encontró 98 avisos en 21 paquetes, todos por la versión de Spring Boot (3.3.5) y lo que trae.

| Paquete | Versión | Avisos | CVSS máx. | Se corrige en | ¿Se usa la parte vulnerable? |
|---|---|---|---|---|---|
| `tomcat-embed-core` | 10.1.31 | 36 | 9.8 | 10.1.58 | En parte. Es el servidor HTTP. Los 9.x más graves (autenticación FORM/Digest, HTTP/2, PUT parcial) no aplican: la API usa JWT y no activa HTTP/2 ni escritura en el servlet por defecto. Quedan avisos de DoS y de cabeceras que sí aplican. |
| `spring-webmvc` | 6.1.14 | 13 | 9.8 | 6.2.19 (el 9.8 solo en 7.0.9) | En parte. El 9.8 (GHSA-pc63-qcmh-9cmg) es de `XsltView`, que no se usa. |
| `spring-security-web` | 6.3.4 | 3 | 9.1 | 6.5.11 | Sí: GHSA-mf92-479x-3373 (cabeceras de seguridad que no se escriben en algunos casos). |
| `spring-security-crypto` | 6.3.4 | 1 | 7.4 | 6.3.8 | Sí, poco: BCrypt con contraseñas de más de 72 bytes. La API limita a 72 *caracteres*; con tildes o ñ se puede pasar de 72 bytes. |
| `jackson-core` / `jackson-databind` | 2.17.2 | 4 / 12 | 8.7 / 8.1 | 2.18.11 | Sí: lee todo el JSON de las peticiones. |
| `postgresql` (pgjdbc) | 42.7.4 | 3 | 8.2 | 42.7.12 | Poco: dos avisos son de `channelBinding`, que no se configura; el tercero es un DoS que requiere un servidor de BD malicioso. |
| `spring-core` / `spring-expression` / `spring-web` / `spring-context` | 6.1.14 | 2 / 3 / 1 / 1 | 7.5 | 6.2.19 | En parte. |
| `spring-boot` / `spring-boot-autoconfigure` | 3.3.5 | 2 / 1 | 7.3 | 3.5.15 | En parte (Actuator no se usa). |
| `spring-data-commons` | 3.3.5 | 3 | 7.5 | 3.5.12 | En parte. |
| `spring-security-core` | 6.3.4 | 2 | 6.3 | 6.5.10 | En parte. |
| `logback-core` | 1.5.11 | 6 | 5.9 | 1.5.34 | Poco (configuración por defecto). |
| `commons-lang3` / `log4j-api` | 3.14.0 / 2.23.1 | 1 / 1 | 6.5 | 3.18.0 / 2.25.5 | Llegan con Apache POI (carga masiva). |
| `json-smart`, `assertj-core`, `xmlunit-core` | — | 3 | 8.2 | — | No: solo en pruebas (`spring-boot-starter-test`). |

**Recomendación para Alexander:** subir `spring-boot-starter-parent` de 3.3.5 a la última 3.5.x (no cambia de versión mayor) y
volver a pasar osv-scanner. Eso debería resolver casi todo; lo que solo se corrige en Spring 7 / Boot 4 (`XsltView`) no se usa.
Es un cambio de versión menor de Spring Boot: necesita las pruebas completas de la API antes de integrarlo.

### `app-movil/` (SEC-19)

osv-scanner no escanea Gradle sin archivo de bloqueo. Recomendación para Mijael: activar `dependencyLocking` en Gradle
o el escaneo de dependencias de GitHub (Dependabot) para el módulo Android.

## Fase 4: análisis estático

**semgrep** (`--config auto`, 258 archivos versionados de las cuatro carpetas): 3 resultados, los tres falsos positivos.

| Regla | Archivo:línea | Motivo del descarte |
|---|---|---|
| `exported_activity` | `app-movil/app/src/main/AndroidManifest.xml:21` | Es `MainActivity` con el `intent-filter` del lanzador: tiene que estar exportada para abrir la app. |
| `dynamic-urllib-use-detected` | `backend-admin/boticas/storage.py:49` | La URL se arma con `SUPABASE_URL` del `.env` y una ruta fija; el usuario no controla el esquema ni el host. |
| `extends-custom-expression` | `backend-admin/catalogo/management/commands/poblar_principios_activos.py:21` | `Initcap(Func)` solo fija `function = 'INITCAP'`; no hay plantilla SQL con datos del usuario. |

semgrep no pudo leer completas 6 plantillas HTML del admin (sintaxis `{% %}` de Django); se revisaron a mano: no usan
`|safe` ni `autoescape off`.

**bandit** (`backend-admin/`, 2 249 líneas, sin `venv/` ni pruebas): 1 resultado, falso positivo.
- B310 (`urlopen`, gravedad media) en `backend-admin/boticas/storage.py:49`: el mismo caso que semgrep.

## Fase 5: revisión manual

### Spring Boot (`backend-usuario/`, solo informe, responsable Alexander)

- **Rutas públicas** (`SecurityConfig.java`): `/auth/**`, `GET /boticas/validar-ruc/**`, `GET /productos/{id}` y
  `GET /productos/{id}/alternativas`. Las dos últimas son públicas según el contrato (`docs/API.md`, H28 y H36) y solo
  muestran productos visibles para el comprador. `validar-ruc` sin sesión solo hace la validación local, para cuidar la cuota
  de SUNAT. `/boticas/**` y el resto de `/productos/**` exigen el rol `DUENO_BOTICA`; todo lo demás exige token. **Correcto.**
- **JWT** (`JwtService.java`): el secreto sale de `JWT_SECRET` (sin valor por defecto) y la API no arranca si tiene menos de
  32 bytes. El algoritmo es HMAC-SHA (jjwt lo elige según la clave y `verifyWith` rechaza `alg: none` y tokens con otra
  firma). Expiración: 24 h. El filtro también comprueba que la cuenta siga activa. **Correcto** (ver SEC-11 y SEC-13).
- **Contraseñas:** BCrypt (`BCryptPasswordEncoder`), de 8 a 72 caracteres. **Correcto.**
- **Tokens de verificación y recuperación:** se guardan como HMAC-SHA256, nunca en claro. Verificación: 32 bytes aleatorios,
  vence en 48 h y sirve una sola vez. Recuperación: código de 6 dígitos con `SecureRandom`, vence en 15 min, una sola vez,
  máximo 5 intentos y comparación en tiempo constante; no revela si el correo existe. **Correcto** (ver SEC-05).
- **Registro:** el rol solo puede ser `COMPRADOR` o `DUENO_BOTICA` (`@Pattern`); no hay forma de registrarse como administrador.
- **Control de acceso por id:** los productos se buscan con `findByIdAndBoticaIdAndActivoTrue(id, boticaDelToken)`, la botica
  con `findByUsuarioId(usuarioDelToken)`, y favoritos y alertas filtran por `usuario_id = :usuario` en cada consulta
  (incluidos marcar como leída y eliminar). **Un dueño no puede ver ni cambiar productos o boticas de otro.**
- **SQL:** todo usa JPA o `NamedParameterJdbcTemplate` con parámetros. Lo único que se concatena son constantes o valores de
  listas cerradas (`columna` y `direccion` en `BusquedaService`, `FILTRO_VISIBLE`, `MAX_ALERTAS`). **Sin inyección SQL.**
- **Subida de licencias:** máximo 5 MB (también `max-file-size=5MB`), tipo detectado por los bytes iniciales (PDF, JPG o PNG)
  y nombre de archivo generado por el servidor (`botica-{id}/licencia.{ext}`): nunca se usa el nombre del usuario. **Correcto.**
- **Carga masiva:** 5 MB y 1 000 filas como máximo, tipo detectado por el contenido. El `.xlsx` se lee con Apache POI 5.5.1,
  que trae protección contra "zip bombs" por defecto. **Correcto.**
- **CORS:** solo `http://localhost:5173` (ver SEC-10). **Límite de intentos:** no hay (ver SEC-05).
- **Mensajes de error:** `GlobalExceptionHandler` responde "Ocurrió un error inesperado en el servidor" ante cualquier excepción
  no prevista y deja el detalle solo en el log; los errores de integridad tampoco devuelven SQL. **Correcto.**

### Django (`backend-admin/`)

- **`DEBUG`, `SECRET_KEY`, `ALLOWED_HOSTS`:** antes `DEBUG` era `True` por defecto y había una `SECRET_KEY` fija que también
  se usaba fuera de desarrollo. **Corregido (SEC-03, `01ba8cb`):** `DEBUG` es `False` salvo `DJANGO_DEBUG=True`; sin
  `DJANGO_SECRET_KEY` Django no arranca (salvo con `DEBUG` o en las pruebas); `ALLOWED_HOSTS` sale de `DJANGO_ALLOWED_HOSTS`.
  Con 4 pruebas nuevas en `config/tests.py`.
- **Ajustes de producción:** `manage.py check --deploy` avisa que faltan HSTS (W004), redirección a HTTPS (W008) y cookies de
  sesión y CSRF seguras (W012, W016). En desarrollo local por HTTP no aplican; quedan en Recomendaciones (SEC-15).
- **Importación DIGEMID:** máximo 5 MB (`catalogo/forms.py:151`), se lee con `load_workbook(read_only=True, data_only=True)`.
  `defusedxml` no estaba en `requirements.txt`. **Corregido (SEC-04, `9b72c81`)**, con una prueba que comprueba que openpyxl lo usa.
- **Correos de revisión** (`boticas/notificaciones.py`): se envían en **texto plano** (`send_mail` sin `html_message`), así que
  el motivo de rechazo no se interpreta como HTML y no hace falta escaparlo. El asunto es fijo.
- **HTML del admin:** todo pasa por `format_html` (escapa los valores); no hay `mark_safe`, `|safe` ni `autoescape off`.
- **Licencias en Storage:** el bucket es privado y el admin las abre con una **URL firmada de 5 minutos**
  (`boticas/storage.py`), creada en el servidor con la clave secreta, que nunca llega al navegador. **Correcto.**
- **Exportación a Excel (PR #63):** confirmado. `reportes/exportar.py:36` guarda como texto (`data_type = 's'`) toda celda que
  empieza con `=`, y `reportes/tests/test_exportar.py` lo prueba con `=1+1` y `=HYPERLINK(...)`. En `.xlsx` los textos que
  empiezan con `+`, `-` o `@` ya se guardan como texto y Excel no los evalúa (SEC-08).

### Web React (`web-usuario/`)

- **`dangerouslySetInnerHTML` / `innerHTML` / `eval`:** ningún uso en `src/`.
- **JWT:** se guarda en `localStorage` (`services/sesion.js`). Se borra al cerrar sesión (`borrarSesion`) y con cualquier 401
  de la API (`services/index.js:26`, salvo en el login, donde 401 significa credenciales incorrectas). Riesgo de XSS anotado
  en SEC-06.
- **Rutas:** todas las de `/panel` están dentro de `RutaProtegida`; sin sesión se redirige a `/login`. Un comprador no puede
  guardar sesión en la web (H18).

### App móvil (`app-movil/`, solo informe, responsable Mijael)

- **Token:** en DataStore (`settings`), en texto plano, y `allowBackup="true"` sin reglas de exclusión (SEC-09). Recomendación:
  excluir el DataStore del respaldo (`data_extraction_rules.xml` / `backup_rules.xml`) o cifrar el token con Android Keystore.
- **Tráfico sin cifrar:** `usesCleartextTraffic="true"` para todo (SEC-07). Bien para `10.0.2.2` en desarrollo; en producción usar
  HTTPS y un `network_security_config.xml` que permita texto plano solo para `10.0.2.2` en la variante debug.
- **Logs de OkHttp:** no hay `HttpLoggingInterceptor`; el token no se escribe en los logs. **Correcto.**
- **Claves y URLs:** `BASE_URL` sale de `local.properties` (ignorado) con `http://10.0.2.2:8080/api/` por defecto; no hay claves
  en el código y gitleaks no encontró nada en `app-movil/`. **Correcto.**

### Base de datos (`database/`)

El linter de Supabase da 10 avisos INFO "RLS enabled no policy" en las tablas internas de Django (`auth_*`, `django_*`).
No son un riesgo: con RLS activado y sin políticas, las API públicas de Supabase (`anon` y `authenticated`) no pueden
leerlas. Las tablas de la app tienen RLS con la política `app_acceso_total` solo para el rol `boticompara_app` (SEC-16).
Los scripts no contienen contraseñas de roles; `seed.sql` solo trae hashes BCrypt de los usuarios de prueba (SEC-17).

## Fase 6: correcciones aplicadas

| Commit | Hallazgo | Cambio | Pruebas |
|---|---|---|---|
| `7072e46` | SEC-02 (D8) | `npm audit fix`: vite 8.3.2 → 8.3.6 (solo `package-lock.json`) | `npm audit` 0, oxlint y `npm run build` correctos |
| `01ba8cb` | SEC-03 | `settings.py`: `DEBUG` falso por defecto, `SECRET_KEY` obligatoria fuera de desarrollo, `ALLOWED_HOSTS` del entorno; `.env.example` y README actualizados | 4 pruebas nuevas (`config/tests.py`); 84 en total, todas pasan |
| `9b72c81` | SEC-04 | `defusedxml>=0.7.1` en `requirements.txt` | 1 prueba nueva (`catalogo/tests/test_seguridad_xml.py`); 85 en total, todas pasan |

**Importante para el equipo:** después de `01ba8cb`, quien ejecute Django en local debe tener `DJANGO_DEBUG=True` en el `.env`
de la raíz (ya está en `.env.example`). Sin esa línea ni `DJANGO_SECRET_KEY`, Django no arranca y lo indica en el mensaje.

No se corrigieron ahora (fuera de mis capas, o no son rápidos y de bajo riesgo): SEC-01, SEC-05, SEC-07, SEC-09 a SEC-13 y SEC-19
(Alexander y Mijael); SEC-06 (requiere cambiar la API), SEC-14 y SEC-15 (son de producción), SEC-20.

## Recomendaciones

### Para arreglar ahora (antes de cerrar el Sprint 3)

1. **Alexander:** subir Spring Boot a la última 3.5.x y volver a escanear (SEC-01).
2. **Alexander:** limitar intentos por IP y por correo en login, recuperación y restablecimiento, por ejemplo con Bucket4j o un
   contador en la base (SEC-05).
3. **Mijael:** limitar el texto plano a `10.0.2.2` con `network_security_config.xml` y excluir el token del respaldo (SEC-07, SEC-09).

### Para producción

- **Django:** con `DJANGO_DEBUG=False`, activar `SECURE_SSL_REDIRECT`, `SESSION_COOKIE_SECURE`, `CSRF_COOKIE_SECURE`,
  `SECURE_HSTS_SECONDS` (empezar con poco y subir), `CSRF_TRUSTED_ORIGINS` con el dominio del admin y `SECURE_PROXY_SSL_HEADER`
  si va detrás de un proxy. Verificar con `python manage.py check --deploy` (SEC-15). Limitar intentos del login del admin (SEC-14).
- **Spring Boot:** CORS desde una variable de entorno con el dominio real de la web (SEC-10); `APP_BASE_URL` con HTTPS; considerar
  una clave HMAC separada de `JWT_SECRET` para los tokens de correo (SEC-13) y una forma de revocar JWT, por ejemplo con una
  versión de sesión en el usuario (SEC-11).
- **Web:** servir solo por HTTPS con cabeceras `Content-Security-Policy`, `X-Content-Type-Options` y `Referrer-Policy`; evaluar
  pasar el JWT a una cookie `HttpOnly; Secure; SameSite` (SEC-06).
- **Móvil:** `BASE_URL` con HTTPS en la variante release y token cifrado con Android Keystore.
- **Base de datos:** no cargar `seed.sql` en producción (SEC-17) y mantener RLS en todas las tablas nuevas.
- **Dependencias:** escanear en cada PR (osv-scanner o Dependabot) y fijar versiones exactas en `requirements.txt` con un
  archivo de bloqueo (SEC-20); activar el bloqueo de dependencias de Gradle (SEC-19).
- **Claves:** rotar `JWT_SECRET`, la clave secreta de Supabase y la API key de correo antes de salir a producción, y no reutilizar
  las de desarrollo.
