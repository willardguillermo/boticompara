# backend-usuario (Spring Boot)

API REST de BotiCompara para compradores y dueños de botica. Corre en `http://localhost:8080/api`.
El contrato (rutas, campos, códigos y errores) está en [`../docs/API.md`](../docs/API.md): si un endpoint cambia,
primero se actualiza ese archivo.

Responsable: Alexander. La base de datos (Supabase) la define y modifica solo Guillermo, en `../database/schema.sql`.

## Qué hace

- **Dueños de botica:** registran su cuenta y su botica (RUC validado en SUNAT, ubicación opcional y licencia de
  funcionamiento), administran su catálogo de productos y reciben una alerta diaria de stock bajo.
- **Compradores:** buscan medicamentos por nombre o principio activo, ven el detalle de un producto, comparan sus
  alternativas con el ahorro y guardan favoritos.
- **Administrador:** no usa esta API. Aprueba o rechaza las boticas desde Django (`../backend-admin/`).

## Requisitos

- Java 17+ y Maven 3.9+ (o el wrapper `mvnw.cmd` / `mvnw` de esta carpeta).
- Una base PostgreSQL en Supabase con `schema.sql` y `seed.sql` ya aplicados.

## Puesta en marcha

1. Copiar `../.env.example` como `.env` **dentro de esta carpeta** y completarlo. El `.env` nunca se sube al repo.
2. Levantar la API:
   ```powershell
   .\mvnw.cmd spring-boot:run     # en Linux/macOS: ./mvnw spring-boot:run
   ```
3. Comprobar que responde, por ejemplo: `http://localhost:8080/api/productos/7/alternativas` (es público).

Si el puerto 8080 ya está ocupado, hay una instancia vieja corriendo: cerrarla antes de volver a levantar.

### Variables de entorno (`.env`)

| Variable | Para qué sirve | Si falta |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_SSLMODE` | Conexión a Supabase | La API no arranca |
| `JWT_SECRET` | Firma de los JWT (mínimo 32 caracteres, duran 24 h) | La API no arranca |
| `SUPABASE_URL`, `SUPABASE_SECRET_KEY`, `SUPABASE_BUCKET_LICENCIAS` | Licencias en Storage privado (H4) | La API arranca, pero subir la licencia responde 503 |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Correos de H12, H17 y H20 (en desarrollo, sandbox de Mailtrap) | La API arranca, pero no envía correos |
| `RUC_API_URL`, `RUC_API_TOKEN` | Consulta del RUC en SUNAT con Decolecta (H2) | El RUC solo se valida localmente |
| `ALERTA_STOCK_CRON`, `APP_BASE_URL` | Opcionales: horario de la alerta y URL pública de los enlaces de correo | Valores por defecto (8:00 hora de Lima; `http://localhost:8080/api`) |

La clave secreta de Supabase y el token de Decolecta son **secretos**: solo van en el `.env`.

## Endpoints

Detalle completo, con ejemplos de petición y respuesta, en [`../docs/API.md`](../docs/API.md). 🔒 = pide token.

| Grupo | Endpoint | Historias | Acceso |
|---|---|---|---|
| Autenticación | `POST /auth/registro`, `POST /auth/login` | H1, H15, H18, H20 | Público |
| | `POST /auth/recuperar`, `POST /auth/restablecer` | H17 | Público |
| | `GET /auth/verificar?token=` | H20 | Público |
| Perfil | `GET`, `PUT`, `DELETE /usuarios/me` | H16, H19 | 🔒 cualquier rol |
| Botica | `GET /boticas/validar-ruc/{ruc}` | H2 | Público (con token consulta SUNAT) |
| | `POST /boticas`, `GET /boticas/mia`, `PUT /boticas/mia` | H1, H2, H3, H5, H7 | 🔒 dueño |
| | `POST /boticas/mia/licencia` | H4 | 🔒 dueño |
| Catálogo | `GET /boticas/mia/productos?q=&stockBajo=` | H12, H13, H14 | 🔒 dueño |
| | `POST /productos`, `PUT /productos/{id}`, `DELETE /productos/{id}` | H8, H9, H10 | 🔒 dueño |
| | `POST /productos/carga-masiva` (CSV y `.xlsx`) | H11 | 🔒 dueño |
| | `GET /medicamentos?q=` (catálogo maestro) | H31 | 🔒 dueño |
| | `DELETE /productos/{id}/medicamento` (quitar el enlace) | H31 | 🔒 dueño |
| Búsqueda | `GET /productos/buscar?q=&tipo=&orden=` | H22, H23, H24, H27 | 🔒 cualquier rol |
| | `GET /productos/{id}` | H28 | Público |
| | `GET /productos/{id}/alternativas` | H36, H37, H38, H41, H42 | Público |
| Favoritos | `POST /favoritos`, `GET /favoritos`, `DELETE /favoritos/{productoId}` | H43, H44 | 🔒 cualquier rol |

