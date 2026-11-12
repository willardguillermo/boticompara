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
| 403 | Rol sin permiso, botica no aprobada intentando crear productos, o acción no permitida por el estado de la botica |
| 404 | Recurso no existe o no pertenece al usuario |
| 409 | Duplicado (correo o RUC ya registrado) |
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

### `DELETE /productos/{id}` 🔒 — H10
Baja lógica (`activo = false`): deja de aparecer en listados y búsquedas. Respuesta: 204 sin cuerpo.

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
    { "fila": 11, "mensaje": "El medicamento no existe en el catálogo" }
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
  ]
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
  ]
}
```

```json
// 200 OK — pregunta fuera de alcance ("Me duele la cabeza, ¿qué tomo?")
{
  "respuesta": "No puedo recomendarte medicamentos ni dosis. Consulta con el químico farmacéutico de tu botica o con tu médico. Si es una emergencia, llama al SAMU al 106. Si ya sabes qué medicamento necesitas, te ayudo a encontrar dónde está más barato.",
  "productos": []
}
```

**`respuesta`:** texto plano en español, sin markdown, sin listas ni enlaces, de hasta 600 caracteres. Está pensada para leerse en voz alta (H71): los precios van escritos como se dicen.

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

**Límite de uso (por usuario):** por defecto 3 preguntas por minuto y 30 por día (el día cambia a las 00:00, hora de Lima). Al pasarlo → 429. Los pedidos que responden 400 o 401 no cuentan. Los dos valores son configurables en el backend (`chat.limite.por-minuto` y `chat.limite.por-dia`). Con 3 llamadas a Gemini como máximo por pregunta, un usuario usa como máximo 9 de los 15 pedidos por minuto del plan gratis de `gemini-3.5-flash-lite`. La cuota de Gemini es por proyecto, no por usuario: la comparten todos los usuarios de un mismo backend.

Errores:

| Código | Cuándo | `mensaje` |
|---|---|---|
| 400 | `mensaje` vacío o solo espacios | "Escribe tu pregunta" |
| 400 | `mensaje` de más de 500 caracteres | "La pregunta no puede pasar de 500 caracteres" |
| 400 | `historial` con un `rol` distinto de `usuario` o `asistente`, o un `texto` vacío o de más de 1000 caracteres | "El historial no es válido" |
| 400 | Solo una de `lat` y `lng`, o coordenadas fuera de rango | Los mismos mensajes que `GET /productos/buscar` |
| 401 | El token no es válido o la cuenta no está habilitada (dada de baja o suspendida) | El mismo que el resto de la API |
| 429 | Se pasó el límite por minuto o por día | "Hiciste muchas preguntas seguidas. Espera un minuto y vuelve a intentar" o "Llegaste al límite de preguntas de hoy. Vuelve mañana" |
| 503 | Gemini no responde, devuelve un error, se agotó la cuota gratis del proyecto, o falta `GEMINI_API_KEY` | "El asistente no está disponible en este momento. Intenta más tarde" |
| 504 | La respuesta completa tarda más de 20 segundos | "El asistente tardó demasiado en responder. Intenta de nuevo" |

- El 429 trae el header `Retry-After` con los segundos que faltan para poder preguntar otra vez.
- El 503 y el 504 no dicen la causa interna (clave, cuota, modelo): eso va solo al log del servidor.

**Configuración (backend):**
- `GEMINI_API_KEY` y `GEMINI_MODEL` en el `.env` de `backend-usuario/`. Cada integrante usa su propia clave de Google AI Studio; nunca se sube al repositorio.
- Valor recomendado de `GEMINI_MODEL`: `gemini-3.5-flash-lite` (plan gratis: 15 pedidos por minuto, 250 000 tokens por minuto y 500 pedidos por día). Alternativa, solo si la calidad no alcanza: `gemini-3.8-flash` (5 pedidos por minuto y 20 por día, unas 7 preguntas al día). Son límites por proyecto, no por usuario; cada uno los confirma en <https://aistudio.google.com/rate-limit> con su clave.
- `chat.limite.por-minuto` (por defecto 3) y `chat.limite.por-dia` (por defecto 30).
- El `.env.example` y la colección de Postman se actualizan en el PR del módulo.

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
