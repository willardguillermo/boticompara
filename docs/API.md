# Contrato de API — BotiCompara (backend Spring Boot)

Este archivo es el acuerdo entre el backend, la web y el móvil.
**Si un endpoint cambia, se actualiza aquí primero** (por Pull Request) y se avisa al grupo.

- URL base local: `http://localhost:8080/api`
- Desde el emulador de Android: `http://10.0.2.2:8080/api`
- Formato: JSON, nombres de campos en `camelCase`
- Autenticación: header `Authorization: Bearer <token>` en todo lo que diga 🔒
- CORS: permitir `http://localhost:5173` (web React con Vite)

## Roles

| Rol | Quién | Usa |
|---|---|---|
| `COMPRADOR` | Usuario que busca medicamentos | App móvil |
| `DUENO_BOTICA` | Dueño de una botica | Web React |
| Administrador | Aprueba boticas | Panel Django (`/admin`), **no usa esta API** |

## Formato de error (igual en todos los endpoints)

```json
{
  "status": 400,
  "error": "Bad Request",
  "mensaje": "El RUC no es válido",
  "timestamp": "2026-10-05T21:30:00Z"
}
```

| Código | Cuándo |
|---|---|
| 400 | Datos inválidos (validación) |
| 401 | Sin token, token vencido o credenciales incorrectas |
| 403 | Rol sin permiso, botica no aprobada intentando crear productos, acción no permitida por el estado de la botica, o reseña sin una reserva entregada (H85) |
| 404 | Recurso no existe o no pertenece al usuario |
| 409 | Duplicado (correo o RUC ya registrado), o conflicto con el estado actual: cambio de estado no permitido de una reserva o pedido, stock insuficiente, reserva activa repetida |
| 429 | Demasiados pedidos (solo `POST /chat`) |
| 503, 504 | El asistente no está disponible o tardó demasiado (solo `POST /chat`) |

Única excepción al formato JSON: `GET /auth/verificar` responde una página HTML (ver H20).

---

## 1. Autenticación

### `POST /auth/registro` — H1, H15, H20
Crea un comprador o un dueño de botica.

```json
// Request
{
  "nombre": "Carlos Ramírez",
  "correo": "carlos@boticompara.nissidev.dev",
  "password": "Boti2026!",
  "telefono": "954321098",
  "rol": "COMPRADOR"
}
```
```json
// 201 Created
{ "id": 4, "nombre": "Carlos Ramírez", "correo": "carlos@boticompara.nissidev.dev", "rol": "COMPRADOR" }
```
Validaciones: correo con formato válido, password de mínimo 8 caracteres, `rol` = `COMPRADOR` o `DUENO_BOTICA`. Nunca se piden datos de salud (H21).
Tras el registro se envía un correo con un enlace de verificación (H20). El login **no** exige tener el correo verificado.
Errores: 400, 409 (correo ya registrado).

### `POST /auth/login` — H18
```json
// Request
{ "correo": "carlos@boticompara.nissidev.dev", "password": "Boti2026!" }
```
```json
// 200 OK
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tipo": "Bearer",
  "usuario": { "id": 4, "nombre": "Carlos Ramírez", "correo": "carlos@boticompara.nissidev.dev", "rol": "COMPRADOR" }
}
```
El token dura 24 h. Errores: 401.

### `POST /auth/recuperar` — H17
Inicia la recuperación de contraseña. **Siempre responde 200**, exista o no el correo, para no revelar qué correos están registrados.
```json
// Request
{ "correo": "carlos@boticompara.nissidev.dev" }
```
```json
// 200 OK
{ "mensaje": "Si el correo está registrado, te enviamos las instrucciones para restablecer tu contraseña" }
```
Si el correo existe, se envía un mensaje con un código de 6 dígitos. El código vence a los 15 minutos, solo sirve una vez y se invalida tras 5 intentos fallidos. Si ya se pidió uno hace menos de un minuto, no se envía otro (la respuesta sigue siendo 200). Tampoco se genera otro código si ya se generaron 3 en la última hora (la respuesta también es 200).
Errores: 400 (falta el campo `correo`).

### `POST /auth/restablecer` — H17
```json
// Request
{ "correo": "carlos@boticompara.nissidev.dev", "token": "483920", "password": "NuevaClave2026!" }
```
```json
// 200 OK
{ "mensaje": "Contraseña actualizada" }
```
El código de 6 dígitos no es único entre usuarios, por eso se envía junto con el `correo`. Cada intento con un código incorrecto se cuenta; al quinto, el código queda invalidado y hay que pedir uno nuevo con `POST /auth/recuperar`. Al restablecer, los demás códigos pendientes de ese usuario quedan invalidados.
Errores: 400 (código inválido, vencido, ya usado o invalidado; o password de menos de 8 caracteres). El mensaje es el mismo en todos los casos de código inválido, para no revelar si el correo existe.

### `GET /auth/verificar?token=...` — H20
Es el enlace que llega por correo al registrarse. No requiere sesión. **Responde una página HTML, no JSON**, para que se pueda abrir en cualquier navegador.
- 200: página "Correo verificado". Marca `correoVerificado = true`.
- 400: página "El enlace no es válido o ya venció".

El token vence a las 48 horas.

---

## 2. Perfil

### `GET /usuarios/me` 🔒 — H16
```json
// 200 OK
{ "id": 4, "nombre": "Carlos Ramírez", "correo": "carlos@boticompara.nissidev.dev",
  "telefono": "954321098", "direccion": "Av. Lima 123, Chosica", "rol": "COMPRADOR",
  "correoVerificado": true, "notificacionesPrecio": true }
```
`correoVerificado` (D5): `true` si el usuario ya abrió el enlace de verificación del correo (H20). El login no lo exige.
`notificacionesPrecio` (H66): `true` si el usuario quiere recibir alertas de baja de precio. Por defecto `true`.

### `PUT /usuarios/me` 🔒 — H16
```json
// Request (el correo y el rol NO se editan aquí)
{ "nombre": "Carlos Ramírez", "telefono": "999888777", "direccion": "Jr. Ayacucho 45" }
```
Respuesta: 200 con el perfil completo actualizado (mismo formato que `GET /usuarios/me`, incluidos `correoVerificado` y `notificacionesPrecio`).
`notificacionesPrecio` **no** se cambia aquí, sino en `PUT /usuarios/me/notificaciones`.

### `PUT /usuarios/me/notificaciones` 🔒 — H66
```json
// Request
{ "notificacionesPrecio": false }
```
Respuesta: 200 con el perfil completo (mismo formato que `GET /usuarios/me`).
Con `false` no se crean alertas de baja de precio ni se envían sus correos; las alertas que ya tiene se conservan.
Con `true` vuelven a crearse desde la siguiente baja de precio (no se recuperan las de mientras estuvo apagado).
Errores: 400 (falta `notificacionesPrecio` o no es booleano), 401 (sin token).

### `DELETE /usuarios/me` 🔒 — H19
Elimina la cuenta del usuario logueado. Los datos se **anonimizan**, no se borra la fila:
- `nombre` pasa a `"Usuario eliminado"` y `correo` a `eliminado-{id}@boticompara.local`.
- `telefono` y `direccion` quedan en `null`, la contraseña deja de servir y `activo = false`.
- Si es dueño de botica, todos sus productos pasan a `activo = false` (dejan de aparecer en las búsquedas).
- Se borran todos sus favoritos (D1).
- Se borran todas sus alertas de precio (H59).

Respuesta: 204 sin cuerpo. El token usado deja de ser válido (las siguientes llamadas responden 401).

---

## 3. Botica (rol `DUENO_BOTICA`)

### `GET /boticas/validar-ruc/{ruc}` — H2
Primero valida el formato y el dígito verificador. Si pasan, consulta el RUC en SUNAT (servicio Decolecta) para obtener la razón social, el estado y la condición. La web lo usa para avisar mientras se escribe y para mostrar la razón social.
```json
// 200 OK — consultado en SUNAT
{ "ruc": "20601234565", "valido": true, "mensaje": "RUC válido",
  "razonSocial": "SALUD TOTAL FARMA S.A.C.", "estado": "ACTIVO", "condicion": "HABIDO", "fuente": "SUNAT",
  "direccion": "AV. LIMA SUR NRO. 450", "distrito": "LURIGANCHO" }
```
```json
// 200 OK — SUNAT no respondió: solo validación local
{ "ruc": "20601234565", "valido": true, "mensaje": "RUC válido (no se pudo consultar SUNAT)",
  "razonSocial": null, "estado": null, "condicion": null, "fuente": "LOCAL",
  "direccion": null, "distrito": null }
```
`valido` es `false`, con el motivo en `mensaje`, si el formato o el dígito verificador son incorrectos (no se consulta SUNAT), si el RUC no existe en SUNAT, o si `estado` no es `ACTIVO` o `condicion` no es `HABIDO`.

- `fuente`: `SUNAT` si se consultó; `LOCAL` si solo se validó el formato (RUC mal escrito, o SUNAT no respondió en 5 s o falló).
- Sin sesión de dueño de botica (sin token o con otro rol) solo se hace la validación local (`fuente: LOCAL`), para cuidar la cuota del servicio.
- `direccion` y `distrito`: domicilio **fiscal** que figura en SUNAT, no necesariamente el local de la botica. Son opcionales: `null` si SUNAT no los da o si `fuente` es `LOCAL`. La web los usa solo como sugerencia; `POST` y `PUT /boticas` siguen guardando la dirección que envía el dueño.
- Cada consulta a SUNAT se guarda 24 h en memoria

**Algoritmo del dígito verificador:**
1. 11 dígitos numéricos; los 2 primeros deben ser `10`, `15`, `17` o `20`.
2. Multiplicar los primeros 10 dígitos por los pesos `5, 4, 3, 2, 7, 6, 5, 4, 3, 2` y sumar.
3. `resto = 11 - (suma % 11)`; si `resto == 10` → `0`; si `resto == 11` → `1`.
4. El resultado debe ser igual al dígito 11.

### `POST /boticas` 🔒 — H1, H2, H3
Registra la botica del dueño logueado. Queda en estado `PENDIENTE`.
El RUC se verifica igual que en `validar-ruc`. Si SUNAT responde, se guarda su razón social (reemplaza la enviada); si no responde, se guarda la enviada.
```json
// Request
{
  "nombreComercial": "Botica Salud Total",
  "ruc": "20601234565",
  "razonSocial": "Salud Total Farma S.A.C.",
  "direccion": "Av. Lima Sur 450",
  "distrito": "Lurigancho-Chosica",
  "telefono": "014567890",
  "latitud": -11.942500,
  "longitud": -76.699000
}
```
```json
// 201 Created
{ "id": 1, "nombreComercial": "Botica Salud Total", "ruc": "20601234565",
  "razonSocial": "SALUD TOTAL FARMA S.A.C.", "estado": "PENDIENTE" }
```
`latitud` y `longitud` son opcionales (H3), pero si se envía una hay que enviar la otra. Rangos: latitud entre -90 y 90, longitud entre -180 y 180, hasta 6 decimales.
Errores: 400 (RUC inválido, inexistente en SUNAT, que no está `ACTIVO` o no está `HABIDO`; coordenadas inválidas o incompletas), 409 (RUC ya registrado o el dueño ya tiene botica).