Todos los errores usan el mismo formato, descrito al inicio de `API.md`.

## Reglas de negocio importantes

### Qué productos ve el comprador
Solo los activos, con stock mayor que 0, de boticas en estado `APROBADO`, sin medicamento oculto y con registro
sanitario vigente (H38, H39). La regla vive en un solo lugar (`BusquedaService.FILTRO_VISIBLE`) y la comparten la
búsqueda, el detalle, las alternativas y los favoritos. Un producto que deja de verse se trata como inexistente (404).

### Alternativas y ahorro (H36, H37, H41, H42)
- Si el producto está enlazado a un medicamento del catálogo maestro que pertenece a un **grupo de equivalencia**,
  las alternativas son los productos de ese grupo: `tipoCoincidencia = EQUIVALENTE`.
- Si no está enlazado, o su medicamento no está en ningún grupo, se usan los productos con el mismo principio activo
  (sin distinguir mayúsculas ni espacios de más): `tipoCoincidencia = PRINCIPIO_ACTIVO`.
- Orden: menor a mayor precio. Máximo 20. El producto consultado no aparece entre sus alternativas.
- `ahorroPorcentaje = (precio consultado − precio de la alternativa) / precio consultado × 100`, con 1 decimal; es
  `null` si la alternativa no es más barata.
- `sinEquivalentes` es `true` cuando la lista viene vacía (H41).
- Cada consulta de un producto visible queda registrada en `consulta_comparacion`, con el usuario si llegó un token
  válido (H42). Si el registro falla, la respuesta no se ve afectada; las consultas con 404 no se registran.

### Enlace con el catálogo maestro (H31)
- Un producto puede enlazarse a un medicamento con `medicamentoId` en `POST /productos` o `PUT /productos/{id}`;
  el medicamento debe existir y no estar oculto.
- `PUT` sin `medicamentoId` **conserva** el enlace actual: enviar `null` no lo borra.
- Para quitarlo existe `DELETE /productos/{id}/medicamento`: responde `204`, también si el producto no estaba
  enlazado (idempotente). El producto sigue activo y conserva nombre, precio, stock y principio activo. Un producto
  de otra botica, inexistente o dado de baja responde `404`; un dueño sin botica aprobada, `403`.
- Después de desenlazar, las alternativas del producto salen por `PRINCIPIO_ACTIVO`.

### Favoritos (H43, H44)
- Cada usuario ve y modifica solo los suyos. Guardar dos veces el mismo producto no lo duplica (`201` la primera
  vez, `200` después). Quitar uno que no estaba también responde `204`.
- Solo se pueden guardar productos que el comprador ve hoy. Si un favorito deja de verse (por ejemplo, se queda sin
  stock), sale de la lista pero la fila se conserva y vuelve a aparecer cuando el producto se ve otra vez.
- La lista va del más reciente al más antiguo. Si se borra el producto, sus favoritos se borran solos (`on delete cascade`).

### Otras reglas
- `ddl-auto=validate`: la API no crea ni modifica tablas.
- Las tablas y columnas que no están en las entidades JPA (favoritos, consultas, grupos de equivalencia) se leen y
  escriben con `NamedParameterJdbcTemplate`.
