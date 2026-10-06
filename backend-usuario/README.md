# backend-usuario (Spring Boot)

API REST para compradores y dueños de botica. El contrato está en [`../docs/API.md`](../docs/API.md).

## Requisitos
- Java 17+ y Maven 3.9+

## Puesta en marcha
1. Copiar `../.env.example` como `.env` **dentro de esta carpeta** y completar los datos de Supabase
   y `JWT_SECRET` (mínimo 32 caracteres). El `.env` nunca se sube al repo.
2. Levantar: `mvn spring-boot:run`
3. La API queda en `http://localhost:8080/api`.

## Reglas
- `ddl-auto=validate`: la API no crea ni modifica tablas (ver `../database/schema.sql`).
- Errores siempre con el formato de `API.md`.