### `GET /boticas/mia` 🔒 — H5
```json
// 200 OK
{
  "id": 1,
  "nombreComercial": "Botica Salud Total",
  "ruc": "20601234565",
  "razonSocial": "Salud Total Farma S.A.C.",
  "direccion": "Av. Lima Sur 450",
  "distrito": "Lurigancho-Chosica",
  "telefono": "014567890",
  "latitud": -11.942500,
  "longitud": -76.699000,
  "tieneLicencia": true,
  "estado": "RECHAZADO",
  "motivoRechazo": "La dirección no coincide con la ficha RUC"
}
```
`motivoRechazo` es `null` salvo cuando el estado es `RECHAZADO`. `latitud` y `longitud` son `null` si no se registraron. `tieneLicencia` indica si ya se subió el documento de licencia (H4); la ruta del archivo no se expone. Error: 404 si el dueño todavía no registró botica.

### `PUT /boticas/mia` 🔒 — H7
Corrige y reenvía una solicitud rechazada. **Solo se permite si el estado es `RECHAZADO`.** Al guardar, la botica vuelve a `PENDIENTE` y `motivoRechazo` se borra. La licencia ya subida se conserva.
Cuerpo igual al de `POST /boticas` (incluye `latitud` y `longitud` opcionales). El RUC se verifica en SUNAT con las mismas reglas.
Respuesta: 200 con el mismo formato que `GET /boticas/mia` (con `estado: "PENDIENTE"` y `motivoRechazo: null`).
Errores: 400 (datos inválidos), 403 (el estado no es `RECHAZADO`), 404 (no tiene botica), 409 (el RUC pertenece a otra botica).

### `POST /boticas/mia/licencia` 🔒 — H4
Sube la licencia de funcionamiento. Es `multipart/form-data` con un campo `archivo`.
- Formatos: PDF, JPG o PNG (se verifica el contenido, no solo la extensión).
- Tamaño máximo: 5 MB.
- Subir otro archivo reemplaza el anterior.

El archivo se guarda en Supabase Storage (bucket privado) y la ruta queda en `licencia_url`.
```json
// 200 OK
{ "tieneLicencia": true, "mensaje": "Licencia cargada correctamente" }
```
Errores: 400 (falta el archivo, formato no permitido o más de 5 MB), 404 (no tiene botica).

---

## 4. Catálogo (rol `DUENO_BOTICA`, solo sobre su propia botica)

Para crear productos la botica debe estar `APROBADO`; si no → 403.

### `GET /boticas/mia/productos?q=texto&stockBajo=true` 🔒 — H13, H14, H12
Sin `q` lista todo el catálogo activo; con `q` filtra por nombre comercial o principio activo (sin distinguir mayúsculas).
Con `stockBajo=true` devuelve solo los productos con `stock <= stockMinimo` (H12); se puede combinar con `q`.
```json
// 200 OK
[
  { "id": 1, "nombreComercial": "Panadol", "principioActivo": "Paracetamol",
    "presentacion": "Tableta 500 mg x 10", "precio": 8.50, "stock": 40, "stockMinimo": 5,
    "medicamentoId": 12 }
]
```
`medicamentoId` es `null` si el producto no está enlazado al catálogo maestro (H31).

### `GET /medicamentos?q=texto` 🔒 — H31
Busca en el catálogo maestro para el selector de la web al crear o editar un producto. `q` (mínimo 2 caracteres) se busca en el nombre comercial y en el principio activo, sin distinguir mayúsculas. Devuelve hasta 20 medicamentos, sin los ocultos, ordenados por nombre comercial.
```json
// 200 OK
[
  { "id": 12, "nombreComercial": "Panadol", "principioActivo": "Paracetamol",
    "presentacion": "Tableta", "concentracion": "500 mg" }
]
```
`concentracion` es `null` si no está registrada. Sin resultados → `200` con `[]`.
Errores: 400 (`q` de menos de 2 caracteres).

### `POST /productos` 🔒 — H8
```json
// Request
{ "nombreComercial": "Panadol", "principioActivo": "Paracetamol",
  "presentacion": "Tableta 500 mg x 10", "precio": 8.50, "stock": 40, "stockMinimo": 5,
  "medicamentoId": 12 }
```
Respuesta: 201 con el producto creado. Validaciones: `precio > 0`, `stock >= 0`, textos obligatorios. `stockMinimo` es opcional y debe ser `>= 0`: al crear queda en 5 si no se envía, y al editar con `PUT` se conserva el valor actual.
`medicamentoId` es opcional (H31): enlaza el producto con un medicamento del catálogo maestro, elegido con `GET /medicamentos`. Si el medicamento no existe o está oculto → 400.
**Regla D2:** mientras el producto esté enlazado, su `principioActivo` es el del medicamento. Al enlazar (en `POST` o `PUT` con `medicamentoId`) y también en un `PUT` sin `medicamentoId` sobre un producto ya enlazado, el backend guarda el nombre del principio activo del medicamento e ignora el texto enviado; la respuesta devuelve ese valor. Al quitar el enlace con `DELETE /productos/{id}/medicamento`, el producto conserva ese texto, y desde entonces se usa el que envía la web.

### `PUT /productos/{id}` 🔒 — H9
Mismo cuerpo que el POST. Si no se envía `medicamentoId`, se conserva el enlace actual (H31); para quitarlo se usa `DELETE /productos/{id}/medicamento` (enviar `null` no lo borra). Respuesta: 200 con el producto actualizado. 404 si no es de su botica.

**Stock comprometido (H79, H80):** el `stock` nuevo no puede quedar por debajo de las **unidades comprometidas** del producto (suma de `cantidad` de sus reservas `CONFIRMADA` y `LISTA`, ver sección 10) → 409 y el producto no cambia. El mensaje va en singular o plural según `n`: "Tienes 1 unidad comprometida en pedidos confirmados o listos para recoger; el stock no puede ser menor" o "Tienes {n} unidades comprometidas en pedidos confirmados o listos para recoger; el stock no puede ser menor". Ejemplo: un pedido `CONFIRMADA` de 2 y otro `LISTA` de 3 → comprometidas 5; se puede guardar stock 5 o más, pero no 4 (409 "Tienes 5 unidades comprometidas…"). Las reservas `PENDIENTE` no cuentan: el dueño todavía no las aceptó. Se calcula con la fila del producto bloqueada (`select ... from producto where id = :id for update`), igual que al confirmar un pedido, para que una edición y una confirmación a la vez no se crucen.

Errores:

| Código | Cuándo | `mensaje` |
|---|---|---|
| 400 | Las mismas validaciones del `POST` | Los mismos mensajes del `POST` |
| 401 | Sin token | El mismo que el resto de la API |
| 403 | Sin botica o botica no aprobada | El mismo que el `POST` |
| 404 | El producto no existe o no es de su botica | El mismo que el resto de la API |
| 409 | El `stock` nuevo es menor que las unidades comprometidas (`n`) | `n` = 1: "Tienes 1 unidad comprometida en pedidos confirmados o listos para recoger; el stock no puede ser menor". `n` > 1: "Tienes {n} unidades comprometidas en pedidos confirmados o listos para recoger; el stock no puede ser menor" |

### `DELETE /productos/{id}` 🔒 — H10
Baja lógica (`activo = false`): deja de aparecer en listados y búsquedas. Respuesta: 204 sin cuerpo.

**Pedidos activos (H79, H80):** si el producto tiene reservas `CONFIRMADA` o `LISTA` → 409 "No puedes dar de baja un producto con pedidos confirmados o listos para recoger. Atiéndelos o recházalos primero." y el producto sigue activo. Se revisa con la fila del producto bloqueada (`select ... from producto where id = :id for update`), igual que en `PUT /productos/{id}`, para que una baja y una confirmación a la vez no se crucen. Las reservas `PENDIENTE` **no** bloquean la baja, pero una vez dado de baja el producto ya no se pueden confirmar: `PATCH /pedidos/{id}/estado` a `CONFIRMADA` responde 409 "Este producto ya no está disponible en tu catálogo. Rechaza el pedido con un motivo." y el dueño las rechaza con un motivo (`PATCH /pedidos/{id}/rechazar`, sección 10). Ejemplo: un pedido `LISTA` de 1 → 409; solo un pedido `PENDIENTE` de 2 → 204, y ese pedido queda para rechazar.

Errores: 401 (sin token), 403 (sin botica o botica no aprobada), 404 (el producto no existe o es de otra botica), 409 (tiene reservas `CONFIRMADA` o `LISTA`).

### `DELETE /productos/{id}/medicamento` 🔒 — H31
Quita el enlace del producto con el catálogo maestro (`medicamentoId` pasa a `null`). El producto sigue activo y conserva su nombre, precio, stock y principio activo (el texto copiado al enlazar, regla D2). Respuesta: 204 sin cuerpo, también si el producto no estaba enlazado (se puede repetir sin error).
Sin enlace, el producto deja de depender del medicamento: sus alternativas salen por el mismo principio activo (`PRINCIPIO_ACTIVO`) y no lo afecta que ese medicamento esté oculto o con el registro vencido (H39).
Errores: 400 (`id` no numérico), 401 (sin token), 403 (sin botica o botica no aprobada), 404 (el producto no existe, está dado de baja o es de otra botica).

### `POST /productos/carga-masiva` 🔒 — H11
Carga o actualiza muchos productos con un archivo CSV o Excel (`.xlsx`). Es `multipart/form-data` con un campo `archivo`. Requiere botica `APROBADO` (si no → 403).

El tipo se detecta por el contenido del archivo, no por su extensión. Para los dos formatos: primera fila de encabezado, máximo 5 MB y 1000 filas de productos; las filas vacías no cuentan.

**CSV:** UTF-8, separador coma.
```
nombreComercial,principioActivo,presentacion,precio,stock,stockMinimo,medicamentoId
Panadol,Paracetamol,Tableta 500 mg x 10,8.50,40,5,12
Ibuprofeno Genérico,Ibuprofeno,Tableta 400 mg x 10,3.20,100,10,
```

**Excel (`.xlsx`):** las mismas columnas, en la primera hoja (también con `medicamentoId` al final, con valor o vacía). Las celdas pueden ser de texto o numéricas; de una fórmula se toma el valor calculado. Los `.xls` de Excel 97-2003 no se admiten: hay que guardarlos como `.xlsx` o como CSV UTF-8.

Las columnas `stockMinimo` y `medicamentoId` son opcionales. Las columnas se reconocen por su nombre en el encabezado, no por su posición.

