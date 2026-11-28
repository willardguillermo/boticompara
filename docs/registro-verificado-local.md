# Probar en local el registro con el correo verificado (H1, H20)

Contrato: `docs/API.md`, sección 1. Esta guía levanta en Docker un PostgreSQL con `schema.sql` + `seed.sql` y
Mailpit (un servidor de correo de prueba con bandeja web), y corre la API contra ellos **sin tocar Supabase ni gastar
la cuota de SUNAT ni la de correo**. Los comandos se ejecutan desde la raíz del repositorio.

## 1. PostgreSQL y Mailpit

```bash
docker run -d --name bc-pg -e POSTGRES_PASSWORD=postgres -p 55432:5432 postgres:17
docker run -d --name bc-mail -e MP_SMTP_AUTH_ACCEPT_ANY=1 -e MP_SMTP_AUTH_ALLOW_INSECURE=1 \
  -p 1025:1025 -p 8025:8025 axllent/mailpit
until docker exec bc-pg pg_isready -U postgres >/dev/null 2>&1; do sleep 1; done

# Lo que Supabase ya trae: los roles de su API automática y el esquema extensions
docker exec -i bc-pg psql -U postgres -v ON_ERROR_STOP=1 <<'SQL'
create role anon nologin;
create role authenticated nologin;
create role boticompara_app login password 'app_local';
create schema extensions;
create extension pgcrypto schema extensions;
create extension pg_trgm schema extensions;
SQL
docker exec -i bc-pg psql -U postgres -v ON_ERROR_STOP=1 -q < database/schema.sql
docker exec -i bc-pg psql -U postgres -v ON_ERROR_STOP=1 -q < database/seed.sql
```

`schema.sql` ya trae la tabla `codigo_correo`. Para probar `v9_verificacion_correo.sql` sobre una base "vieja", cargar
el `schema.sql` de `main` (`git show origin/main:database/schema.sql`), luego `seed.sql` y después v9 (primero con la
prueba en seco descrita al inicio del archivo).

## 2. La API contra esa base

Las variables de entorno pesan más que el `.env`, así que basta con pisar la base, el correo y SUNAT:

```bash
cd backend-usuario
./mvnw -q package -DskipTests
PORT=18080 DB_HOST=localhost DB_PORT=55432 DB_NAME=postgres DB_USER=postgres DB_PASSWORD=postgres \
DB_SSLMODE=disable MAIL_HOST=localhost MAIL_PORT=1025 MAIL_USERNAME=prueba MAIL_PASSWORD=prueba \
RUC_API_URL= RUC_API_TOKEN= SUPABASE_URL= SUPABASE_SECRET_KEY= GEMINI_API_KEY= ALERTA_STOCK_HABILITADA=false \
java -jar target/backend-usuario-0.0.1-SNAPSHOT.jar
```

- Sin `RUC_API_TOKEN`, el RUC solo se valida localmente (`fuente: LOCAL`): no gasta Decolecta.
- Sin `SUPABASE_URL`, `POST /boticas/mia/licencia` responde 503 (la licencia no se prueba en local).
- Los correos llegan a la bandeja de Mailpit: <http://localhost:8025>. Si `MAIL_HOST` está vacío,
  `POST /auth/registro/codigo` responde 503 (el código nunca se escribe en el log).

## 3. El flujo con curl

```bash
API=http://localhost:18080/api
curl -s -X POST $API/auth/registro/codigo -H 'Content-Type: application/json' -d '{"correo":"nueva@correo.pe"}'
# → abrir http://localhost:8025 y copiar el código de 6 dígitos
curl -s -X POST $API/auth/registro/verificar -H 'Content-Type: application/json' \
  -d '{"correo":"nueva@correo.pe","codigo":"123456"}'
# → {"tokenVerificacion":"..."}
curl -s -X POST $API/auth/registro/validar-ruc -H 'Content-Type: application/json' \
  -d '{"tokenVerificacion":"...","ruc":"20131312955"}'
curl -s -X POST $API/auth/registro -H 'Content-Type: application/json' -d '{
  "tokenVerificacion":"...","nombre":"Rosa Nueva","correo":"nueva@correo.pe","password":"Boti2026!",
  "rol":"DUENO_BOTICA","botica":{"nombreComercial":"Botica Nueva","ruc":"20131312955",
  "razonSocial":"Nueva S.A.C.","direccion":"Av. Lima 1","distrito":"Chosica"}}'
# → 201 con el JWT; luego GET /boticas/mia con ese token muestra la botica PENDIENTE sin licencia
```

Para ver la transacción: registrar con el RUC `20601234565` (Botica Salud Total, ya `APROBADO`) da 409, no crea la
cuenta (`select count(*) from usuario where correo = 'nueva@correo.pe'` → 0) y el mismo token sirve para reintentar
con otro RUC.

Cuenta antigua sin verificar:

```bash
docker exec bc-pg psql -U postgres -c \
  "update usuario set correo_verificado = false where correo = 'lucia@boticompara.nissidev.dev'"
curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"correo":"lucia@boticompara.nissidev.dev","password":"Boti2026!"}'
# → 403 con "codigo":"CORREO_NO_VERIFICADO"
curl -s -X POST $API/auth/verificacion/codigo -H 'Content-Type: application/json' \
  -d '{"correo":"lucia@boticompara.nissidev.dev"}'
curl -s -X POST $API/auth/verificacion/confirmar -H 'Content-Type: application/json' \
  -d '{"correo":"lucia@boticompara.nissidev.dev","codigo":"(el de Mailpit)"}'
# → ahora el login responde 200
```

## 4. Las pruebas del backend contra esa base

Con la base de Docker, las pruebas que usan PostgreSQL (incluida `CodigoCorreoConsultasTest`) corren en lugar de
omitirse. Mientras Supabase no tenga v9, esas pruebas fallan con `missing table [codigo_correo]` si se corren con el
`.env` que apunta a Supabase.

```bash
cd backend-usuario
DB_HOST=localhost DB_PORT=55432 DB_NAME=postgres DB_USER=postgres DB_PASSWORD=postgres DB_SSLMODE=disable ./mvnw test
```

## 5. Limpiar

```bash
docker rm -f bc-pg bc-mail
```
