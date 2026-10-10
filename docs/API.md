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
  "correo": "carlos@correo.com",
  "password": "Boti2026!",
  "telefono": "954321098",
  "rol": "COMPRADOR"
}
```
```json
// 201 Created
{ "id": 4, "nombre": "Carlos Ramírez", "correo": "carlos@correo.com", "rol": "COMPRADOR" }
```
Validaciones: correo con formato válido, password de mínimo 8 caracteres, `rol` = `COMPRADOR` o `DUENO_BOTICA`. Nunca se piden datos de salud (H21).
Tras el registro se envía un correo con un enlace de verificación (H20). El login **no** exige tener el correo verificado.
Errores: 400, 409 (correo ya registrado).

### `POST /auth/login` — H18
```json
// Request
{ "correo": "carlos@correo.com", "password": "Boti2026!" }
```
```json
// 200 OK
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tipo": "Bearer",
  "usuario": { "id": 4, "nombre": "Carlos Ramírez", "correo": "carlos@correo.com", "rol": "COMPRADOR" }
}
```
El token dura 24 h. Errores: 401.

### `POST /auth/recuperar` — H17
Inicia la recuperación de contraseña. **Siempre responde 200**, exista o no el correo, para no revelar qué correos están registrados.
```json
// Request
{ "correo": "carlos@correo.com" }
```
```json
// 200 OK
{ "mensaje": "Si el correo está registrado, te enviamos las instrucciones para restablecer tu contraseña" }
```
Si el correo existe, se envía un mensaje con el token y un enlace. El token vence a los 30 minutos y solo sirve una vez.
Errores: 400 (falta el campo `correo`).

### `POST /auth/restablecer` — H17
```json
// Request
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "password": "NuevaClave2026!" }
```
```json
// 200 OK
{ "mensaje": "Contraseña actualizada" }
```
Errores: 400 (token inválido, vencido o ya usado; o password de menos de 8 caracteres).

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
{ "id": 4, "nombre": "Carlos Ramírez", "correo": "carlos@correo.com",
  "telefono": "954321098", "direccion": "Av. Lima 123, Chosica", "rol": "COMPRADOR" }
```

### `PUT /usuarios/me` 🔒 — H16
```json
// Request (el correo y el rol NO se editan aquí)
{ "nombre": "Carlos Ramírez", "telefono": "999888777", "direccion": "Jr. Ayacucho 45" }
```
Respuesta: 200 con el perfil actualizado.

### `DELETE /usuarios/me` 🔒 — H19
Elimina la cuenta del usuario logueado. Los datos se **anonimizan**, no se borra la fila:
- `nombre` pasa a `"Usuario eliminado"` y `correo` a `eliminado-{id}@boticompara.local`.
- `telefono` y `direccion` quedan en `null`, la contraseña deja de servir y `activo = false`.
- Si es dueño de botica, todos sus productos pasan a `activo = false` (dejan de aparecer en las búsquedas).

Respuesta: 204 sin cuerpo. El token usado deja de ser válido (las siguientes llamadas responden 401).

---

## 3. Botica (rol `DUENO_BOTICA`)

### `GET /boticas/validar-ruc/{ruc}` — H2
Valida solo el formato y el dígito verificador (sin servicios externos). La web lo usa para avisar mientras se escribe.
```json
// 200 OK
{ "ruc": "20601234565", "valido": true, "mensaje": "RUC válido" }
```

**Algoritmo del dígito verificador:**
1. 11 dígitos numéricos; los 2 primeros deben ser `10`, `15`, `17` o `20`.
2. Multiplicar los primeros 10 dígitos por los pesos `5, 4, 3, 2, 7, 6, 5, 4, 3, 2` y sumar.
3. `resto = 11 - (suma % 11)`; si `resto == 10` → `0`; si `resto == 11` → `1`.
4. El resultado debe ser igual al dígito 11.