**Reglas por fila:**
- Se validan los mismos campos que en `POST /productos`.
- Si la botica ya tiene un producto activo con el mismo `nombreComercial` y `presentacion` (sin distinguir mayúsculas), se **actualizan** su precio y su stock (y `stockMinimo` si la fila lo trae). Si no existe, se **crea**.
- Si el mismo producto aparece dos veces en el archivo, la segunda aparición se informa como error.
- Las filas válidas se guardan aunque otras tengan errores.
- **Stock comprometido (H79, H80):** al actualizar un producto existente, el `stock` de la fila no puede quedar por debajo de sus unidades comprometidas (reservas `CONFIRMADA` y `LISTA`, igual que en `PUT /productos/{id}`). Si pasa, esa fila se informa como error con el mismo mensaje, en singular o plural ("Tienes 1 unidad comprometida en pedidos confirmados o listos para recoger; el stock no puede ser menor" o "Tienes {n} unidades comprometidas en pedidos confirmados o listos para recoger; el stock no puede ser menor"), el producto no cambia y las demás filas se siguen procesando.
- `medicamentoId` enlaza el producto con un medicamento del catálogo maestro (H31), igual que en `POST /productos`.
- Si `medicamentoId` no es un número entero, el medicamento no existe o está oculto, esa fila se informa como error (con el mismo mensaje que da `POST /productos`) y las demás se siguen procesando.
- Si `medicamentoId` está vacío o el archivo no trae la columna, se conserva el enlace actual del producto: la carga masiva no quita enlaces (para eso se usa `DELETE /productos/{id}/medicamento`).
- **Regla D2:** al crear o actualizar un producto con `medicamentoId`, su `principioActivo` pasa a ser el del medicamento y se ignora el texto de la fila. Si el producto ya estaba enlazado a otro medicamento, se cambia el enlace.

```json
// 200 OK
{
  "creados": 12,
  "actualizados": 3,
  "errores": [
    { "fila": 7, "mensaje": "El precio debe ser mayor a 0." },
    { "fila": 9, "mensaje": "Producto duplicado en el archivo (ya aparece en la fila 2)." },
    { "fila": 11, "mensaje": "El medicamento no existe en el catálogo" },
    { "fila": 14, "mensaje": "Tienes 5 unidades comprometidas en pedidos confirmados o listos para recoger; el stock no puede ser menor" }
  ]
}
```
`fila` es el número de línea del CSV o el número de fila que muestra Excel (el encabezado es la fila 1).
Errores: 400 (archivo ausente o vacío, CSV que no está en UTF-8, `.xlsx` dañado, `.xls` antiguo, encabezado inválido o más de 1000 filas), 403 (botica no aprobada).

### Alerta diaria de stock bajo — H12
No es un endpoint. Todos los días a las 8:00 (hora de Lima) el backend envía al correo del dueño la lista de productos activos de su botica con `stock <= stockMinimo`. Si no hay ninguno, no se envía nada.

---

## 5. Búsqueda del comprador

### `GET /productos/buscar?q=paracetamol&tipo=nombre&orden=precio_asc` 🔒 — H22, H23, H24, H27, H48, H49

| Parámetro | Valores | Por defecto |
|---|---|---|
| `q` | texto a buscar (mínimo 2 caracteres) | obligatorio |
| `tipo` | `nombre` (H22) o `principio` (H23) | `nombre` |
| `orden` | `precio_asc` (H24), `precio_desc` o `distancia` (H27) | `distancia` si se envían `lat` y `lng`; si no, `precio_asc` |
| `lat`, `lng` | ubicación del comprador (H27); se envían juntas | sin ubicación |
| `radioKm` | solo boticas a esa distancia o menos (H27); mayor que 0 y hasta 50 | sin filtro; si llega vacío (`radioKm=`), 5 |

Solo devuelve productos activos, con stock > 0, de boticas `APROBADO` (vista `v_producto_busqueda`). Se excluyen los productos enlazados a un medicamento que el administrador ocultó o cuyo registro sanitario está vencido (H39); los productos sin enlazar al catálogo maestro sí se muestran.
```json
// 200 OK (con lat y lng)
[
  {
    "productoId": 9,
    "nombreComercial": "Paracetamol Genérico",
    "principioActivo": "Paracetamol",
    "presentacion": "Tableta 500 mg x 10",
    "precio": 2.20,
    "stock": 90,
    "boticaId": 2,
    "boticaNombre": "FarmaVida",
    "boticaDireccion": "Jr. Trujillo 210",
    "boticaDistrito": "Lurigancho-Chosica",
    "boticaLatitud": -11.937200,
    "boticaLongitud": -76.695800,
    "distanciaKm": 1.25
  }
]
```
Sin resultados → `200` con `[]` (la app muestra "No encontramos ese medicamento").

**Ubicación de la botica (H48, H49):**
- `boticaLatitud` y `boticaLongitud`: número con 6 decimales; `null` si la botica no registró ubicación. Van después de `boticaDistrito`.
- El mapa del móvil (H48) solo pone marcador a los resultados con `boticaLatitud` y `boticaLongitud` no nulos.

**Búsqueda por cercanía (H27):**
- Con `lat` y `lng`, cada resultado trae `distanciaKm`: la distancia en línea recta hasta la botica, con 2 decimales. Es `null` si no se envían `lat` y `lng` (la búsqueda funciona como antes) o si la botica no registró ubicación. El móvil la muestra con 1 decimal.
- `orden=distancia`: de la más cercana a la más lejana; a igual distancia, primero el más barato. Las boticas sin coordenadas van al final, con `distanciaKm: null`.
- Con `radioKm`, solo quedan las boticas a esa distancia o menos; las que no tienen coordenadas quedan fuera.

**Registro de la búsqueda (H62):** cada búsqueda que pasa las validaciones queda registrada para los reportes del administrador: el texto (`q` sin espacios al inicio ni al final y en minúsculas, hasta 150 caracteres), el `tipo`, la cantidad de resultados que se devuelven (ya con el filtro de radio) y el usuario de la sesión. No se guarda la ubicación del comprador. Las búsquedas que responden 400 no se registran. Si el registro falla, la respuesta no cambia.

Errores: 400 (`q` de menos de 2 caracteres; `tipo` u `orden` inválidos; solo una de `lat` y `lng`; coordenadas fuera de rango; `radioKm` inválido; `orden=distancia` o `radioKm` sin `lat` y `lng`).

### `GET /productos/{id}` — H28

Público (no necesita token). Devuelve el detalle de un producto que el comprador puede ver: activo, con stock > 0, de una botica `APROBADO` y sin medicamento oculto ni registro sanitario vencido (vista `v_producto_busqueda`).
```json
// 200 OK
{
  "productoId": 9,
  "nombreComercial": "Paracetamol Genérico",
  "principioActivo": "Paracetamol",
  "presentacion": "Tableta 500 mg x 10",
  "precio": 2.20,
  "stock": 90,
  "boticaId": 2,
  "boticaNombre": "FarmaVida",
  "boticaDireccion": "Jr. Trujillo 210",
  "boticaDistrito": "Lurigancho-Chosica",
  "boticaTelefono": "014567890"
}
```
`boticaTelefono` es `null` si la botica no lo registró.
Errores: 400 (`id` no numérico), 404 (el producto no existe, está dado de baja, no tiene stock, su botica no está aprobada, o su medicamento está oculto o con el registro sanitario vencido).

### `GET /productos/{id}/alternativas` — H36, H37, H38, H41, H42

Público (no necesita token). Devuelve los productos que el comprador puede usar en lugar del consultado, para comparar precios.
```json
// 200 OK
{
  "productoId": 7,
  "sinEquivalentes": false,
  "alternativas": [
    {
      "productoId": 13,
      "nombreComercial": "Paracetamol Genérico",
      "principioActivo": "Paracetamol",
      "presentacion": "Tableta 500 mg x 10",
      "precio": 2.20,
      "stock": 90,
      "boticaId": 2,
      "boticaNombre": "FarmaVida",
      "boticaDireccion": "Jr. Trujillo 210",
      "boticaDistrito": "Lurigancho-Chosica",
      "tipoCoincidencia": "EQUIVALENTE",
      "ahorroPorcentaje": 73.2
    }
  ]
}
```
**Cómo se buscan las alternativas (H36):**
- Si el producto está enlazado a un medicamento (H31) que pertenece a un grupo de equivalencia, las alternativas son los productos de los medicamentos de ese grupo: `tipoCoincidencia` = `EQUIVALENTE`.
- Si el producto no está enlazado, o su medicamento no está en ningún grupo, se usan los productos con el mismo principio activo, sin distinguir mayúsculas ni espacios de más: `tipoCoincidencia` = `PRINCIPIO_ACTIVO`.
- Solo salen productos que el comprador puede ver: activos, con stock > 0, de boticas `APROBADO` y sin medicamento oculto ni registro sanitario vencido (H38, H39). El producto consultado no aparece entre sus alternativas.
- Orden: de menor a mayor precio, y a igual precio por nombre de botica. Máximo 20 resultados.

**`ahorroPorcentaje` (H37):** `(precio consultado − precio de la alternativa) / precio consultado × 100`, con 1 decimal. Es `null` si la alternativa no es más barata que el consultado.

**`sinEquivalentes` (H41):** `true` cuando `alternativas` viene vacía (la app muestra que no hay equivalentes).

**Registro de la consulta (H42):** cada consulta de un producto visible queda registrada (producto, fecha y, si la petición trae un token válido, el usuario) para saber qué productos se comparan más. Sin sesión se guarda sin usuario. Si el registro falla, la respuesta no se ve afectada. Las consultas a un producto que no existe o no es visible (404) no se registran.

Errores: 400 (`id` no numérico), 404 (el producto consultado no existe o no es visible: los mismos casos que `GET /productos/{id}`).

---

## 6. Favoritos (cualquier usuario con sesión)

Productos que el comprador guarda para volver a verlos. Todos los endpoints piden token (cualquier rol) y cada usuario solo ve y modifica sus propios favoritos.

### `POST /favoritos` 🔒 — H43, H44
```json
// Request
{ "productoId": 9 }
```
```json
// 201 Created (200 OK si el producto ya estaba en los favoritos)
{
  "productoId": 9,
  "nombreComercial": "Paracetamol Genérico",
  "principioActivo": "Paracetamol",
  "presentacion": "Tableta 500 mg x 10",
  "precio": 2.20,
  "stock": 90,
  "boticaId": 2,
  "boticaNombre": "FarmaVida",
  "boticaDireccion": "Jr. Trujillo 210",
  "boticaDistrito": "Lurigancho-Chosica",
  "fecha": "2026-10-23T15:30:00.123456Z",
  "precioObjetivo": null
}
```
`precioObjetivo` (H60): precio en soles al que el usuario quiere que le avisen; `null` si no puso ninguno.
Solo se pueden guardar productos que el comprador ve hoy: activos, con stock, de boticas `APROBADO` y sin medicamento oculto ni registro sanitario vencido. Guardar dos veces el mismo producto no lo duplica.
Errores: 400 (falta `productoId`), 401 (sin token), 404 (el producto no existe o no se ve).

### `GET /favoritos` 🔒 — H43, H44
Devuelve los favoritos del usuario, del más reciente al más antiguo, con los mismos campos de la búsqueda (sin `distanciaKm`) y la `fecha` en que se guardó. Si un producto deja de verse (sin stock, de baja, oculto o con registro vencido) no aparece en la lista, pero su favorito se conserva: si vuelve a estar disponible, reaparece.
```json
// 200 OK
[
  { "productoId": 9, "nombreComercial": "Paracetamol Genérico", "principioActivo": "Paracetamol",
    "presentacion": "Tableta 500 mg x 10", "precio": 2.20, "stock": 90, "boticaId": 2,
    "boticaNombre": "FarmaVida", "boticaDireccion": "Jr. Trujillo 210",
    "boticaDistrito": "Lurigancho-Chosica", "fecha": "2026-10-23T15:30:00.123456Z",
    "precioObjetivo": null }
]
```
`precioObjetivo` (H60): precio en soles al que el usuario quiere que le avisen; `null` si no puso ninguno.
Sin favoritos → `200` con `[]`.
Errores: 401 (sin token).

