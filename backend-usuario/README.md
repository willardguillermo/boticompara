# backend-usuario

API REST para compradores y dueños de botica: registro, login, perfil, botica, catálogo y búsqueda.

- **Responsable:** Colaborador 1
- **Tecnología:** Spring Boot (Java, Maven), Spring Security + JWT, Spring Data JPA, driver PostgreSQL
- **Puerto local:** 8080, base `/api`
- **Contrato:** [`docs/API.md`](../docs/API.md)
- **Ramas:** `feature/backend-auth`, `feature/backend-catalogo`

Notas:
- Conexión con las variables de [`.env.example`](../.env.example) (`DB_SSLMODE=require` para Supabase).
- Las tablas se definen solo en [`database/schema.sql`](../database/schema.sql): usar
  `spring.jpa.hibernate.ddl-auto=validate` (nunca `update` ni `create`).
- Contraseñas con `BCryptPasswordEncoder` (los hashes del seed son `$2a$` y son compatibles).
- La búsqueda del comprador lee de la vista `v_producto_busqueda`.
