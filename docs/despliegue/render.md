# Backend en Render (plan gratis)

Guía para desplegar `backend-usuario/` en Render con Docker. Cómo funciona la imagen: [`docker.md`](docker.md).
La web va en Vercel (guía `vercel.md`, PR #123).

**Orden:** 1) backend en Render, 2) web en Vercel con la URL de Render, 3) `CORS_ORIGINS` en Render con la URL de Vercel.

Lo de Render se revisó en su documentación el 22/11/2026. Lo marcado **(por comprobar)** solo se puede confirmar con el servicio desplegado.

---

## 1. Antes de desplegar

- [ ] Este PR en `main`: sin el cambio del `Dockerfile`, la API **no arranca** con el Secret File de Firebase (sección 5).
- [ ] El #125 en `main`, para que el log no muestre una contraseña generada.
- [ ] Cambiar la contraseña de las cuentas de prueba: el repositorio es público y está escrita en `docs/API.md`.
- [ ] Tener a la mano los valores del `.env`. **No se copian a este repositorio ni a un chat.**

### Región: Virginia

La base está en AWS **`sa-east-1` (São Paulo)**. Render no tiene región en Sudamérica: ofrece Oregon, Ohio, Virginia, Frankfurt y
Singapur. **La más cercana es Virginia.** No se puede cambiar después: habría que crear otro servicio.

Cada consulta viaja de Virginia a São Paulo y vuelve. Las pantallas que hacen varias consultas se sentirán más lentas que en local.

---

## 2. Crear el Web Service

### Opción A: a mano

1. **New → Web Service** y conectar `willardguillermo/boticompara` desde GitHub.
2. Completar:

| Campo | Valor |
|---|---|
| Name | El que quieras; define la URL `https://NOMBRE.onrender.com` |
| Language | **Docker** |
| Branch | `main` |
| Region | **Virginia** |
| Root Directory | `backend-usuario` |
| Dockerfile Path | `./Dockerfile` (relativo a Root Directory) |
| Instance Type / Compute plan | **Free** |
| Health Check Path | **`/api/salud`** (con `/api`) |
| Auto-Deploy | Off: se despliega a mano y un merge no reinicia la API en plena demo |

3. Cargar las variables de la sección 3 y pulsar **Deploy**.

### Opción B: con el Blueprint

`render.yaml` (raíz del repo) ya trae todo lo de la tabla; el plan gratis lo admite. **New → Blueprint**, elegir el repo y llenar las
variables que pide. Van con `sync: false`, o sea sin valores en el repo, y Render solo las pregunta **la primera vez**; después se
cambian en **Environment**. La única con valor fijo es `SERVER_TOMCAT_REMOTEIP_REMOTEIPHEADER` (no es secreta, sección 7).
No incluye `FIREBASE_PROJECT_ID`: se agrega a mano (sección 5).

### El health check

`GET /api/salud` es público, responde `{"estado":"OK"}` y no consulta la base (`docs/API.md`, sección 13). Render lo llama cada pocos
segundos y espera un 2xx o 3xx en menos de 5 s; con 60 s de fallos seguidos reinicia el servicio. Al desplegar da hasta 15 minutos.

---

## 3. Variables de entorno

Solo nombres: los valores están en tu `.env`. `PORT` **no se pone**, la da Render.