### `PUT /favoritos/{productoId}/precio-objetivo` 🔒 — H60
```json
// Request: poner o cambiar
{ "precioObjetivo": 7.50 }
// Request: quitar
{ "precioObjetivo": null }
```
Respuesta: 200 con el favorito (mismo formato que `GET /favoritos`).
- El cuerpo debe traer el campo `precioObjetivo`: si falta (`{}` o sin cuerpo) responde 400. Para quitar el objetivo se envía `null` explícito.
- `precioObjetivo` debe ser mayor que 0, con máximo 2 decimales y menor que 100000.
- Puede ser mayor o igual al precio actual: la alerta llega en la siguiente baja que lo cumpla.
- Cada vez que se pone, cambia o quita, se reinicia el registro del último precio avisado, para que la siguiente baja que cumpla la regla vuelva a avisar.
- Si el producto está en sus favoritos pero ya no se ve (inactivo, sin stock, botica no aprobada, medicamento oculto o registro vencido), responde 404 con el mensaje "El producto no está disponible", con el mismo criterio de visibilidad que `POST /favoritos`.

Errores: 400 (falta `precioObjetivo` o valor inválido), 401 (sin token), 404 (no está en sus favoritos o ya no se ve).

### `DELETE /favoritos/{productoId}` 🔒 — H43, H44
Quita el producto de los favoritos. Responde `204` sin cuerpo, también si el producto no estaba guardado.
Errores: 400 (`productoId` no numérico), 401 (sin token).

---

## 7. Alertas de precio (cualquier usuario con sesión)

Avisos de que bajó el precio de un producto que el usuario tiene en favoritos (H59, H61). Se ven en la pantalla "Alertas" de la app y llegan por correo. No hay notificaciones push en este sprint.

### Cuándo se crea una alerta — H59, H60, H61, H66
No es un endpoint. Se evalúa cada vez que cambia el precio de un producto, en `PUT /productos/{id}` y en `POST /productos/carga-masiva`:
1. Solo si el precio **baja** (nuevo < anterior). Si sube o no cambia, no se crea nada.
2. Solo si el producto se ve en la búsqueda **después del cambio**: activo, con stock, de botica `APROBADO`, sin medicamento oculto ni registro vencido. Si el mismo `PUT` baja el precio y deja el stock en 0, no hay alerta.
3. Para cada usuario que lo tiene en favoritos, **activo** y con `notificacionesPrecio = true`:
   - **Sin precio objetivo:** se crea una alerta en cada baja.
   - **Con precio objetivo:** solo si el precio nuevo es **menor o igual** al objetivo y menor que el último precio que ya se le avisó (o si todavía no se le avisó nada).
4. Al crear la alerta se guarda el precio nuevo como último precio avisado de ese favorito.

**Correo (H61):** se envía después de guardar el cambio de precio y en segundo plano, así que no hace más lenta la respuesta. Uno por alerta en `PUT /productos/{id}`. En la carga masiva, **un solo correo por usuario** con todas sus bajas. Asunto de ejemplo: "Panadol bajó a S/ 7.50 en FarmaVida". Si el correo falla, la alerta igual queda guardada.

Ejemplo: Carlos guarda Panadol de FarmaVida (S/ 7.90) con precio objetivo S/ 7.50. Si FarmaVida lo baja a S/ 7.70 no hay alerta (no llega al objetivo); si lo baja a S/ 7.50 se crea la alerta y le llega el correo; si luego vuelve a S/ 7.50 desde un precio mayor, no se repite el aviso (no es menor que el último avisado).

### `GET /alertas?soloNoLeidas=true` 🔒 — H59
Alertas del usuario, de la más reciente a la más antigua (máximo 100). `soloNoLeidas` es opcional (por defecto `false`).
```json
// 200 OK
[
  {
    "id": 31,
    "productoId": 14,
    "nombreComercial": "Panadol",
    "presentacion": "Tableta 500 mg x 10",
    "boticaNombre": "FarmaVida",
    "precioAnterior": 7.90,
    "precioNuevo": 7.50,
    "ahorro": 0.40,
    "leida": false,
    "fecha": "2026-11-05T14:20:00.123456Z"
  }
]
```
`ahorro` = `precioAnterior - precioNuevo`. La alerta se conserva aunque después el producto deje de verse; para abrir su detalle se usa `GET /productos/{productoId}`, que responde 404 si ya no se ve. Sin alertas → `200` con `[]`.
Errores: 400 (`soloNoLeidas` no es `true` ni `false`), 401 (sin token).

### `GET /alertas/resumen` 🔒 — H59
Para el número sobre el ícono de Alertas.
```json
// 200 OK
{ "noLeidas": 2 }
```
Errores: 401 (sin token).

### `PUT /alertas/{id}/leida` 🔒 — H59
Marca una alerta como leída. Responde `204` sin cuerpo, también si ya estaba leída.
Errores: 400 (`id` no numérico), 401 (sin token), 404 (la alerta no existe o es de otro usuario).

### `PUT /alertas/leidas` 🔒 — H59
Marca como leídas todas las alertas del usuario. Responde `204` sin cuerpo.
Errores: 401 (sin token).

---

## 8. Asistente (chat)

Asistente de texto que responde preguntas sobre BotiCompara usando los mismos datos que el resto de la API. El backend le pasa la pregunta a Gemini (Google) y ejecuta por él las consultas que necesite ("herramientas"). El diseño completo está en `docs/chat/diseno-chat.md`.

### `POST /chat` 🔒 — H68

Roles: `COMPRADOR` y `DUENO_BOTICA`. El rol del token decide qué herramientas tiene el asistente (ver más abajo). El servidor no guarda la conversación: el cliente envía el historial en cada pedido.

```json
// Pedido
{
  "mensaje": "¿Dónde está más barato el paracetamol?",
  "historial": [
    { "rol": "usuario",   "texto": "Hola" },
    { "rol": "asistente", "texto": "Hola, soy el asistente de BotiCompara. ¿Qué medicamento buscas?" }
  ],
  "lat": -11.936500,
  "lng": -76.697200
}
```

| Campo | Obligatorio | Validación |
|---|---|---|
| `mensaje` | sí | Texto de 1 a 500 caracteres, sin contar espacios al inicio ni al final |
| `historial` | no | Lista de mensajes anteriores, del más antiguo al más reciente. Cada uno con `rol` (`usuario` o `asistente`) y `texto` (1 a 1000 caracteres). Si llegan más de 10, el servidor usa solo los 10 últimos. Sin historial: se omite, `null` o `[]` |
| `lat`, `lng` | no | Ubicación del usuario; se envían juntas, con los mismos rangos que en `GET /productos/buscar`. Solo sirven para calcular distancias en el servidor: **no se envían a Gemini ni se guardan** |

El cliente arma el historial con lo que ya mostró en pantalla: los `mensaje` enviados (`usuario`) y las `respuesta` recibidas (`asistente`). Los `productos` de respuestas anteriores no se reenvían.

```json
// 200 OK — comprador (con lat y lng)
{
  "respuesta": "El paracetamol más barato es el Paracetamol Genérico de FarmaVida, a 2 soles con 20, a poco más de un kilómetro de ti. También lo tiene Botica Salud Total a 2 soles con 50.",
  "productos": [
    {
      "productoId": 9,
      "nombreComercial": "Paracetamol Genérico",
      "principioActivo": "Paracetamol",
      "presentacion": "Tableta 500 mg x 10",
      "precio": 2.20,
      "stock": 90,
      "stockMinimo": null,
      "boticaId": 2,
      "boticaNombre": "FarmaVida",
      "boticaDistrito": "Lurigancho-Chosica",
      "distanciaKm": 1.25
    },
    {
      "productoId": 3,
      "nombreComercial": "Paracetamol MK",
      "principioActivo": "Paracetamol",
      "presentacion": "Tableta 500 mg x 10",
      "precio": 2.50,
      "stock": 35,
      "stockMinimo": null,
      "boticaId": 1,
      "boticaNombre": "Botica Salud Total",
      "boticaDistrito": "Lurigancho-Chosica",
      "distanciaKm": 2.10
    }
  ],
  "respaldo": false
}
```

```json
// 200 OK — dueño ("¿Qué productos tengo con stock bajo?")
{
  "respuesta": "Tienes 2 productos con stock bajo: Amoxicilina 500 miligramos, con 3 unidades, y Loratadina, con 5 unidades. Te conviene reponerlos pronto.",
  "productos": [
    {
      "productoId": 4,
      "nombreComercial": "Amoxicilina",
      "principioActivo": "Amoxicilina",
      "presentacion": "Cápsula 500 mg x 10",
      "precio": 6.00,
      "stock": 3,
      "stockMinimo": 5,
      "boticaId": 1,
      "boticaNombre": "Botica Salud Total",
      "boticaDistrito": "Lurigancho-Chosica",
      "distanciaKm": null
    }
  ],
  "respaldo": false
}
```

```json
// 200 OK — pregunta fuera de alcance ("Me duele la cabeza, ¿qué tomo?")
{
  "respuesta": "No puedo recomendarte medicamentos ni dosis. Consulta con el químico farmacéutico de tu botica o con tu médico. Si es una emergencia, llama al SAMU al 106. Si ya sabes qué medicamento necesitas, te ayudo a encontrar dónde está más barato.",
  "productos": [],
  "respaldo": false
}
```

```json
// 200 OK — respuesta de respaldo (las herramientas devolvieron 3 productos, pero la llamada final a Gemini falló o no llegó a tiempo)
{
  "respuesta": "Encontré 3 opciones. La más barata es Paracetamol Genérico a 2 soles con 20 en FarmaVida, Lurigancho-Chosica.",
  "productos": [ /* las 3 tarjetas, con las mismas reglas de siempre */ ],
  "respaldo": true
}
```

**`respuesta`:** texto plano en español, sin markdown, sin listas ni enlaces, de hasta 600 caracteres. Está pensada para leerse en voz alta (H71): los precios van escritos como se dicen.

**`respaldo`:** `false` en una respuesta normal. `true` cuando el texto no lo escribió Gemini sino el servidor, porque la llamada final falló o no llegó a tiempo (ver "Tiempos y respuesta de respaldo").

**`productos`:** siempre es una lista (vacía si la respuesta no trata de productos). Sirve para que la web y la app muestren tarjetas con enlace al detalle.
- La arma **el servidor** con lo que devolvieron las herramientas en esta pregunta, no el modelo: los ids, precios y stock son los reales aunque el texto se equivoque. Si el texto y una tarjeta no coinciden, vale la tarjeta.
- Máximo 10, sin repetidos, en el orden en que los devolvieron las herramientas.
- `stockMinimo` solo tiene valor en los productos propios del dueño (herramientas `ver_stock_bajo` y `buscar_en_mi_catalogo`); en los demás es `null`.
- `distanciaKm` solo tiene valor si el pedido trae `lat` y `lng` y la botica registró ubicación; si no, `null`. Mismo cálculo que en `GET /productos/buscar`.
- Los resultados de `buscar_medicamento` (catálogo maestro) solo aparecen en el texto: no generan tarjetas ni una lista aparte, porque no son productos de una botica.

