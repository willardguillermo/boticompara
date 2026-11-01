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
Si el correo existe, se envía un mensaje con un código de 6 dígitos. El código vence a los 15 minutos, solo sirve una vez y se invalida tras 5 intentos fallidos. Si ya se pidió uno hace menos de un minuto, no se envía otro (la respuesta sigue siendo 200).
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
**Regla D2:** al enlazar (en `POST` o `PUT`), el backend copia el nombre del principio activo del medicamento a `principioActivo` del producto, y la respuesta devuelve ese valor actualizado. Al quitar el enlace con `DELETE /productos/{id}/medicamento`, el producto conserva ese texto.

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
nombreComercial,principioActivo,presentacion,precio,stock,stockMinimo
Panadol,Paracetamol,Tableta 500 mg x 10,8.50,40,5
Ibuprofeno Genérico,Ibuprofeno,Tableta 400 mg x 10,3.20,100,10
```

**Excel (`.xlsx`):** las mismas columnas, en la primera hoja. Las celdas pueden ser de texto o numéricas; de una fórmula se toma el valor calculado. Los `.xls` de Excel 97-2003 no se admiten: hay que guardarlos como `.xlsx` o como CSV UTF-8.

La columna `stockMinimo` es opcional.

**Reglas por fila:**
- Se validan los mismos campos que en `POST /productos`.
- Si la botica ya tiene un producto activo con el mismo `nombreComercial` y `presentacion` (sin distinguir mayúsculas), se **actualizan** su precio y su stock (y `stockMinimo` si la fila lo trae). Si no existe, se **crea**.
- Si el mismo producto aparece dos veces en el archivo, la segunda aparición se informa como error.
- Las filas válidas se guardan aunque otras tengan errores.

```json
// 200 OK
{
  "creados": 12,
  "actualizados": 3,
  "errores": [
    { "fila": 7, "mensaje": "El precio debe ser mayor a 0." },
    { "fila": 9, "mensaje": "Producto duplicado en el archivo (ya aparece en la fila 2)." }
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
- `precioObjetivo` debe ser mayor que 0, con máximo 2 decimales y menor que 100000.
- Puede ser mayor o igual al precio actual: la alerta llega en la siguiente baja que lo cumpla.
- Cada vez que se pone, cambia o quita, se reinicia el registro del último precio avisado, para que la siguiente baja que cumpla la regla vuelva a avisar.

Errores: 400 (valor inválido), 401 (sin token), 404 (el producto no está en sus favoritos).

### `DELETE /favoritos/{productoId}` 🔒 — H43, H44
Quita el producto de los favoritos. Responde `204` sin cuerpo, también si el producto no estaba guardado.
Errores: 400 (`productoId` no numérico), 401 (sin token).

---

## 7. Alertas de precio (cualquier usuario con sesión)

Avisos de que bajó el precio de un producto que el usuario tiene en favoritos (H59, H61). Se ven en la pantalla "Alertas" de la app y llegan por correo. No hay notificaciones push en este sprint.

### Cuándo se crea una alerta — H59, H60, H61, H66
No es un endpoint. Se evalúa cada vez que cambia el precio de un producto, en `PUT /productos/{id}` y en `POST /productos/carga-masiva`:
1. Solo si el precio **baja** (nuevo < anterior). Si sube o no cambia, no se crea nada.
2. Solo si el producto se ve en la búsqueda: activo, con stock, de botica `APROBADO`, sin medicamento oculto ni registro vencido.
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
Errores: 401 (sin token).

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

## Usuarios de prueba (seed.sql)

Contraseña de todos: `Boti2026!`

| Correo | Rol | Situación |
|---|---|---|
| rosa@boticompara.nissidev.dev | DUENO_BOTICA | Botica Salud Total — APROBADO |
| jorge@boticompara.nissidev.dev | DUENO_BOTICA | FarmaVida — APROBADO |
| lucia@boticompara.nissidev.dev | DUENO_BOTICA | Botica Nueva Era — RECHAZADO (para probar la corrección, H7) |
| carlos@boticompara.nissidev.dev | COMPRADOR | Para probar el móvil |

Búsqueda de prueba: `q=paracetamol&tipo=principio` debe devolver 4 resultados, el primero a S/ 2.20.