- CORS permite solo `http://localhost:5173` (la web de Vite). El móvil no necesita CORS.
- Seguridad: JWT con rol. `/productos/**` y `/boticas/**` exigen rol `DUENO_BOTICA`, salvo las rutas públicas o
  de cualquier rol de la tabla de endpoints.

## Estructura

```
src/main/java/com/boticompara/usuario/
  controller/   Endpoints REST (uno por grupo de historias)
  service/      Reglas de negocio (Busqueda, Alternativas, Favorito, Producto, Medicamento, Boticas, Auth...)
  repository/   Acceso JPA a usuario, botica, producto y tokens
  entity/       Usuario, Botica, Producto, TokenUsuario
  dto/          Peticiones y respuestas (camelCase, como en API.md)
  security/     JWT, filtro y reglas de acceso
  error/        ApiException y formato de error único
  job/          Alerta diaria de stock bajo (H12)
  util/         Ahorro, Distancia, RucValidator, lectores de CSV y xlsx
```

## Pruebas

### Tests automáticos
```powershell
.\mvnw.cmd test
```
Resultado actual: **215 tests, 0 fallas**. Los de este sprint:

| Clase | Tests | Historias |
|---|---|---|
| `AlternativasServiceTest` | 14 | H36, H37, H38, H41, H42 |
| `FavoritoServiceTest` | 8 | H43, H44 |
| `ProductoServiceTest` | 13 | H8, H9, H31 (enlazar y desenlazar) |
| `MedicamentoServiceTest` | 5 | H31 |
| `AhorroTest` | 7 | H37 |

### Colección de Postman
[`../docs/postman/BotiCompara.postman_collection.json`](../docs/postman/BotiCompara.postman_collection.json), con
8 carpetas. Con la API levantada se ejecuta cada carpeta con **Run folder**. Todas las pruebas comprueban también
que la respuesta tarda menos de 2 s (H26).

- **Carpeta 7 (H40):** Rosa sube el precio y el stock de un producto, el comprador lo ve en la búsqueda y en el
  detalle, y Rosa lo restaura. 29 pruebas.
- **Carpeta 8 (H2, H28, H31, H36–H44):** detalle, alternativas, medicamentos, favoritos, validar RUC y el
  desenlazado del H31. 92 pruebas, todas en verde. No crea productos: lo que modifica, lo restaura.

### Pruebas en vivo
Durante el sprint cada historia se probó contra Supabase con scripts locales de PowerShell, que crean productos
`ZZ …` en la botica de Rosa, comprueban cada caso (incluida la base de datos) y los borran siempre al final, con
doble filtro por nombre y botica. Resultados: alternativas 26 OK, favoritos 29 OK, desenlazar 17 OK. Esos scripts
no se suben al repo; la evidencia de cada prueba está en la descripción del PR correspondiente.

### Cuentas de prueba (`seed.sql`, contraseña `Boti2026!`)
`rosa@boticasalud.pe` y `jorge@farmavida.pe` (dueños, boticas aprobadas), `lucia@boticanueva.pe` (botica pendiente),
`carlos@correo.com` (comprador).

## Fuera de alcance (Sprint 3)

- Validar que el principio activo del producto coincida con el del medicamento elegido (en `POST`, en `PUT` con
  `medicamentoId` y en `PUT` sin él).
- Que la web rellene el principio activo con el del medicamento elegido, y que muestre un botón "Quitar enlace" que
  llame a `DELETE /productos/{id}/medicamento`.
- La carga masiva (CSV y `.xlsx`) no maneja `medicamentoId`: los productos que crea no quedan enlazados.
- Los grupos de equivalencia se administran desde Django; esta API solo los lee.

## Convenciones

- Commits en español, `tipo(capa): qué se hizo (Hxx)`. Tipos: `feat`, `fix`, `docs`, `style`, `refactor`, `test`,
  `build`; nunca `chore`. Una rama `feature/...` por grupo de historias y un Pull Request a `main` con merge
  commit, sin squash. Nadie aprueba su propio PR.
- Nunca se suben `.env`, claves ni scripts `.ps1` locales.