| Variable | Para qué sirve | Si falta |
|---|---|---|
| `DB_HOST`, `DB_USER`, `DB_PASSWORD` | Conexión a Supabase (pooler) | **No arranca** |
| `DB_PORT`, `DB_NAME`, `DB_SSLMODE` | Puerto, base y SSL de la conexión | Usa 5432, `postgres` y `require` |
| `JWT_SECRET` | Firma los JWT; mínimo 32 caracteres. Usa una **distinta** a la de local | **No arranca** |
| `CORS_ORIGINS` | Orígenes de la web que pueden llamar a la API (sección 6) | Solo `http://localhost:5173` |
| `SERVER_TOMCAT_REMOTEIP_REMOTEIPHEADER` | **`CF-Connecting-IP`**: la IP real del cliente para los límites por IP (SEC-05). No es secreta; ya viene en `render.yaml` | Se usa la IP de Cloudflare, que cambia en cada petición: los límites de login, registro y recuperar **no bloquean** (sección 7) |
| `APP_BASE_URL` | URL pública de la API para los enlaces de los correos: la de Render terminada en `/api` | Los enlaces apuntan a `localhost` |
| `SUPABASE_URL`, `SUPABASE_SECRET_KEY` | Licencias en Storage privado (H4) | Subir la licencia responde 503 |
| `SUPABASE_BUCKET_LICENCIAS` | Nombre del bucket | `licencias` |
| `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Correos por SMTP (Resend) | Sin `MAIL_HOST` no se envían |
| `MAIL_PORT` | Puerto SMTP. **En Render gratis no sirve el 587** (ver abajo) | 2525 |
| `ALERTA_STOCK_HABILITADA` | Aviso diario de stock bajo (H12) | `true` |
| `ALERTA_STOCK_CRON` | Hora del aviso; solo para pruebas | 8:00 de Lima |
| `RUC_API_URL`, `RUC_API_TOKEN` | Consulta del RUC en SUNAT (H2) | El RUC solo se valida localmente |
| `GEMINI_API_KEY` | Asistente (H68) | `POST /chat` responde 503 |
| `GEMINI_MODEL` | Modelo de Gemini | El de `application.properties` |
| `JAVA_TOOL_OPTIONS` | Arranque más rápido (opcional, sección 8) | La del `Dockerfile` |
| `CHAT_LIMITE_POR_MINUTO`, `CHAT_LIMITE_POR_DIA`, `CHAT_LIMITE_GLOBAL_POR_DIA` | Límites del chat (opcionales) | 3, 15 y 150 |
| `FIREBASE_CREDENTIALS_PATH`, `FIREBASE_PROJECT_ID` | Login con Google (H67), sección 5 | `POST /auth/google` responde 503 |

Las `DJANGO_*` del `.env.example` son del panel de administración: aquí no van.

- **No dejes una variable creada y vacía:** `DB_PORT` vacía, por ejemplo, reemplaza al 5432 y la API no arranca. Si no la usas, no la crees.
- **Si tu Docker local sigue encendido** con la misma base, los dos envían el aviso de stock: pon `ALERTA_STOCK_HABILITADA=false` en uno.

### Los correos en el plan gratis

Render bloquea en el plan gratis la salida por los puertos **25, 465 y 587**. Resend también acepta STARTTLS en el **2587**, que no está
en esa lista: pon **`MAIL_PORT=2587`**.

| Dónde | Resultado |
|---|---|
| En local (Docker, 22/11/2026) | **Probado.** `POST /auth/recuperar` con `MAIL_PORT=2587`: conexión establecida al 2587, respuesta en 3,4 s y ningún error en el log: Resend aceptó el correo |
| En Render | **Por comprobar** el lunes: pide "olvidé mi contraseña" y revisa que el correo llegue y que **Logs** no muestre `No se pudo enviar el correo` |

Si no sale desde Render, la API sigue funcionando, pero registrarse y recuperar la contraseña pueden tardar hasta 10 s por el tiempo de espera.

---

## 4. Comprobar que quedó arriba

```bash
API=https://NOMBRE.onrender.com/api
curl -i $API/salud                                              # 200 {"estado":"OK"}
curl -s -o /dev/null -w '%{http_code}\n' $API/productos/7/alternativas   # 200: la base responde
curl -s -o /dev/null -w '%{http_code}\n' $API/usuarios/me                # 401: pide token
```

| Si ves esto en **Logs** | Revisa |
|---|---|
| `JWT_SECRET debe tener al menos 32 caracteres` | `JWT_SECRET` |
| Error de conexión o `FATAL: ... authentication` | `DB_*` |
| `/etc/secrets/firebase.json (Permission denied)` | El `Dockerfile` de este PR (grupo 1000) |
| `/etc/secrets/firebase.json (No such file or directory)` | El Secret File (sección 5) |
| `Schema-validation: missing table` o `missing column` | Falta aplicar un script de `database/` |

---

## 5. Firebase (login con Google)

1. Servicio → **Environment → Secret Files → Add Secret File**. Filename: `firebase.json`; en Contents, el JSON de la cuenta de servicio.
2. Después, en las variables:

```
FIREBASE_CREDENTIALS_PATH=/etc/secrets/firebase.json
FIREBASE_PROJECT_ID=   ← el project_id que está dentro del JSON
```

**En ese orden:** con las dos variables puestas y sin el archivo, la API no arranca.

- Render deja el archivo en `/etc/secrets/` y solo lo puede leer el **grupo 1000**. Por eso el `Dockerfile` agrega el usuario a ese grupo.
- Render también copia los Secret Files al contexto del build; el `.dockerignore` (`*.json`) evita que entren a la imagen.
- Comprobación: `POST /auth/google` con un token inventado debe dar **401**. Si da 503, Firebase no quedó configurado.

---

## 6. CORS con la URL de Vercel

```
CORS_ORIGINS=https://TU-WEB.vercel.app
```

Con `https://`, sin `/` al final y sin ruta. Varios orígenes, separados por coma. Al guardar, Render reinicia el servicio.

