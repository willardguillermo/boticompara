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
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Correos de H12, H17, H20 y H61 (Resend por SMTP, ver `../.env.example`) | La API arranca, pero no envía correos |
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
| | `PUT /usuarios/me/notificaciones` | H66 | 🔒 cualquier rol |
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
| Alertas | `GET /alertas`, `GET /alertas/resumen`, `PUT /alertas/{id}/leida`, `PUT /alertas/leidas` | H59 | 🔒 cualquier rol |
| Favoritos | `POST /favoritos`, `GET /favoritos`, `DELETE /favoritos/{productoId}` | H43, H44 | 🔒 cualquier rol |
| | `PUT /favoritos/{productoId}/precio-objetivo` | H60 | 🔒 cualquier rol |

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

### Alertas de baja de precio (H59)
- Se evalúan en `PUT /productos/{id}` y en la carga masiva, en la misma transacción que el cambio de precio
  (`AlertaService.registrarBaja`). Antes, `productos.flush()` deja el cambio en la BD para que la vista de búsqueda
  vea el producto ya actualizado.
- Solo si el precio baja y el producto se ve después del cambio. Para cada favorito de un usuario activo con
  `notificacionesPrecio`: sin precio objetivo, alerta en cada baja; con objetivo, solo si el precio nuevo es menor o
  igual al objetivo y menor que el último avisado. El precio nuevo queda como `ultimo_precio_avisado`.
- `GET /alertas` devuelve hasta 100, de la más reciente a la más antigua (`soloNoLeidas` solo acepta `true` o
  `false`). Marcar una alerta de otro usuario responde `404`. La alerta se conserva aunque el producto deje de verse.
- Al eliminar la cuenta (H19) se borran sus alertas.

### Correo de baja de precio (H61)
- Al crear alertas, `ProductoService.actualizar` y `CargaMasivaService` publican un evento `AlertasCreadas`.
  `AlertaCorreoService` lo escucha con `@TransactionalEventListener(AFTER_COMMIT)` y `@Async` (`@EnableAsync`): el
  correo sale solo si el cambio de precio se confirmó y no hace más lenta la respuesta.
- Un correo por usuario. En `PUT /productos/{id}` es uno por alerta (asunto: "Panadol bajó a S/ 7.50 en FarmaVida");
  en la carga masiva, uno solo con todas las bajas de ese usuario ("Bajaron de precio N productos de tus favoritos").
- Si el correo falla, la alerta queda guardada y solo se deja un aviso en el log. Con `MAIL_HOST` vacío no se envía
  nada y el log muestra el asunto, lo que sirve para probar sin gastar el límite de Resend.

### Enlace con el catálogo maestro (H31)
- Un producto puede enlazarse a un medicamento con `medicamentoId` en `POST /productos` o `PUT /productos/{id}`;
  el medicamento debe existir y no estar oculto.
- `PUT` sin `medicamentoId` **conserva** el enlace actual: enviar `null` no lo borra.
- Para quitarlo existe `DELETE /productos/{id}/medicamento`: responde `204`, también si el producto no estaba
  enlazado (idempotente). El producto sigue activo y conserva nombre, precio, stock y principio activo. Un producto
  de otra botica, inexistente o dado de baja responde `404`; un dueño sin botica aprobada, `403`.
- Después de desenlazar, las alternativas del producto salen por `PRINCIPIO_ACTIVO`.
- **D2:** mientras el producto esté enlazado, su principio activo es el del medicamento: se copia al enlazar (en
  `POST` o `PUT` con `medicamentoId`) y se mantiene en un `PUT` sin `medicamentoId`; el texto enviado se ignora. Al
  quitar el enlace, el producto conserva ese texto y desde entonces se usa el que envía la web. Así las alternativas
  por principio activo usan el mismo nombre que el catálogo maestro.
- **D4:** la carga masiva (CSV y `.xlsx`) acepta la columna opcional `medicamentoId`. Con valor, enlaza el producto
  (nuevo o existente) y copia el principio activo del medicamento, como en D2. Un id que no es un entero mayor que 0,
  que no existe o que está oculto deja esa fila como error y las demás se guardan. Vacía o ausente, conserva el
  enlace actual. Cada medicamento se consulta una vez por carga con `MedicamentoService.enlace`, que devuelve el
  motivo en vez de lanzar una excepción para no marcar la transacción como *rollback-only*.

### Favoritos (H43, H44, H60)
- Cada usuario ve y modifica solo los suyos. Guardar dos veces el mismo producto no lo duplica (`201` la primera
  vez, `200` después). Quitar uno que no estaba también responde `204`.
- Solo se pueden guardar productos que el comprador ve hoy. Si un favorito deja de verse (por ejemplo, se queda sin
  stock), sale de la lista pero la fila se conserva y vuelve a aparecer cuando el producto se ve otra vez.
- La lista va del más reciente al más antiguo. Si se borra el producto, sus favoritos se borran solos (`on delete cascade`).
- **H60:** cada favorito trae `precioObjetivo` (`null` si no hay). `PUT /favoritos/{productoId}/precio-objetivo` lo
  pone, cambia o quita (con `null`); el cuerpo se lee como `JsonNode` para distinguir el campo ausente (`400`) de
  `null`. Cada cambio reinicia `ultimo_precio_avisado`. Si el producto ya no se ve responde `404` y la transacción
  deshace el cambio.