### `POST /boticas` 🔒 — H1, H2, H3
Registra la botica del dueño logueado. Queda en estado `PENDIENTE`.
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
{ "id": 1, "nombreComercial": "Botica Salud Total", "ruc": "20601234565", "estado": "PENDIENTE" }
```
`latitud` y `longitud` son opcionales (H3), pero si se envía una hay que enviar la otra. Rangos: latitud entre -90 y 90, longitud entre -180 y 180, hasta 6 decimales.
Errores: 400 (RUC inválido, coordenadas inválidas o incompletas), 409 (RUC ya registrado o el dueño ya tiene botica).

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
Cuerpo igual al de `POST /boticas` (incluye `latitud` y `longitud` opcionales).
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
    "presentacion": "Tableta 500 mg x 10", "precio": 8.50, "stock": 40, "stockMinimo": 5 }
]
```

### `POST /productos` 🔒 — H8
```json
// Request
{ "nombreComercial": "Panadol", "principioActivo": "Paracetamol",
  "presentacion": "Tableta 500 mg x 10", "precio": 8.50, "stock": 40, "stockMinimo": 5 }
```
Respuesta: 201 con el producto creado. Validaciones: `precio > 0`, `stock >= 0`, textos obligatorios. `stockMinimo` es opcional (por defecto 5) y debe ser `>= 0`.

### `PUT /productos/{id}` 🔒 — H9
Mismo cuerpo que el POST. Respuesta: 200 con el producto actualizado. 404 si no es de su botica.

### `DELETE /productos/{id}` 🔒 — H10
Baja lógica (`activo = false`): deja de aparecer en listados y búsquedas. Respuesta: 204 sin cuerpo.

### `POST /productos/carga-masiva` 🔒 — H11
Carga o actualiza muchos productos con un archivo CSV. Es `multipart/form-data` con un campo `archivo`. Requiere botica `APROBADO` (si no → 403).

**Formato del archivo:** UTF-8, separador coma, primera fila de encabezado, máximo 5 MB y 1000 filas.
```
nombreComercial,principioActivo,presentacion,precio,stock,stockMinimo
Panadol,Paracetamol,Tableta 500 mg x 10,8.50,40,5
Ibuprofeno Genérico,Ibuprofeno,Tableta 400 mg x 10,3.20,100,10
```
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
    { "fila": 7, "mensaje": "El precio debe ser mayor a 0" },
    { "fila": 9, "mensaje": "Producto duplicado en el archivo" }
  ]
}
```
`fila` es el número de línea del archivo (el encabezado es la fila 1).
Errores: 400 (archivo ausente, vacío, no es CSV, encabezado inválido o más de 1000 filas), 403 (botica no aprobada).

### Alerta diaria de stock bajo — H12
No es un endpoint. Todos los días a las 8:00 (hora de Lima) el backend envía al correo del dueño la lista de productos activos de su botica con `stock <= stockMinimo`. Si no hay ninguno, no se envía nada.

---

## 5. Búsqueda del comprador

### `GET /productos/buscar?q=paracetamol&tipo=nombre&orden=precio_asc` 🔒 — H22, H23, H24

| Parámetro | Valores | Por defecto |
|---|---|---|
| `q` | texto a buscar (mínimo 2 caracteres) | obligatorio |
| `tipo` | `nombre` (H22) o `principio` (H23) | `nombre` |
| `orden` | `precio_asc` (H24) o `precio_desc` | `precio_asc` |

Solo devuelve productos activos, con stock > 0, de boticas `APROBADO` (vista `v_producto_busqueda`).
```json
// 200 OK
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
    "boticaDistrito": "Lurigancho-Chosica"
  }
]
```
Sin resultados → `200` con `[]` (la app muestra "No encontramos ese medicamento").

---

## Usuarios de prueba (seed.sql)

Contraseña de todos: `Boti2026!`

| Correo | Rol | Situación |
|---|---|---|
| rosa@boticasalud.pe | DUENO_BOTICA | Botica Salud Total — APROBADO |
| jorge@farmavida.pe | DUENO_BOTICA | FarmaVida — APROBADO |
| lucia@boticanueva.pe | DUENO_BOTICA | Botica Nueva Era — PENDIENTE (para probar Django) |
| carlos@correo.com | COMPRADOR | Para probar el móvil |

Búsqueda de prueba: `q=paracetamol&tipo=principio` debe devolver 4 resultados, el primero a S/ 2.20.