**Herramientas del asistente, según el rol del token.** El modelo solo decide los parámetros de la tabla. El usuario, su botica y la ubicación los pone el servidor a partir del token y del pedido, nunca el modelo.

| Herramienta | Rol | Para qué sirve | Parámetros que da el modelo | Servicio que usa |
|---|---|---|---|---|
| `buscar_precios` | `COMPRADOR`, `DUENO_BOTICA` | Buscar un medicamento en las boticas aprobadas y comparar precios | `texto` (mínimo 2 caracteres), `tipo` (`nombre` o `principio`), `cerca` (`true` = de la botica más cercana a la más lejana; solo si el pedido trae ubicación) | `BusquedaService.buscar` (`GET /productos/buscar`, vista `v_producto_busqueda`) |
| `buscar_alternativas` | `COMPRADOR`, `DUENO_BOTICA` | Equivalentes más baratos de un producto que ya salió en una búsqueda | `productoId` | `AlternativasService.alternativas` (`GET /productos/{id}/alternativas`) |
| `ver_stock_bajo` | `DUENO_BOTICA` | Productos de su botica con `stock <= stockMinimo` | ninguno | `CatalogoService.listar(usuarioId, null, true)` (`GET /boticas/mia/productos?stockBajo=true`) |
| `buscar_en_mi_catalogo` | `DUENO_BOTICA` | Buscar un producto en su propio catálogo (precio y stock) | `texto` | `CatalogoService.listar(usuarioId, texto, false)` (`GET /boticas/mia/productos?q=`) |
| `buscar_medicamento` | `DUENO_BOTICA` | Buscar en el catálogo maestro (DIGEMID) | `texto` (mínimo 2 caracteres) | `MedicamentoService.buscar` (`GET /medicamentos?q=`) |

- Cada herramienta devuelve al modelo como máximo 5 resultados, solo con datos de productos y boticas (nombre, presentación, precio, stock, botica, distrito y distancia en km). Nunca coordenadas ni datos de personas.
- Por pregunta hay como máximo 2 rondas de llamadas a herramientas (en una ronda el modelo puede pedir una o varias a la vez). Si después de la segunda pide más, el servidor le exige responder con lo que ya tiene. Así una pregunta usa como máximo 3 llamadas a Gemini: 2 rondas de herramientas más la respuesta.
- Si el dueño aún no registró su botica, sus herramientas devuelven "sin botica registrada" y el asistente lo explica: no es un error.
- Las búsquedas del asistente **no** se registran en los reportes de búsquedas (H62) ni en el de comparaciones (H42): el texto lo escribe el modelo, no el usuario.

**Límites del asistente:**
- Solo responde sobre BotiCompara: precios, disponibilidad, boticas, alternativas y, para el dueño, su catálogo.
- No diagnostica, no da dosis y no recomienda medicamentos por síntomas: deriva al químico farmacéutico o al médico. Ante una emergencia indica llamar al SAMU (106).
- Estos casos responden `200` con `productos: []`; no son errores.

**Límite de uso:**
- **Por usuario:** por defecto 3 preguntas por minuto y 15 por día. Al pasarlo → 429.
- **Global, por backend:** por defecto 150 preguntas por día entre todos los usuarios (unas 450 llamadas a Gemini en el peor caso, con margen bajo los 500 pedidos por día de `gemini-3.5-flash-lite`). Al llegar al tope → 503 con "El asistente alcanzó su límite de hoy. Intenta mañana.", **sin llamar a Gemini**. Existe porque la cuota de Gemini es por proyecto, no por usuario: sin este tope, unos pocos usuarios intensivos podrían agotarla para todos.
- Primero se revisa el límite por usuario (429) y después el global (503). Los pedidos que responden 400 o 401 no cuentan en ninguno.
- **Reinicio diario:** el límite propio (por usuario y global) se reinicia a medianoche de Lima. La cuota de Google se reinicia a medianoche del Pacífico: 08:00 UTC (3:00 a. m. en Lima) de noviembre a marzo, con el horario estándar de EE. UU., y 07:00 UTC (2:00 a. m. en Lima) de marzo a noviembre, con su horario de verano. Lima no cambia de hora (UTC−5).
- Un reintento por 500 o 503 de Google (ver abajo) es un pedido más a Gemini. Es raro y el tope global deja margen: 150 preguntas × 3 llamadas = 450, 50 por debajo de los 500 del día.
- Los tres valores son configurables en el backend: `chat.limite.por-minuto`, `chat.limite.por-dia` y `chat.limite.global-por-dia`. Con 3 llamadas a Gemini como máximo por pregunta, un usuario usa como máximo 9 de los 15 pedidos por minuto del plan gratis de `gemini-3.5-flash-lite`.

**Tiempos y respuesta de respaldo:**
- **Por llamada a Gemini:** plazo de 25 segundos. **Por pregunta completa:** plazo total de 50 segundos, contado desde que el servidor empieza a atender el pedido. Cada llamada espera lo que sea menor: 25 s o lo que falte para los 50 s.
- **Reintento:** si Google responde 500 o 503, la llamada se reintenta **una sola vez**, y solo si todavía queda tiempo dentro de los 50 s. Otros errores de Google (por ejemplo, cuota agotada) no se reintentan.
- **Respaldo:** si las herramientas ya devolvieron productos en esta pregunta, pero la llamada final a Gemini falla (también después del reintento) o no llega a tiempo, el servidor responde `200` con un texto armado por él a partir de esas tarjetas y `"respaldo": true`. Ejemplo: "Encontré 3 opciones. La más barata es Paracetamol Genérico a 2 soles con 20 en FarmaVida, Lurigancho-Chosica." Sigue las reglas de `respuesta` (texto plano, precios como se dicen, hasta 600 caracteres).
- El texto de respaldo **solo describe productos** (cuántos hay, el más barato, la botica y el distrito): nunca da consejos de salud.
- Si no hubo productos de herramientas, no hay respaldo: se responde 503 si Gemini falla y 504 si se acaba el tiempo, como antes.
- **Clientes:** la web (`fetch`) y la app (`readTimeout` de Retrofit) deben esperar **al menos 60 segundos** la respuesta de `POST /chat`, más que los 50 s del servidor, y mostrar un indicador ("Escribiendo…") mientras tanto. Con el plazo por defecto del cliente, el pedido podría cortarse antes de que el servidor responda.

Errores:

| Código | Cuándo | `mensaje` |
|---|---|---|
| 400 | `mensaje` vacío o solo espacios | "Escribe tu pregunta" |
| 400 | `mensaje` de más de 500 caracteres | "La pregunta no puede pasar de 500 caracteres" |
| 400 | `historial` con un `rol` distinto de `usuario` o `asistente`, o un `texto` vacío o de más de 1000 caracteres | "El historial no es válido" |
| 400 | Solo una de `lat` y `lng`, o coordenadas fuera de rango | Los mismos mensajes que `GET /productos/buscar` |
| 401 | El token no es válido o la cuenta no está habilitada (dada de baja o suspendida) | El mismo que el resto de la API |
| 429 | Se pasó el límite por minuto o por día | "Hiciste muchas preguntas seguidas. Espera un minuto y vuelve a intentar" o "Llegaste al límite de preguntas de hoy. Vuelve mañana" |
| 503 | Se llegó al tope global del día (`chat.limite.global-por-dia`); no se llama a Gemini | "El asistente alcanzó su límite de hoy. Intenta mañana." |
| 503 | Gemini no responde, devuelve un error (también después del reintento), se agotó la cuota gratis del proyecto, o falta `GEMINI_API_KEY`, y no hay productos de herramientas para el respaldo | "El asistente no está disponible en este momento. Intenta más tarde" |
| 504 | Se pasan los 50 segundos de la pregunta y no hay productos de herramientas para el respaldo | "El asistente tardó demasiado en responder. Intenta de nuevo" |