### Preferencia de alertas de precio (H66)
- `notificacionesPrecio` viene en el perfil y por defecto es `true`. Solo cambia con `PUT /usuarios/me/notificaciones`;
  `PUT /usuarios/me` no lo toca. Falta o `null` responde `400`.
- Con `false` no se crean alertas de baja de precio ni se envían sus correos (H59, H61).

### Otras reglas
- `ddl-auto=validate`: la API no crea ni modifica tablas.
- Las tablas y columnas que no están en las entidades JPA (favoritos, consultas, grupos de equivalencia) se leen y
  escriben con `NamedParameterJdbcTemplate`.
- CORS permite solo `http://localhost:5173` (la web de Vite). El móvil no necesita CORS.
- Seguridad: JWT con rol. `/productos/**` y `/boticas/**` exigen rol `DUENO_BOTICA`, salvo las rutas públicas o
  de cualquier rol de la tabla de endpoints.
- **SEC-05:** `POST /auth/recuperar` no genera ni envía otro código si ya se generaron 3 para ese usuario en la
  última hora (cuenta también los usados o invalidados); la respuesta sigue siendo `200` con el mismo mensaje. Cada
  código se sigue invalidando a los 5 intentos y solo se puede pedir uno por minuto.

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
Resultado actual: **299 tests, 0 fallas**. Los de este sprint:

| Clase | Tests | Historias |
|---|---|---|
| `AlternativasServiceTest` | 14 | H36, H37, H38, H41, H42 |
| `FavoritoServiceTest` | 8 | H43, H44 |
| `ProductoServiceTest` | 13 | H8, H9, H31 (enlazar y desenlazar) |
| `MedicamentoServiceTest` | 5 | H31 |
| `AhorroTest` | 7 | H37 |

### Colección de Postman
[`../docs/postman/BotiCompara.postman_collection.json`](../docs/postman/BotiCompara.postman_collection.json), con
9 carpetas. Con la API levantada se ejecuta cada carpeta con **Run folder**. Todas las pruebas comprueban también
que la respuesta tarda menos de 2 s (H26).

- **Carpeta 7 (H40):** Rosa sube el precio y el stock de un producto, el comprador lo ve en la búsqueda y en el
  detalle, y Rosa lo restaura. 29 pruebas.
- **Carpeta 8 (H2, H28, H31, H36–H44):** detalle, alternativas, medicamentos, favoritos, validar RUC y el
  desenlazado del H31. 92 pruebas, todas en verde. No crea productos: lo que modifica, lo restaura.
- **Carpeta 9 (H59, H60, H66):** Carlos guarda un producto de Rosa en favoritos con un precio objetivo, Rosa baja el
  precio dos veces (la primera no llega al objetivo), Carlos ve la alerta y la marca como leída, y se prueba
  desactivar y reactivar las alertas. 85 pruebas, todas en verde. No crea productos: lo que modifica, lo restaura
  (deja una alerta ya leída en el historial de Carlos). Con `MAIL_HOST` configurado, la baja del paso 8 envía el
  correo de H61.

### Pruebas en vivo
Durante el sprint cada historia se probó contra Supabase con scripts locales de PowerShell, que crean productos
`ZZ …` en la botica de Rosa, comprueban cada caso (incluida la base de datos) y los borran siempre al final, con
doble filtro por nombre y botica. Resultados: alternativas 26 OK, favoritos 29 OK, desenlazar 17 OK, correo de baja
de precio 8 OK, `medicamentoId` en la carga masiva 9 OK, límite de códigos de recuperación 7 OK. Esos scripts
no se suben al repo; la evidencia de cada prueba está en la descripción del PR correspondiente.

### Cuentas de prueba (contraseña `Boti2026!`)
Desde el Sprint 3 reciben correo real (Resend), que llega a la bandeja del equipo.

| Correo | Rol | Botica |
|---|---|---|
| `rosa@boticompara.nissidev.dev` | Dueña | Botica Salud Total, aprobada |
| `jorge@boticompara.nissidev.dev` | Dueño | FarmaVida, aprobada |
| `lucia@boticompara.nissidev.dev` | Dueña | Botica Nueva Era, rechazada (sirve para probar la corrección y el reenvío, H7) |
| `carlos@boticompara.nissidev.dev` | Comprador | — |

## Fuera de alcance (Sprint 3)

- Que la web rellene el principio activo con el del medicamento elegido, y que muestre un botón "Quitar enlace" que
  llame a `DELETE /productos/{id}/medicamento`.
- Los grupos de equivalencia se administran desde Django; esta API solo los lee.

## Convenciones

- Commits en español, `tipo(capa): qué se hizo (Hxx)`. Tipos: `feat`, `fix`, `docs`, `style`, `refactor`, `test`,
  `build`; nunca `chore`. Una rama `feature/...` por grupo de historias y un Pull Request a `main` con merge
  commit, sin squash. Nadie aprueba su propio PR.
- Nunca se suben `.env`, claves ni scripts `.ps1` locales.
