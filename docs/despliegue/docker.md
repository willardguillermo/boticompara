# Docker para el backend de BotiCompara

Guía para aprender Docker con `backend-usuario` (Spring Boot) y prepararlo para Render.
Los archivos que se explican son:

- `backend-usuario/Dockerfile`: la receta para construir la imagen.
- `backend-usuario/.dockerignore`: lo que **no** entra a la imagen.
- `compose.yaml` (en la raíz): atajo para levantar el backend en tu Mac con un solo comando.

> Todos los comandos se escriben en la **raíz del repo** (`~/Proyectos/boticompara`), salvo que se diga otra cosa.
> Docker Desktop tiene que estar abierto (la ballena en la barra de menú).

---

## 1. Las ideas básicas

| Concepto | Qué es | Comparación |
|---|---|---|
| **Imagen** | Un paquete de solo lectura con todo lo necesario para correr el programa: sistema base (Ubuntu mínimo), Java 17 y nuestro `.jar`. Se construye una vez. | La *clase* en Java, o el instalador de un programa. |
| **Contenedor** | Una imagen **en ejecución**. Es un proceso aislado: tiene su propio sistema de archivos, su red y su límite de memoria. Se puede crear, detener y borrar sin tocar la imagen. | El *objeto* creado con `new`. De una imagen salen muchos contenedores. |
| **Dockerfile** | La receta, paso a paso, para construir la imagen. | El `pom.xml`, pero para el sistema completo. |
| **Capa** | Cada instrucción del Dockerfile genera una capa. Docker guarda las capas en caché: si un paso y los anteriores no cambiaron, no lo repite. | |
| **Registro** | Donde se guardan imágenes para descargarlas (Docker Hub). De ahí vienen `eclipse-temurin:17-jdk` y `eclipse-temurin:17-jre`. | Maven Central, pero de imágenes. |
| **Puerto publicado** | El contenedor tiene su propia red. `-p 8080:8080` conecta el puerto 8080 de tu Mac con el 8080 del contenedor. | |
| **Variable de entorno** | La forma de pasarle configuración y secretos al contenedor **al correrlo**, sin meterlos en la imagen. | El `.env`. |
| **Volumen / montaje** | Una carpeta de tu Mac que se ve dentro del contenedor. Lo usamos para el JSON de Firebase. | |

¿Por qué Docker? Porque la imagen trae su propio Java: el backend corre igual en tu Mac, en la de Alexander y en Render,
sin instalar nada más que Docker. Render construye la imagen con nuestro `Dockerfile` y la corre.

### Cómo arranca el backend hoy (sin Docker)

- **Java 17** (`<java.version>17</java.version>` en el `pom.xml`) y **Maven 3.9.16** a través del wrapper `./mvnw`
  (lo define `.mvn/wrapper/maven-wrapper.properties`), con Spring Boot 3.5.
- `application.properties` lee el `.env` de la carpeta donde se lanza (`spring.config.import=optional:file:.env[.properties]`).
  Si no existe, usa las variables de entorno del sistema. **En el contenedor no hay `.env`**: las variables se pasan al correrlo.
- El puerto era `server.port=8080`. Ahora es `server.port=${PORT:8080}`: si existe la variable `PORT` (Render la pone) se usa esa;
  si no, 8080 como siempre. En tu Mac, sin Docker, todo sigue igual.
- Login con Google (H67): `FirebaseVerificadorGoogle` lee el archivo JSON de la cuenta de servicio desde la ruta
  `FIREBASE_CREDENTIALS_PATH`, junto con `FIREBASE_PROJECT_ID`. Si falta alguna de las dos, la API arranca igual y el login con
  Google responde 503. Ojo: si `FIREBASE_PROJECT_ID` tiene valor pero el archivo no existe en esa ruta, la API **no arranca**.

---

## 2. El `Dockerfile`, línea por línea

Tiene **dos etapas** (*multi-stage build*). La primera tiene el JDK completo y Maven para compilar; la segunda solo el JRE
y el `.jar`. La imagen final no lleva el código fuente, ni Maven, ni las dependencias de compilación: pesa menos y expone menos.