- El 429 trae el header `Retry-After` con los segundos que faltan para poder preguntar otra vez.
- **Cuentas suspendidas:** el 401 para una cuenta suspendida depende de que `JwtAuthFilter` también rechace `suspendido_hasta > now()`. Ese cambio es parte de H91 (Alexander, anotado como pendiente en el PR #79) y es requisito de este contrato. Mientras no esté, un JWT vigente de una cuenta suspendida sigue entrando, como en el resto de la API.
- Salvo el tope global, el 503 y el 504 no dicen la causa interna (clave, cuota, modelo): eso va solo al log del servidor.

**Configuración (backend):**
- `GEMINI_API_KEY` y `GEMINI_MODEL` en el `.env` de `backend-usuario/`. Cada integrante usa su propia clave de Google AI Studio; nunca se sube al repositorio.
- Valor recomendado de `GEMINI_MODEL`: `gemini-3.5-flash-lite`. Alternativa, solo si la calidad no alcanza: `gemini-3.8-flash` (unas 7 preguntas al día).
- Cuotas comprobadas el 12/11/2026 en AI Studio (plan gratuito): `gemini-3.5-flash-lite` 15 RPM, 250K TPM y 500 RPD; `gemini-3.8-flash` 5 RPM, 250K TPM y 20 RPD. Las cuotas son por proyecto de Google AI Studio, no por clave ni por usuario: varias claves del mismo proyecto comparten la misma cuota. Cada integrante usa su propio proyecto y debe revisar sus cuotas en <https://aistudio.google.com/rate-limit>.
- `chat.limite.por-minuto` (por defecto 3), `chat.limite.por-dia` (por defecto 15) y `chat.limite.global-por-dia` (por defecto 150).
- El `.env.example` y la colección de Postman se actualizan en el PR del módulo.

---

## 9. Reservas del comprador (rol `COMPRADOR`)

El comprador aparta un producto para recogerlo en la botica. La reserva **aparta unidades pero no descuenta el stock**: mientras está activa resta de la cantidad disponible, y el stock se descuenta recién cuando el dueño la entrega (sección 10).

**Cantidad disponible** de un producto = `producto.stock` − la suma de `cantidad` de sus reservas activas (`PENDIENTE`, `CONFIRMADA` y `LISTA`). Ejemplo: stock 5 y una reserva `CONFIRMADA` de 2 → disponible 3. Al entregarse esa reserva, el stock baja a 3 y la reserva deja de estar activa: el disponible sigue en 3. Nunca es negativo: si la resta da menos de 0, se toma 0. Se calcula con `select coalesce(sum(cantidad), 0) from reserva where producto_id = :id and estado in ('PENDIENTE', 'CONFIRMADA', 'LISTA')` (índice `idx_reserva_producto`). Todos los endpoints piden token de `COMPRADOR`; con otro rol responden 403. El usuario sale siempre del token (`UsuarioAutenticado.id`), nunca del cuerpo.

**Estados de una reserva** (`chk_reserva_estado` en `reserva`):

| Estado | Significa | Quién la deja así |
|---|---|---|
| `PENDIENTE` | Recién creada, la botica aún no la vio | Comprador (`POST /reservas`) |
| `CONFIRMADA` | La botica la aceptó y la está preparando | Dueño |
| `LISTA` | Lista para recoger (48 h de plazo) | Dueño |
| `ENTREGADA` | El comprador la recogió; se descontó el stock | Dueño |
| `RECHAZADA` | La botica no puede atenderla (con motivo) | Dueño |
| `CANCELADA` | El comprador ya no la quiere | Comprador |
| `VENCIDA` | Pasó `venceEn` sin entregarse | Proceso programado (H84) |

`ENTREGADA`, `RECHAZADA`, `CANCELADA` y `VENCIDA` son finales: ya no cambian.

**Formato de una reserva** (lo devuelven todos los endpoints de esta sección):
```json
{
  "id": 15,
  "codigo": "BC-7K2QXM",
  "estado": "PENDIENTE",
  "productoId": 9,
  "nombreComercial": "Paracetamol Genérico",
  "presentacion": "Tableta 500 mg x 10",
  "cantidad": 2,
  "precioUnitario": 2.20,
  "total": 4.40,
  "boticaId": 2,
  "boticaNombre": "FarmaVida",
  "boticaDireccion": "Jr. Trujillo 210",
  "boticaDistrito": "Lurigancho-Chosica",
  "boticaTelefono": "014567890",
  "motivoRechazo": null,
  "venceEn": "2026-11-13T15:30:00.123456Z",
  "creadoEn": "2026-11-12T15:30:00.123456Z",
  "actualizadoEn": "2026-11-12T15:30:00.123456Z"
}
```
- `precioUnitario`: el precio del producto **al momento de reservar** (`reserva.precio_unitario`). Si después la botica cambia el precio, la reserva conserva el suyo.
- `total` = `cantidad × precioUnitario`, con 2 decimales. No se guarda: se calcula al responder.
- Los datos del producto y la botica se leen de las tablas `producto` y `botica`, no de la vista: una reserva se sigue viendo aunque el producto se quede sin stock o se dé de baja.
- `boticaTelefono` es `null` si la botica no lo registró. `motivoRechazo` es `null` salvo en `RECHAZADA`.
- Fechas en UTC con el mismo formato que el resto de la API.

### `POST /reservas` 🔒 — H72, H73, H74
```json
// Request
{ "productoId": 9, "cantidad": 2 }
```
Respuesta: 201 con la reserva (formato de arriba), en estado `PENDIENTE`.

Validaciones y reglas:
- `productoId` obligatorio. El producto debe verse en `v_producto_busqueda` con el mismo criterio que `GET /productos/{id}` (activo, con stock, botica `APROBADO`, dueño no suspendido ni dado de baja, sin medicamento oculto ni registro vencido). Si no → 404 "El producto no está disponible".
- `cantidad` obligatoria, entero de **1 a 10** → si no, 400.
- `cantidad` no puede pasar de la cantidad disponible (stock menos reservas activas) → 409 "Solo hay {disponible} unidades disponibles de este producto". Si el disponible es 0 → 409 "Todas las unidades de este producto ya están reservadas".
- **Sin carreras:** dentro de la misma transacción, primero se bloquea la fila del producto (`select ... from producto where id = :id for update`), luego se calcula el disponible y se inserta la reserva. Si dos compradores reservan las últimas unidades a la vez, el segundo espera al primero y ve su reserva en la suma, así que recibe el 409.
- Un comprador no puede tener dos reservas activas (`PENDIENTE`, `CONFIRMADA` o `LISTA`) del mismo producto → 409 "Ya tienes una reserva activa de este producto" (propuesta, ver decisiones).
- `precio_unitario` se copia de `producto.precio` en ese momento.
- `estado = PENDIENTE` y `vence_en = ahora + 24 h`. Confirmar la reserva **no** amplía ese plazo; solo pasar a `LISTA` lo cambia (sección 10).
- **Código de confirmación:** `BC-` seguido de 6 caracteres tomados de `23456789ABCDEFGHJKLMNPQRSTUVWXYZ` (sin `0`, `O`, `1` ni `I`, para que no se confundan al dictarlo). Se genera con `SecureRandom`; si choca con `uq_reserva_codigo` se genera otro (hasta 3 intentos). Hay 32⁶ ≈ 1 070 millones de combinaciones. El comprador lo muestra al recoger.
- Al crearse, se avisa por correo al dueño de la botica (ver "Correos" en la sección 10).

Errores: 400 (falta `productoId` o `cantidad`, o `cantidad` fuera de 1 a 10), 401 (sin token), 403 (el rol no es `COMPRADOR`), 404 (el producto no existe o no se ve), 409 (cantidad mayor que la disponible, o ya tiene una reserva activa de ese producto).

### `GET /reservas/mias?estado=PENDIENTE` 🔒 — H76
Historial de reservas del comprador, de la más reciente a la más antigua (por `creadoEn`), máximo 100. `estado` es opcional: sin él devuelve todas.
```json
// 200 OK
[
  { "id": 15, "codigo": "BC-7K2QXM", "estado": "PENDIENTE", "productoId": 9,
    "nombreComercial": "Paracetamol Genérico", "presentacion": "Tableta 500 mg x 10",
    "cantidad": 2, "precioUnitario": 2.20, "total": 4.40, "boticaId": 2,
    "boticaNombre": "FarmaVida", "boticaDireccion": "Jr. Trujillo 210",
    "boticaDistrito": "Lurigancho-Chosica", "boticaTelefono": "014567890",
    "motivoRechazo": null, "venceEn": "2026-11-13T15:30:00.123456Z",
    "creadoEn": "2026-11-12T15:30:00.123456Z", "actualizadoEn": "2026-11-12T15:30:00.123456Z" }
]
```
Sin reservas → `200` con `[]`.
Errores: 400 (`estado` no es uno de los 7 de la tabla), 401 (sin token), 403 (el rol no es `COMPRADOR`).

### `GET /reservas/{id}` 🔒 — H76
Detalle de una reserva propia. Respuesta: 200 con la reserva.
Errores: 400 (`id` no numérico), 401 (sin token), 403 (el rol no es `COMPRADOR`), 404 (no existe o es de otro comprador; mismo mensaje en los dos casos).

### `PATCH /reservas/{id}/cancelar` 🔒 — H75
Sin cuerpo. Pasa la reserva a `CANCELADA`. Respuesta: 200 con la reserva actualizada.
- Solo desde `PENDIENTE`, `CONFIRMADA` o `LISTA`. Desde cualquier otro estado → 409 "No se puede cancelar una reserva {estado}".
- Si `venceEn` ya pasó (aunque el proceso de H84 todavía no la haya marcado) → 409 "La reserva ya venció".
- No se descuenta ni se devuelve stock (nunca se descontó). Como la reserva deja de estar activa, sus unidades vuelven a la cantidad disponible.
- No se envía correo (ver "Correos" en la sección 10).

Errores: 400 (`id` no numérico), 401 (sin token), 403 (el rol no es `COMPRADOR`), 404 (no existe o es de otro comprador), 409 (estado que no se puede cancelar o reserva vencida).

### Decisiones a confirmar con Alexander (sección 9)
1. **Formato del código:** `BC-` + 6 caracteres sin `0/O/1/I` (cabe en `varchar(20)`), con hasta 3 reintentos si se repite.
2. **Plazo de 24 h** desde la creación para `PENDIENTE` y `CONFIRMADA`; confirmar no lo amplía.
3. **Cantidad máxima de 10** unidades por reserva.
4. **Una sola reserva activa por producto** y comprador (409 si intenta otra).
5. **La reserva aparta unidades:** disponible = stock − reservas activas (`PENDIENTE`, `CONFIRMADA`, `LISTA`). Se valida al crear (con la fila del producto bloqueada con `for update`) y al confirmar (sección 10); el stock se descuenta recién al entregar.
6. **Reserva con `venceEn` pasado y aún sin marcar** → se trata como vencida (409 al cancelar o cambiar de estado).
7. **Máximo 100** en el historial, sin paginación (como `GET /alertas`).

---

## 10. Pedidos del dueño (rol `DUENO_BOTICA`)

Las reservas vistas desde la botica. Todos los endpoints piden token de `DUENO_BOTICA` (otro rol → 403). La botica sale del token: es la botica cuyo `usuario_id` es el del token. Solo se ven y cambian reservas de productos de **esa** botica (`reserva.producto_id → producto.botica_id`). Si el dueño todavía no registró botica → 404 "Todavía no registraste tu botica" (como `GET /boticas/mia`).

**Unidades comprometidas** de un producto = la suma de `cantidad` de sus reservas `CONFIRMADA` y `LISTA`. Son las que el dueño ya aceptó: **el dueño no puede comprometer stock que no tiene**, porque aceptar un pedido sin stock sería engañar al comprador. Por eso:
- Solo se puede confirmar un pedido que el stock alcanza a cubrir (ver `PATCH /pedidos/{id}/estado`).
- El stock de un producto nunca puede quedar por debajo de sus unidades comprometidas, ni al editarlo (`PUT /productos/{id}`) ni en la carga masiva (sección 4).
- No se puede dar de baja un producto con reservas `CONFIRMADA` o `LISTA` (`DELETE /productos/{id}`, sección 4).

Se calcula con `select coalesce(sum(cantidad), 0) from reserva where producto_id = :id and estado in ('CONFIRMADA', 'LISTA')`.

**Formato de un pedido:**
```json
{
  "id": 15,
  "codigo": "BC-7K2QXM",
  "estado": "PENDIENTE",
  "productoId": 9,
  "nombreComercial": "Paracetamol Genérico",
  "presentacion": "Tableta 500 mg x 10",
  "cantidad": 2,
  "precioUnitario": 2.20,
  "total": 4.40,
  "stockActual": 90,
  "disponible": 85,
  "compradorNombre": "Carlos R.",
  "motivoRechazo": null,
  "venceEn": "2026-11-13T15:30:00.123456Z",
  "creadoEn": "2026-11-12T15:30:00.123456Z",
  "actualizadoEn": "2026-11-12T15:30:00.123456Z"
}
```
- Del comprador **solo** se muestra `compradorNombre`, con la misma regla que `autor` en las reseñas (sección 11): **nombre de pila más la inicial del apellido** (`"Carlos Ramírez"` → `"Carlos R."`). Nunca su correo, teléfono ni dirección. Si el comprador eliminó su cuenta, sale `"Usuario eliminado"` (el nombre anonimizado de H19).
- `stockActual`: el stock de hoy del producto, para que el dueño sepa si puede atender antes de confirmar.
- `disponible`: **solo en `PENDIENTE`**; en los demás estados es `null`. Es `producto.stock` − la suma de `cantidad` de las **otras** reservas activas (`PENDIENTE`, `CONFIRMADA` y `LISTA`) del producto, **sin contar este pedido**. Es el mismo número que se valida al confirmar: si `disponible < cantidad`, confirmar dará 409, y la web lo usa para avisar al dueño antes de que lo intente (y sugerirle rechazarlo con un motivo). En el ejemplo: stock 90 y otras reservas activas por 5 → `disponible` 85. **Nunca es negativo:** si la resta da menos de 0 (por ejemplo, el dueño bajó el stock por debajo de reservas aún `PENDIENTE`), se devuelve 0. Ejemplo: stock 2 y otras reservas `PENDIENTE` por 5 → 2 − 5 = −3 → `disponible` 0.
- `precioUnitario`, `total` y `motivoRechazo`: igual que en la sección 9.

### `GET /pedidos?estado=PENDIENTE` 🔒 — H77, H81
Pedidos de la botica, del más reciente al más antiguo (por `creadoEn`), máximo 100. `estado` es opcional (H81): sin él devuelve todos.
```json
// 200 OK
[
  { "id": 15, "codigo": "BC-7K2QXM", "estado": "PENDIENTE", "productoId": 9,
    "nombreComercial": "Paracetamol Genérico", "presentacion": "Tableta 500 mg x 10",
    "cantidad": 2, "precioUnitario": 2.20, "total": 4.40, "stockActual": 90,
    "disponible": 85, "compradorNombre": "Carlos R.", "motivoRechazo": null,
    "venceEn": "2026-11-13T15:30:00.123456Z", "creadoEn": "2026-11-12T15:30:00.123456Z",
    "actualizadoEn": "2026-11-12T15:30:00.123456Z" }
]
```
Sin pedidos → `200` con `[]`.
Errores: 400 (`estado` no es uno de los 7 estados), 401 (sin token), 403 (el rol no es `DUENO_BOTICA`), 404 (no tiene botica).

### `GET /pedidos/{id}` 🔒 — H78
Detalle de un pedido de su botica. Respuesta: 200 con el pedido.
Errores: 400 (`id` no numérico), 401, 403, 404 (no tiene botica, o el pedido no existe o es de otra botica; mismo mensaje).

### `PATCH /pedidos/{id}/estado` 🔒 — H79
```json
// Request
{ "estado": "CONFIRMADA" }
```
Respuesta: 200 con el pedido actualizado.

Únicos cambios permitidos:

| Desde | Hacia | Qué más pasa |
|---|---|---|
| `PENDIENTE` | `CONFIRMADA` | Se valida lo disponible (ver abajo); `venceEn` no cambia. Desde aquí sus unidades quedan comprometidas |
| `CONFIRMADA` | `LISTA` | `vence_en = ahora + 48 h` (plazo para recoger) y `recordatorio_enviado = false`. No se valida stock: ya está garantizado |
| `LISTA` | `ENTREGADA` | Se descuenta `cantidad` del stock del producto, en la misma transacción |

- Cualquier otro cambio (saltarse un paso, volver atrás, salir de un estado final, o enviar `RECHAZADA`, `CANCELADA`, `VENCIDA` o `PENDIENTE`) → 409 "No se puede pasar de {actual} a {nuevo}". Para rechazar se usa `PATCH /pedidos/{id}/rechazar`.
- **Validación al confirmar (`PENDIENTE` → `CONFIRMADA`):** **el dueño no puede aceptar un pedido que no puede cumplir.** Con la fila del producto bloqueada (`select ... from producto where id = :id for update`), se calcula lo disponible igual que el campo `disponible`: `producto.stock` − (suma de `cantidad` de las **otras** reservas `PENDIENTE`, `CONFIRMADA` y `LISTA` del producto), y si da menos de 0 se toma 0. Debe ser ≥ `cantidad`. Si no → 409 "No hay stock suficiente para aceptar este pedido: disponible {n}, pedido {m}. Recházalo con un motivo." y el pedido sigue en `PENDIENTE`; el dueño debe rechazarlo con `PATCH /pedidos/{id}/rechazar`. Puede pasar si el dueño bajó el stock en su catálogo después de que se creó la reserva. Ejemplo: stock 3, otra reserva `CONFIRMADA` de 2 y este pedido de 2 → disponible 3 − 2 = 1, menor que 2 → 409 "No hay stock suficiente para aceptar este pedido: disponible 1, pedido 2. Recházalo con un motivo."
- **Producto dado de baja (H79):** al confirmar, con la misma fila del producto bloqueada y **antes** de calcular lo disponible, si el producto tiene `activo = false` → 409 "Este producto ya no está disponible en tu catálogo. Rechaza el pedido con un motivo." y el pedido sigue en `PENDIENTE`; el dueño lo rechaza con `PATCH /pedidos/{id}/rechazar`. Pasa cuando el dueño dio de baja el producto con reservas `PENDIENTE` (`DELETE /productos/{id}`, sección 4). Solo afecta al paso a `CONFIRMADA`: `CONFIRMADA` y `LISTA` no pueden existir en un producto dado de baja, porque la baja se bloquea si las tiene.
- **`LISTA` y `ENTREGADA` ya tienen el stock garantizado:** al confirmar, el pedido quedó cubierto, y desde entonces el stock no puede bajar de las unidades comprometidas (sección 4). Por eso al pasar a `LISTA` no se valida stock.
- **Descuento de stock al entregar (respaldo):** `update producto set stock = stock - :cantidad where id = :id and stock >= :cantidad`. Con la regla anterior siempre debería actualizar la fila; la condición `stock >= :cantidad` queda **solo como respaldo** por si algo inconsistente pasara (por ejemplo, un cambio hecho directo en la base). Si no actualiza ninguna fila → 409 "Stock insuficiente: quedan {stock} unidades" y el pedido sigue en `LISTA`. El producto se descuenta aunque esté dado de baja (la venta ocurrió). Si queda en 0, deja de verse en la búsqueda.
- Si `venceEn` ya pasó → 409 "La reserva ya venció".
- Correo al comprador **solo** al pasar a `LISTA`. Al pasar a `CONFIRMADA` o `ENTREGADA` no se envía correo: el estado se ve en la app (ver "Correos").

Errores:

| Código | Cuándo | `mensaje` |
|---|---|---|
| 400 | Falta `estado` o no es uno de los 7 estados | El mismo que el resto de la API |
| 401 | Sin token | El mismo que el resto de la API |
| 403 | El rol no es `DUENO_BOTICA` | El mismo que el resto de la API |
| 404 | No tiene botica, o el pedido no existe o no es suyo | "Todavía no registraste tu botica" o el mismo que `GET /pedidos/{id}` |
| 409 | Cambio no permitido | "No se puede pasar de {actual} a {nuevo}" |
| 409 | Al confirmar, el producto está dado de baja | "Este producto ya no está disponible en tu catálogo. Rechaza el pedido con un motivo." |
| 409 | Al confirmar, lo disponible no alcanza | "No hay stock suficiente para aceptar este pedido: disponible {n}, pedido {m}. Recházalo con un motivo." |
| 409 | Al entregar, inconsistencia de stock (respaldo) | "Stock insuficiente: quedan {stock} unidades" |
| 409 | `venceEn` ya pasó | "La reserva ya venció" |

### `PATCH /pedidos/{id}/rechazar` 🔒 — H80
```json
// Request
{ "motivo": "No nos queda stock de esa presentación" }
```
Respuesta: 200 con el pedido en `RECHAZADA` y `motivoRechazo` guardado (`reserva.motivo_rechazo`; `chk_reserva_motivo_rechazo` lo exige).
- `motivo` obligatorio, de 10 a 300 caracteres sin contar espacios al inicio ni al final → si no, 400.
- Solo desde `PENDIENTE` o `CONFIRMADA`. Desde `LISTA` o un estado final → 409 "No se puede rechazar un pedido {estado}". (Desde `LISTA` el comprador ya puede estar en camino.)
- No toca el stock. Sus unidades vuelven a la cantidad disponible.
- Envía un correo al comprador con el motivo (ver "Correos").

Errores: 400 (`motivo` ausente, muy corto o muy largo), 401, 403, 404, 409 (estado que no se puede rechazar o reserva vencida).

### Reservas vencidas y recordatorio — H84
No es un endpoint. Un proceso programado (`@Scheduled`, zona `America/Lima`, como `AlertaStockJob`) corre **cada 5 minutos** y hace dos cosas:
1. **Vencer:** pasa a `VENCIDA` las reservas en `PENDIENTE`, `CONFIRMADA` o `LISTA` con `vence_en <= now()`. Usa el índice `idx_reserva_vence_en`. No toca el stock (sus unidades vuelven a la cantidad disponible). No envía correo: el estado se ve en la app.
2. **Recordar:** a las reservas `LISTA` con `recordatorio_enviado = false` y `vence_en` entre ahora y ahora + 6 h, les envía el recordatorio y pone `recordatorio_enviado = true`. Se marca aunque el correo falle, para no reintentar cada 5 minutos (solo queda el aviso en el log).

Como el proceso corre cada 5 minutos, una reserva puede verse con su estado anterior hasta 5 minutos después de `venceEn`; por eso los endpoints de cambio de estado revisan `venceEn` por su cuenta (409 "La reserva ya venció").

### Correos — H82, H83, H84
No hay endpoint. Se usa el mismo `CorreoService` de las alertas: texto plano por SMTP (Resend), desde `MAIL_FROM`. Igual que en H61, se envían **después del commit y en segundo plano** (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`): no hacen más lenta la respuesta y no sale ningún correo si la transacción se deshace. Con `MAIL_HOST` vacío no se envía nada y solo queda el asunto en el log. Las fechas del correo van en hora de Lima (`dd/MM/yyyy HH:mm`) y los precios como `S/ 4.40`.

**Si el correo falla**, la reserva y el cambio de estado siguen funcionando: ya están guardados y la respuesta ya salió. El error solo va al log; no se reintenta ni se devuelve error al cliente.

**Como máximo 3 correos por reserva.** Solo estos tres:

| Correo | Para | Cuándo | Asunto de ejemplo | Lleva |
|---|---|---|---|---|
| Nueva reserva (H82) | Dueño (correo del usuario de la botica) | `POST /reservas` | "Nueva reserva BC-7K2QXM: Paracetamol Genérico x 2" | Código, producto y presentación, cantidad, precio unitario, total, nombre del comprador (`"Carlos R."`), fecha de la reserva, vence en, y que la atienda desde "Pedidos" en la web. **Sin** correo ni teléfono del comprador |
| Lista o rechazada (H83) | Comprador | El dueño la pasa a `LISTA` o la rechaza (`RECHAZADA`) | `LISTA`: "Tu reserva BC-7K2QXM está lista para recoger". `RECHAZADA`: "Tu reserva BC-7K2QXM fue rechazada" | Código, estado nuevo en palabras, producto y presentación, cantidad, total, nombre, dirección, distrito y teléfono de la botica. En `LISTA`: hasta cuándo puede recoger (`venceEn`). En `RECHAZADA`: el motivo |
| Recordatorio (H84) | Comprador | Faltan 6 h o menos para que venza una reserva `LISTA` (una sola vez, `recordatorio_enviado`) | "Tu reserva BC-7K2QXM vence hoy a las 15:30" | Código, producto, cantidad, total, datos de la botica y la hora de vencimiento |

**No se envían correos** cuando la reserva pasa a `CONFIRMADA`, `ENTREGADA`, `CANCELADA` ni `VENCIDA`: el comprador ve el estado en la app y el dueño en la web.

Una reserva que llega a recogerse usa los 3 (nueva, lista y recordatorio); una rechazada usa 2 (nueva y rechazada). **La cuenta:** Resend permite 100 correos al día para todo el equipo; con 3 por reserva son unas 33 reservas al día (100 ÷ 3 = 33.3), **sin contar** los correos de verificación, de recuperación de contraseña y de alertas de precio, que salen del mismo límite.

### Notas para el backend (secciones 9 a 11)
- `SecurityConfig` hoy pide `DUENO_BOTICA` para todo `/boticas/**`. Las reglas de las reseñas (sección 11) deben ir **antes** de esa línea: `GET /boticas/{id}/resenas` público y `PUT /boticas/{id}/resenas/mia` con `COMPRADOR`. `/reservas/**` va con `COMPRADOR`; `/pedidos/**` y `/resenas/**` con `DUENO_BOTICA`.
- El CORS de la web (`corsConfigurationSource`) no incluye `PATCH`; hay que agregarlo para `PATCH /pedidos/...`.
- El 403 por rol sale del `accessDeniedHandler` con el formato de error de siempre.

### Decisiones a confirmar con Alexander (sección 10)
1. **Plazo de 48 h** para recoger, contado desde que pasa a `LISTA`.
2. **El stock se descuenta al pasar a `ENTREGADA`** (no al crear ni al confirmar), con un `update` condicionado que queda como respaldo; si no alcanza, 409.
3. **Validación al confirmar:** al pasar a `CONFIRMADA`, stock − otras reservas activas debe alcanzar para el pedido; si no, 409 y sigue en `PENDIENTE` (el dueño lo rechaza con un motivo).
4. **Sin saltos:** no se puede ir de `PENDIENTE` o `CONFIRMADA` directo a `ENTREGADA`.
5. **Rechazo solo desde `PENDIENTE` o `CONFIRMADA`**, no desde `LISTA`.
6. **Proceso cada 5 minutos** para vencer y recordar; el recordatorio se marca como enviado aunque falle el correo.
7. **Correos: máximo 3 por reserva** (nueva al dueño, `LISTA` o `RECHAZADA` al comprador, y el recordatorio). Ninguno en `CONFIRMADA`, `ENTREGADA`, `CANCELADA` ni `VENCIDA`. Si un correo falla, la reserva o el cambio de estado siguen igual (solo log). Alcanza para unas 33 reservas al día, sin contar verificación, recuperación y alertas de precio.
8. **`compradorNombre`** con nombre de pila más la inicial del apellido (`"Carlos R."`), igual que `autor` en las reseñas.
9. **`stockActual`** en el pedido y **máximo 100** pedidos en la lista, sin paginación.
10. **Stock comprometido (13/11/2026, H79, H80):** el dueño no puede comprometer stock que no tiene; aceptar un pedido sin stock sería engañar al comprador. Unidades comprometidas = suma de `cantidad` de las reservas `CONFIRMADA` y `LISTA`. Solo se confirma si lo disponible alcanza (si no, 409 y se rechaza con motivo); `PUT /productos/{id}` y la carga masiva no dejan el stock por debajo de lo comprometido (409, o error de esa fila en la carga masiva). El pedido trae `disponible` (solo en `PENDIENTE`) para avisar antes de confirmar. Con esto `LISTA` y `ENTREGADA` tienen el stock garantizado y la validación al entregar queda solo como respaldo. Mensaje del 409 en singular o plural: "Tienes 1 unidad comprometida…" o "Tienes {n} unidades comprometidas…". `disponible` nunca es negativo: si la resta da menos de 0, se devuelve 0.
11. **Baja de un producto con pedidos (14/11/2026, H79, H80):** `DELETE /productos/{id}` responde 409 "No puedes dar de baja un producto con pedidos confirmados o listos para recoger. Atiéndelos o recházalos primero." si el producto tiene reservas `CONFIRMADA` o `LISTA` (el dueño ya se comprometió con el comprador). Las `PENDIENTE` no bloquean la baja, pero al dar de baja el producto ya no se pueden confirmar: el dueño las rechaza con un motivo.

---

## 11. Reseñas de boticas

Calificación de 1 a 5 estrellas con comentario opcional. Una reseña por comprador y botica (`uq_resena_usuario_botica`). El dueño puede responderla. El administrador las modera desde Django (H89).

**Formato de una reseña en la lista:**
```json
{
  "id": 8,
  "autor": "Carlos R.",
  "estrellas": 4,
  "comentario": "Me atendieron rápido y tenían todo.",
  "respuesta": "¡Gracias, Carlos! Te esperamos.",
  "respondidaEn": "2026-11-14T10:00:00.123456Z",
  "creadoEn": "2026-11-13T18:20:00.123456Z",
  "actualizadoEn": "2026-11-13T18:20:00.123456Z"
}
```
- `autor`: **nombre de pila más la inicial del apellido**, la misma regla que `compradorNombre` en los pedidos (sección 10): la primera palabra de `usuario.nombre`, un espacio, la primera letra de la segunda palabra en mayúscula y un punto (`"Carlos Ramírez"` → `"Carlos R."`). Si `usuario.nombre` tiene una sola palabra, sale solo esa. Nunca el apellido completo, el correo ni el id del usuario. Si la cuenta se eliminó (H19), sale `"Usuario eliminado"`.
- `comentario`, `respuesta` y `respondidaEn` son `null` si no hay.

### `PUT /boticas/{id}/resenas/mia` 🔒 — H85, H86
Solo `COMPRADOR`. Crea la reseña del comprador para esa botica o, si ya tiene una, la actualiza (H86).
```json
// Request
{ "estrellas": 4, "comentario": "Me atendieron rápido y tenían todo." }
```
```json
// 201 Created (200 OK si ya tenía reseña y se actualizó)
{
  "id": 8,
  "boticaId": 2,
  "estrellas": 4,
  "comentario": "Me atendieron rápido y tenían todo.",
  "respuesta": null,
  "respondidaEn": null,
  "oculta": false,
  "creadoEn": "2026-11-13T18:20:00.123456Z",
  "actualizadoEn": "2026-11-13T18:20:00.123456Z"
}
```
- La botica debe existir y estar `APROBADO` → si no, 404.
- `estrellas` obligatorio, entero de 1 a 5 → si no, 400.
- `comentario` opcional, hasta 500 caracteres. Se quitan los espacios al inicio y al final; vacío se guarda como `null`. Si en una actualización no se envía, queda en `null`.
- **Reserva entregada (propuesta):** el comprador debe tener al menos una reserva `ENTREGADA` de un producto de esa botica. Si no → 403 "Solo puedes reseñar boticas donde ya recogiste una reserva". Evita reseñas falsas.
- Al actualizar se conserva la respuesta del dueño. Si el administrador la ocultó, sigue oculta aunque el comprador la edite (`oculta` le avisa que no se muestra).

Errores: 400 (`id` no numérico, `estrellas` fuera de 1 a 5 o `comentario` de más de 500 caracteres), 401, 403 (el rol no es `COMPRADOR` o no tiene una reserva entregada en esa botica), 404 (la botica no existe o no está aprobada).

### `GET /boticas/{id}/resenas?page=0` — H87
Público (no necesita token). Promedio, total y lista paginada de las reseñas **no ocultas**, de la más reciente a la más antigua (por `creadoEn`).

| Parámetro | Valores | Por defecto |
|---|---|---|
| `page` | Número de página, desde 0 | 0 |

Páginas de 10 reseñas.
```json
// 200 OK
{
  "boticaId": 2,
  "promedio": 4.3,
  "total": 12,
  "page": 0,
  "totalPages": 2,
  "resenas": [
    { "id": 8, "autor": "Carlos R.", "estrellas": 4, "comentario": "Me atendieron rápido y tenían todo.",
      "respuesta": "¡Gracias, Carlos! Te esperamos.", "respondidaEn": "2026-11-14T10:00:00.123456Z",
      "creadoEn": "2026-11-13T18:20:00.123456Z", "actualizadoEn": "2026-11-13T18:20:00.123456Z" }
  ],
  "miResena": null
}
```
- `promedio`: de las reseñas no ocultas, con 1 decimal; `null` si `total` es 0. `total` y `totalPages` tampoco cuentan las ocultas.
- Una página después de la última → 200 con `resenas: []`.
- `miResena` (propuesta): si la petición trae token de `COMPRADOR` y tiene reseña en esa botica, va con el formato de `PUT /boticas/{id}/resenas/mia` (incluida `oculta`), para que la app rellene el formulario de edición. Si no, `null`. Sin token o con otro rol, siempre `null`.

Errores: 400 (`id` o `page` no numérico, o `page` negativo), 404 (la botica no existe o no está aprobada).

### `PUT /resenas/{id}/respuesta` 🔒 — H88
Solo `DUENO_BOTICA`, y solo sobre reseñas de **su** botica (sale del token).
```json
// Request
{ "respuesta": "¡Gracias, Carlos! Te esperamos." }
```
Respuesta: 200 con la reseña (formato de la lista).
- `respuesta` obligatoria, de 1 a 500 caracteres sin contar espacios al inicio ni al final → si no, 400.
- Guarda `respuesta` y `respondida_en = now()` juntas (`chk_resena_respuesta`). Volver a enviarla reemplaza la anterior y actualiza `respondidaEn`. En este sprint no se puede borrar una respuesta.
- Una reseña oculta por el administrador no se puede responder → 404.

Errores: 400 (`id` no numérico o `respuesta` vacía o de más de 500), 401, 403 (el rol no es `DUENO_BOTICA`), 404 (no tiene botica, la reseña no existe, es de otra botica o está oculta).

### Moderación — H89
No es un endpoint de esta API. El administrador oculta reseñas desde el panel Django: pone `oculta = true` con un `motivo_ocultamiento` obligatorio (`chk_resena_motivo_ocultamiento`). Una reseña oculta no aparece en `GET /boticas/{id}/resenas`, no cuenta en `promedio` ni en `total`, y el dueño no puede responderla. El autor la sigue viendo en `miResena`, con `oculta: true`.

### Decisiones a confirmar con Alexander (sección 11)
1. **Reseña solo con una reserva `ENTREGADA`** en esa botica (403 si no). Es la decisión más importante: sin ella cualquiera puede calificar cualquier botica.
2. **`PUT` que crea o actualiza** (201 o 200) en lugar de `POST` + `PUT` separados.
3. **Páginas de 10 desde `page=0`**, ordenadas de la más reciente a la más antigua.
4. **`GET` público** y con `miResena` cuando viene token de comprador (evita un endpoint más para la app).
5. **`autor` con nombre de pila más la inicial del apellido** (`"Carlos R."`), la misma regla que `compradorNombre` en los pedidos.
6. **Reseñas de cuentas eliminadas** se conservan con autor `"Usuario eliminado"`.
7. **Editar no quita el ocultamiento** y las reseñas ocultas no se pueden responder.

---

## Usuarios de prueba (seed.sql)

Contraseña de todos: `Boti2026!`

| Correo | Rol | Situación |
|---|---|---|
| rosa@boticompara.nissidev.dev | DUENO_BOTICA | Botica Salud Total — APROBADO |
| jorge@boticompara.nissidev.dev | DUENO_BOTICA | FarmaVida — APROBADO |
| lucia@boticompara.nissidev.dev | DUENO_BOTICA | Botica Nueva Era — RECHAZADO (para probar la corrección, H7) |
| carlos@boticompara.nissidev.dev | COMPRADOR | Para probar el móvil |

Búsqueda de prueba: `q=paracetamol&tipo=principio` debe devolver 4 resultados, el primero a S/ 2.20.
