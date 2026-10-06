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
| 403 | Rol sin permiso, o botica no aprobada intentando crear productos |
| 404 | Recurso no existe o no pertenece al usuario |
| 409 | Duplicado (correo o RUC ya registrado) |

---

## 1. Autenticación

### `POST /auth/registro` — H1, H15
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

### `POST /boticas` 🔒 — H1, H2
Registra la botica del dueño logueado. Queda en estado `PENDIENTE`.
```json
// Request
{
  "nombreComercial": "Botica Salud Total",
  "ruc": "20601234565",
  "razonSocial": "Salud Total Farma S.A.C.",
  "direccion": "Av. Lima Sur 450",
  "distrito": "Lurigancho-Chosica",
  "telefono": "014567890"
}
```
```json
// 201 Created
{ "id": 1, "nombreComercial": "Botica Salud Total", "ruc": "20601234565", "estado": "PENDIENTE" }
```
Errores: 400 (RUC inválido), 409 (RUC ya registrado o el dueño ya tiene botica).

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
  "estado": "RECHAZADO",
  "motivoRechazo": "La dirección no coincide con la ficha RUC"
}
```
`motivoRechazo` es `null` salvo cuando el estado es `RECHAZADO`. Error: 404 si el dueño todavía no registró botica.

---

## 4. Catálogo (rol `DUENO_BOTICA`, solo sobre su propia botica)

Para crear productos la botica debe estar `APROBADO`; si no → 403.

### `GET /boticas/mia/productos?q=texto` 🔒 — H13, H14
Sin `q` lista todo el catálogo activo; con `q` filtra por nombre comercial o principio activo (sin distinguir mayúsculas).
```json
// 200 OK
[
  { "id": 1, "nombreComercial": "Panadol", "principioActivo": "Paracetamol",
    "presentacion": "Tableta 500 mg x 10", "precio": 8.50, "stock": 40 }
]
```

### `POST /productos` 🔒 — H8
```json
// Request
{ "nombreComercial": "Panadol", "principioActivo": "Paracetamol",
  "presentacion": "Tableta 500 mg x 10", "precio": 8.50, "stock": 40 }
```
Respuesta: 201 con el producto creado. Validaciones: `precio > 0`, `stock >= 0`, textos obligatorios.

### `PUT /productos/{id}` 🔒 — H9
Mismo cuerpo que el POST. Respuesta: 200 con el producto actualizado. 404 si no es de su botica.

### `DELETE /productos/{id}` 🔒 — H10
Baja lógica (`activo = false`): deja de aparecer en listados y búsquedas. Respuesta: 204 sin cuerpo.

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