### Etapa 1: compilación

| Línea | Qué hace |
|---|---|
| `FROM eclipse-temurin:17-jdk AS compilacion` | Parte de una imagen oficial con el JDK 17 (Eclipse Temurin). `AS compilacion` le pone nombre a la etapa para copiar de ella después. |
| `WORKDIR /fuente` | Crea la carpeta `/fuente` dentro de la imagen y se para ahí (como un `cd`). |
| `COPY .mvn/ .mvn/` y `COPY mvnw pom.xml ./` | Copia **solo** el wrapper de Maven y el `pom.xml`, todavía no el código. |
| `RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline` | Da permiso de ejecución al wrapper y descarga todas las dependencias. `-B` es modo no interactivo y `-q` muestra menos texto. Como esta capa solo depende del `pom.xml`, Docker la reutiliza mientras el `pom.xml` no cambie: los siguientes builds son mucho más rápidos. |
| `COPY src/ src/` | Ahora sí copia el código. |
| `RUN ./mvnw -B -q package -DskipTests && cp target/backend-usuario-*.jar /fuente/app.jar` | Compila y arma el `.jar`, y lo copia con un nombre fijo (`app.jar`). Las pruebas se saltan aquí porque algunas necesitan la base de datos, que no existe durante el build; se siguen corriendo con `./mvnw test` como siempre. |

### Etapa 2: ejecución

| Línea | Qué hace |
|---|---|
| `FROM eclipse-temurin:17-jre` | Empieza una imagen **nueva**, solo con el JRE 17 (lo justo para correr Java, sin compilador). Todo lo de la etapa 1 queda fuera, salvo lo que copiemos. |
| `RUN groupadd --system boticompara && useradd ...` | Crea el usuario `boticompara` sin permisos de administrador (sin carpeta personal). |
| `WORKDIR /app` | Carpeta de trabajo. Como en `/app` no hay `.env`, Spring usa las variables de entorno. |
| `COPY --from=compilacion --chown=boticompara:boticompara /fuente/app.jar app.jar` | Trae el `.jar` desde la etapa 1 y lo deja a nombre del usuario nuevo. |
| `USER boticompara` | Desde aquí todo corre con ese usuario, no como `root`. Si alguien aprovechara una falla de la API, no tendría permisos de administrador en el contenedor. |
| `ENV PORT=8080` | Valor por defecto del puerto. Render lo reemplaza con el suyo al correr el contenedor. |
| `ENV JAVA_TOOL_OPTIONS="..."` | Opciones de memoria de Java (ver abajo). Java lee esta variable solo, por eso aparece el mensaje `Picked up JAVA_TOOL_OPTIONS` al arrancar. |
| `ENV SERVER_TOMCAT_THREADS_MAX=40` | Spring convierte esta variable en `server.tomcat.threads.max`. Tomcat usa 200 hilos por defecto y cada uno reserva memoria; 40 sobra para el proyecto. |
| `EXPOSE 8080` | Documenta el puerto en el que escucha. No publica nada por sí solo: eso lo hace `-p`. |
| `ENTRYPOINT ["java", "-jar", "/app/app.jar"]` | El comando que se ejecuta al iniciar el contenedor. |

### La memoria en 512 MB

El plan gratis de Render da 512 MB **para todo el contenedor**, no solo para el heap de Java. Si se pasa, Render mata el proceso.

| Opción | Para qué |
|---|---|
| `-XX:MaxRAMPercentage=65` | El heap (los objetos de Java) usa como máximo el 65 % de la RAM del contenedor: unos 330 MB. El resto queda para el metaspace (las clases), los hilos y la memoria nativa. |
| `-XX:+UseSerialGC` | Recolector de basura simple, el que menos memoria usa. Con una sola CPU es la mejor opción. |
| `-XX:MaxMetaspaceSize=128m` | Tope para la memoria de las clases cargadas (Spring, Hibernate, Firebase, POI...). |
| `-Xss512k` | Cada hilo reserva 512 KB de pila en vez de 1 MB. |
| `-XX:+ExitOnOutOfMemoryError` | Si se queda sin memoria, Java se cierra de inmediato en vez de quedar colgado; Render lo reinicia. |

