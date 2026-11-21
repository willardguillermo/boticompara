# Imagen del backend de BotiCompara (Spring Boot). Explicación línea por línea en docs/despliegue/docker.md

# ---------- Etapa 1: compilar el .jar con Maven y el JDK 17 ----------
FROM eclipse-temurin:17-jdk AS compilacion
WORKDIR /fuente

# Primero solo lo que define las dependencias, para que Docker las guarde en caché
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Después el código: si solo cambia el código, no se vuelven a descargar las dependencias
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests && cp target/backend-usuario-*.jar /fuente/app.jar

# ---------- Etapa 2: solo el JRE 17 y el .jar ----------
FROM eclipse-temurin:17-jre

# Usuario sin permisos de administrador
RUN groupadd --system boticompara && useradd --system --gid boticompara --no-create-home boticompara

WORKDIR /app
COPY --from=compilacion --chown=boticompara:boticompara /fuente/app.jar app.jar
USER boticompara

# Render pasa el puerto en PORT; en local se usa 8080
ENV PORT=8080
# Memoria para 512 MB: el heap usa como máximo el 65 % de la RAM del contenedor
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -Xss512k -XX:+ExitOnOutOfMemoryError"
# Menos hilos de Tomcat (200 por defecto): cada hilo ocupa memoria
ENV SERVER_TOMCAT_THREADS_MAX=40

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