```bash
curl -si -X OPTIONS $API/auth/login -H 'Origin: https://TU-WEB.vercel.app' -H 'Access-Control-Request-Method: POST' | grep -i access-control-allow-origin
```

Debe mostrar tu URL; con otro `Origin`, nada.

---

## 7. ¿El límite de login ve la IP real?

> **Resultado en Render (23/11/2026):** sin ajuste, la API veía la IP de Cloudflare y el límite no bloqueaba. Se corrigió con
> `SERVER_TOMCAT_REMOTEIP_REMOTEIPHEADER=CF-Connecting-IP`, sin cambiar código. Detalle en
> [Lo que se probó en Render](#lo-que-se-probó-en-render-23112026).

El límite (SEC-05) cuenta 5 fallos por **correo e IP**. Detrás de un proxy la IP sale de `X-Forwarded-For`, que cualquiera puede
inventar. Con `server.forward-headers-strategy=native`, Tomcat lee esa lista **de derecha a izquierda** y usa la primera IP que no sea
de un proxy interno (redes privadas: 10.x, 172.16–31.x, 192.168.x, 127.x, 100.64–127.x, IPv6 local). Lo inventado queda a la izquierda.

El tráfico a Render pasa por **Cloudflare** y su balanceador, y Render no documenta cuántas IP quedan en la lista. Por eso hay que probarlo.

### Prueba 1: inventar `X-Forwarded-For` no debe evitar el bloqueo

Correo inexistente (cuenta igual y no bloquea a nadie). Usa uno nuevo en cada intento de la prueba.

```bash
CORREO=limite1@prueba.invalid
for i in 1 2 3 4 5 6; do
  curl -4 -s -o /dev/null -w '%{http_code} ' $API/auth/login -H 'Content-Type: application/json' \
    -H "X-Forwarded-For: 203.0.113.$i" -d "{\"correo\":\"$CORREO\",\"password\":\"mala\"}"
done; echo
```

**Esperado:** `401 401 401 401 401 429`.

### Prueba 2: otra persona no debe quedar bloqueada

Enseguida, con **el mismo correo**, desde otra red (por ejemplo la laptop conectada a los datos del celular):

```bash
curl -4 -s -o /dev/null -w '%{http_code}\n' $API/auth/login -H 'Content-Type: application/json' \
  -d '{"correo":"limite1@prueba.invalid","password":"mala"}'
```

**Esperado:** `401`. Si da `429`, las dos redes comparten IP para la API. Si la prueba 1 no bloqueó, esta no dice nada.

### Cómo leer el resultado

| Prueba 1 | Prueba 2 | Qué pasa | Qué ajustar |
|---|---|---|---|
| 6.º = 429 | 401 | La API ve la IP real | Nada |
| 6.º = 429 | 429 | Todos comparten la IP de un proxy: o el de Render no está entre los internos (Tomcat ignora la lista), o se toma una IP fija de Cloudflare | Mirar la IP (abajo): caso 1 o 2 |
| Los 6 = 401 | — | La IP cambia en cada intento: es la de Cloudflare, que varía, o la inventada | Mirar la IP: caso 2 o 3 |

### Mirar qué IP llega

Agrega estas variables un momento, repite la prueba 1 y mira **Logs**. **Bórralas al terminar:** escriben las IP de los usuarios.
El 23/11 se usaron solo para el diagnóstico y se quitaron después.

```
SERVER_TOMCAT_ACCESSLOG_ENABLED=true
SERVER_TOMCAT_ACCESSLOG_DIRECTORY=/dev
SERVER_TOMCAT_ACCESSLOG_PREFIX=std
SERVER_TOMCAT_ACCESSLOG_SUFFIX=out
SERVER_TOMCAT_ACCESSLOG_ROTATE=false
SERVER_TOMCAT_ACCESSLOG_BUFFERED=false
SERVER_TOMCAT_ACCESSLOG_REQUESTATTRIBUTESENABLED=true
SERVER_TOMCAT_ACCESSLOG_PATTERN=ACCESO ip=%a xff=%{X-Forwarded-For}i cf=%{CF-Connecting-IP}i
```

Compara `ip` con tu IP pública (`curl -4 ifconfig.me`).

| Caso | `ip` es… | Propiedad | Variable en Render |
|---|---|---|---|
| 1 | La del balanceador de Render, y no es una red privada de la lista de arriba | `server.tomcat.remoteip.internal-proxies` | `SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` = expresión regular con **solo** esas IP, además de las que ya trae por defecto (al ponerla, reemplaza la lista completa) |
| 2 | Una de Cloudflare (por ejemplo `172.64.x`–`172.71.x`, `162.158.x`, `104.16.x`–`104.31.x`) | `server.tomcat.remoteip.remote-ip-header` | `SERVER_TOMCAT_REMOTEIP_REMOTEIPHEADER=CF-Connecting-IP`, **solo si** `cf=` muestra tu IP real y sigue mostrándola al enviar un `CF-Connecting-IP` inventado. Si no, `SERVER_TOMCAT_REMOTEIP_TRUSTEDPROXIES` con los [rangos de Cloudflare](https://www.cloudflare.com/ips/) |
| 3 | La inventada | `server.tomcat.remoteip.internal-proxies` demasiado amplia | Quitar la variable |

> **Nunca** pongas `.*` en `INTERNALPROXIES`: hace que se use la IP inventada y el límite deja de servir.

Después de cualquier ajuste, repite las dos pruebas con otro correo. No hace falta cambiar código.

### Lo que se probó en Render (23/11/2026)

Con el servicio desplegado. Las IP de las personas no se anotan aquí.

| Momento | Prueba 1 | IP que usó la API (access log) |
|---|---|---|
| Antes: sin ajuste | **`401 401 401 401 401 401`: no bloquea** | Una de Cloudflare (`104.22.x`), distinta en cada petición |
| Después: `SERVER_TOMCAT_REMOTEIP_REMOTEIPHEADER=CF-Connecting-IP` | **`401 401 401 401 401 429`** | La real del cliente: `ip` = `cf` |

- **Qué pasaba:** `X-Forwarded-For` llegaba como `cliente, Cloudflare (104.22.x), balanceador de Render (10.x)`. Tomcat descartaba
  el `10.x` por ser red privada y se quedaba con la de Cloudflare, que no está entre los proxies internos. Era el **caso 2** de la tabla de arriba.
- **El arreglo:** con `remote-ip-header=CF-Connecting-IP`, Tomcat toma la IP de la cabecera que pone Cloudflare con el cliente que se
  conectó. El `X-Forwarded-For` inventado de la prueba ya no influye.
- Registro y recuperar usan la misma IP, así que el arreglo también les sirve; solo se probó el login. La variable quedó en
  `render.yaml` con valor fijo.
- **Por comprobar:** la prueba 2 (otra red no queda bloqueada) y que un `CF-Connecting-IP` inventado no cambie `cf=`.
- El access log de la sección anterior se activó solo para este diagnóstico y luego se quitó.

### Lo que se probó en local (22/11/2026)

La imagen de este PR contra un PostgreSQL desechable, simulando cada cadena. **No reemplaza la prueba en Render.**

| Cadena o ajuste | Prueba 1 | Otro cliente | IP que usó la API |
|---|---|---|---|
| `inventada, real` | 429 al 6.º | 401 | La real |
| `inventada, real, Cloudflare` (cambia) | **6 × 401, no bloquea** | — | La de Cloudflare |
| `inventada, real, Cloudflare` (fija) | — | **429: bloquea a otros** | La de Cloudflare |
| Igual, con `REMOTEIPHEADER=CF-Connecting-IP` | 429 al 6.º | 401 | La real |
| Igual, con `TRUSTEDPROXIES` = rango de Cloudflare | 429 al 6.º | 401 | La real |
| Proxy fuera de `INTERNALPROXIES` | 429 al 6.º | **429: bloquea a otros** | La del proxy |
| `INTERNALPROXIES=.*` | **6 × 401, no bloquea** | — | La inventada |

---

## 8. El plan gratis se duerme: despertarlo antes de la demo

- Tras **15 minutos** sin peticiones, Render apaga el servicio. La siguiente lo despierta.
- Render dice que tarda cerca de un minuto, pero el plan gratis da **0.1 CPU** y Spring Boot pesa: en local, con 0.1 CPU y 512 MB, el
  arranque tardó **4 min 35 s** (dos mediciones: 277 y 274 s) y usó unos 320 MB. **Mide el tiempo real el lunes.**
- **Recomendado:** agregar `-XX:TieredStopAtLevel=1` bajó ese arranque a **1 min 47 s** (una medición) y 271 MB; a cambio, Java optimiza
  menos el código a la larga, que para la demo no importa. Va en la variable `JAVA_TOOL_OPTIONS`, que reemplaza **completa** a la del `Dockerfile`:

```
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -Xss512k -XX:+ExitOnOutOfMemoryError -XX:TieredStopAtLevel=1
```

- Mientras despierta, Render muestra una página de carga: la web y la app pueden ver errores de conexión o de CORS. No es una falla del código.
- Al dormirse se pierde lo que está en memoria: los bloqueos del límite de login y los contadores del chat.

Los pasos para que esto no se note están en la sección 9.

---

## 9. El día de la demo

### Un monitor que no lo deje dormir

Un servicio gratis llama a `https://NOMBRE.onrender.com/api/salud` **cada 10 minutos**: como Render espera 15, nunca se duerme.

| | cron-job.org (**recomendado**) | UptimeRobot |
|---|---|---|
| Método | GET | HEAD por defecto; elegir GET puede requerir el plan de pago. `GET /salud` es lo único público: un HEAD recibe **401** y el monitor lo marca caído (igual mantiene despierto el servicio) |
| Intervalo gratis | Hasta cada minuto: elegir **10 minutos** | Desde 5 minutos: elegir **10 minutos** |
| Al despertar en frío | Corta a los 30 s y anota un fallo; no es grave, pero tras varios fallos seguidos puede desactivar la tarea | Avisa que está caído |

En cron-job.org: **Create cronjob** → URL de arriba, cada 10 minutos, sin cabeceras ni datos. **Créalo con el servicio ya despierto**, para
que no empiece con fallos. `/salud` no pide token: no pongas claves ahí.

### Cuánto gasta de las 750 horas

Se cuenta el tiempo encendido, no las peticiones. Despierto todo el día son 24 h por día:

| Monitor encendido | Horas | De las 750 |
|---|---|---|
| Solo el día de la demo | 24 | 3 % |
| Una semana | 168 | 22 % |
| Un mes de 30 días | 720 | 96 % (sobran 30 h) |
| Un mes de 31 días | 744 | 99 % (sobran 6 h) |

Alcanza para **un** servicio despierto todo el mes, pero las 750 h son de **toda la cuenta**: con un segundo servicio gratis se acaban y Render
**suspende todos** hasta el mes siguiente. Enciende el monitor el día anterior y **apágalo después de la demo**. El consumo
se ve en **Billing → Monthly Included Usage**.

### Lista del día

1. **El día anterior:** monitor encendido, `JAVA_TOOL_OPTIONS` de la sección 8 puesta y las pruebas de las secciones 4 y 7 repetidas.
2. **5 minutos antes de presentar:** abrir `https://NOMBRE.onrender.com/api/salud` y esperar `{"estado":"OK"}`. Con el monitor responde al
   instante. Si estaba dormido, 5 minutos alcanzan con `JAVA_TOOL_OPTIONS` (1 min 47 s en local); **sin ella no** (4 min 35 s): hazlo 10 minutos antes.
3. Iniciar sesión una vez en la web y otra en la app: las primeras peticiones después de arrancar son más lentas.
4. No desplegar ni cambiar variables: reinicia el servicio. Render también puede reiniciarlo cuando quiera en el plan gratis.
5. **Plan B:** el backend en Docker en tu Mac, listo para levantar.

---

## Fuentes

Render: [plan gratis](https://render.com/docs/free), [regiones](https://render.com/docs/regions),
[health checks](https://render.com/docs/health-checks), [Docker](https://render.com/docs/docker),
[secretos con Docker](https://render.com/docs/docker-secrets), [Blueprint](https://render.com/docs/blueprint-spec),
[IP del cliente](https://render.com/articles/how-render-handles-ddos-attacks).
Resend: [SMTP](https://resend.com/docs/send-with-smtp).
Monitores: [cron-job.org](https://cron-job.org/en/faq/), [métodos de UptimeRobot](https://uptimerobot.com/blog/http-query-method-monitoring).