---

## 3. El `.dockerignore`

Al construir, Docker envía la carpeta `backend-usuario/` (el **contexto**) al motor de Docker. El `.dockerignore` dice qué
no enviar:

- `target/`: compilaciones de tu Mac; la imagen compila la suya.
- `.env`, `.env.*`, `*.json`, `*.pem`, `*.key`, `*.p12`, `*.jks`: secretos. **Nada secreto entra a la imagen**; si entrara,
  cualquiera con la imagen podría leerlo. Los secretos se pasan al correr el contenedor.
- `.sonar/`, `.idea/`, `.vscode/`, logs: no hacen falta.

---

## 4. El `compose.yaml`, línea por línea

`docker compose` lee este archivo y hace el `build` y el `run` con todas las opciones ya escritas.

| Línea | Qué hace |
|---|---|
| `services:` | Lista de contenedores. Solo hay uno, `backend`. |
| `build: context: ./backend-usuario` y `dockerfile: Dockerfile` | Construye la imagen con el Dockerfile de `backend-usuario/`. |
| `image: boticompara-backend` | Nombre de la imagen construida. |
| `container_name: boticompara-backend` | Nombre fijo del contenedor, para los comandos de logs y detener. |
| `env_file: - .env` | Carga las variables del `.env` de la raíz (que **no** se copia a la imagen: solo se leen sus valores al correr). |
| `environment: PORT: "8080"` | Puerto dentro del contenedor. |
| `environment: FIREBASE_CREDENTIALS_PATH: /secretos/firebase.json` | La ruta del JSON **dentro** del contenedor. Reemplaza la del `.env`, que es una ruta de tu Mac y dentro no existe. |
| `ports: - "8080:8080"` | Puerto de tu Mac : puerto del contenedor. |
| `volumes: - ./secretos:/secretos:ro` | Monta la carpeta `secretos/` de la raíz en `/secretos`, en **solo lectura** (`ro`). Si no existe, Docker la crea vacía. Está en el `.gitignore`. |
| `mem_limit: 512m` | El mismo límite de memoria que en Render, para probar en tu Mac lo que pasará allá. |
| `restart: "no"` | Si se cae, no se reinicia solo: en local preferimos ver el error. |

### El JSON de Firebase (opcional)

Para probar el login con Google en el contenedor:

1. Crea la carpeta `secretos/` en la raíz y copia ahí el JSON de la cuenta de servicio con el nombre `firebase.json`.
2. Agrega `FIREBASE_PROJECT_ID=boticompara` al `.env` de la raíz.

Si no lo haces, la API arranca igual y solo el login con Google responde 503. No pongas `FIREBASE_PROJECT_ID` sin el archivo:
la API no arrancaría.

### Diferencia entre el `.env` de la raíz y el de `backend-usuario/`

El `compose.yaml` usa el `.env` **de la raíz**. Si quieres el chat (H68) o la consulta del RUC (H2) en el contenedor, copia al
`.env` de la raíz las líneas `GEMINI_API_KEY`, `GEMINI_MODEL`, `RUC_API_URL` y `RUC_API_TOKEN` que tienes en `backend-usuario/.env`.

---

## 5. Comandos

Antes de empezar, detén el backend si lo tienes corriendo con `./mvnw spring-boot:run`: los dos usan el puerto 8080.

### Opción A: con `docker` directo (para entender cada paso)

Construir la imagen a partir del Dockerfile. `-t` le pone nombre; el último argumento es la carpeta de contexto.
La primera vez tarda varios minutos porque descarga Java y las dependencias.

```bash
docker build -t boticompara-backend ./backend-usuario
```

Ver las imágenes que tienes y cuánto pesan:

```bash
docker images boticompara-backend
```

Correr un contenedor. `-d` lo deja en segundo plano, `--name` le da nombre, `--env-file` pasa las variables,
`-p` publica el puerto y `--memory` limita la RAM como en Render.

```bash
docker run -d --name boticompara-backend --env-file .env -p 8080:8080 --memory 512m boticompara-backend
```

Ver los contenedores que están corriendo:

```bash
docker ps
```

Ver los logs (lo que antes veías en la terminal de `mvnw`). `-f` los sigue en vivo; se sale con `Ctrl+C`, y el contenedor sigue corriendo.

```bash
docker logs -f boticompara-backend
```

Ver cuánta memoria y CPU usa (se sale con `Ctrl+C`):

```bash
docker stats boticompara-backend
```

Detener el contenedor (Spring se cierra ordenadamente):

```bash
docker stop boticompara-backend
```

Volver a arrancar el mismo contenedor detenido:

```bash
docker start boticompara-backend
```

Borrar el contenedor (tiene que estar detenido). La imagen se queda:

```bash
docker rm boticompara-backend
```

Borrar la imagen (después de borrar sus contenedores):

```bash
docker rmi boticompara-backend
```

Entrar a una terminal dentro del contenedor, para mirar (se sale con `exit`):

```bash
docker exec -it boticompara-backend sh
```

### Opción B: con `docker compose` (el día a día)

Construir y levantar en segundo plano:

```bash
docker compose up -d --build
```

Ver los logs:

```bash
docker compose logs -f backend
```

Detener y borrar el contenedor (la imagen se queda):

```bash
docker compose down
```

### Comprobar que responde

Endpoint público (alternativas de un producto):

```bash
curl -i http://localhost:8080/api/productos/7/alternativas
```

La búsqueda pide token. Primero inicia sesión con Carlos y guarda el token en la variable `TOKEN`:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"correo":"carlos@boticompara.nissidev.dev","password":"Boti2026!"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
```

Luego busca paracetamol:

```bash
curl -s "http://localhost:8080/api/productos/buscar?q=paracetamol&tipo=nombre&orden=precio_asc" -H "Authorization: Bearer $TOKEN"
```

### Limpieza general

Docker guarda capas e imágenes viejas. Para liberar espacio (pide confirmación con `y`):

```bash
docker system prune
```

---

## 6. Problemas comunes

| Síntoma | Causa probable |
|---|---|
| `Cannot connect to the Docker daemon` | Docker Desktop no está abierto. |
| `port is already allocated` | Algo ya usa el 8080: el backend con `mvnw` u otro contenedor (`docker ps`). |
| `Conflict. The container name ... is already in use` | Ya existe un contenedor con ese nombre (aunque esté detenido): `docker rm boticompara-backend`. |
| El contenedor se detiene solo | Mira `docker logs boticompara-backend`. Lo más común: falta una variable (`DB_HOST`, `JWT_SECRET`...) o `FIREBASE_PROJECT_ID` está puesto sin el JSON. |
| `Exited (137)` | Se quedó sin memoria (512 MB). |

---

## 7. En Render (el lunes)

1. **New → Web Service**, conectar el repo y elegir **Docker** como entorno.
2. **Root Directory:** `backend-usuario` (ahí están el `Dockerfile` y el `.dockerignore`).
3. **Instance type:** Free.
4. **Environment:** cargar una por una las variables del `.env` (`DB_HOST`, `DB_PASSWORD`, `JWT_SECRET`, etc.). **No** hace falta
   `PORT`: Render la pone sola. Poner `APP_BASE_URL` con la URL pública que dé Render, terminada en `/api`.
5. Firebase: en **Secret Files** subir el JSON con el nombre `firebase.json`; Render lo deja en `/etc/secrets/firebase.json`.
   Entonces `FIREBASE_CREDENTIALS_PATH=/etc/secrets/firebase.json` y `FIREBASE_PROJECT_ID=boticompara`.
6. En el plan gratis, el servicio se duerme tras unos minutos sin visitas; la primera petición después tarda en responder
   mientras arranca Spring.
