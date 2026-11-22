# Decisiones de seguridad

Decisiones tomadas a propósito, con sus razones y con lo que haría cambiarlas. Los hallazgos están en
[`escaneo-sprint3.md`](escaneo-sprint3.md).

---

## SEC-06: el JWT de la web sigue en `localStorage`

| | |
|---|---|
| **Estado** | **Decisión aceptada.** No es un olvido ni un pendiente |
| **Fecha** | 22/11/2026, antes del primer despliegue (en el Sprint 3 se aceptó con la condición de revisarlo ahora) |
| **Responsable** | Guillermo |
| **Alcance** | Solo la web del dueño (`web-usuario/src/services/sesion.js`). La app móvil no usa navegador ni cookies |

### El riesgo
Si alguien lograra ejecutar JavaScript en la web (XSS), podría leer el token y usarlo hasta que venza. Una cookie `HttpOnly`
evitaría que lo **lea**, aunque ese mismo script podría igual hacer peticiones con la sesión mientras la página está abierta.

### Por qué se queda en `localStorage`
1. **La web y la API están en sitios distintos.** `vercel.app` y `onrender.com` están en la
   [Public Suffix List](https://publicsuffix.org/list/), así que `algo.vercel.app` y `algo.onrender.com` no comparten sitio. Una
   cookie de la API sería **de terceros**: Safari la bloquea, Firefox la aísla y Chrome la bloquea en incógnito o si el usuario lo
   pide. El login fallaría según el navegador.
2. **Haría falta protección CSRF.** Esa cookie tendría que ser `SameSite=None` y el navegador la enviaría sola en cada petición. Hoy el
   token va en la cabecera `Authorization`, que otro sitio no puede poner: por eso la API no necesita CSRF.
3. **Un XSS es difícil en esta web:**
   - CSP estricta (PR #123): `default-src 'none'` y `script-src 'self'`; sin scripts en línea, sin `eval` y sin scripts de terceros.
     `connect-src` solo permite la API y Nominatim, así que un script tampoco puede mandar el token a otro servidor con `fetch`. No es absoluto: una CSP no impide que un script cambie de página con el token en la URL.
   - React escapa todo el texto, y el código no usa `dangerouslySetInnerHTML`, `innerHTML` ni `eval` (revisado el 22/11/2026).
4. **El daño está acotado en el tiempo:** el token dura 24 h y se borra del navegador al cerrar sesión o al recibir un 401.

### Lo que se acepta
- Un XSS que pasara la CSP podría leer el token. La CSP reduce mucho ese riesgo, pero no lo hace cero.
- Al cerrar sesión el token se borra del navegador, pero el servidor lo sigue aceptando hasta que venza (SEC-11).
- Todo esto depende de que la CSP esté activa: **el #123 debe estar en `main` antes de publicar la web.**

### Alternativa descartada: pasar la API por Vercel
Con un `rewrite` de `/api` hacia Render, la cookie sería propia sin comprar un dominio. Se descartó por ahora: todas las peticiones
llegarían a Render desde Vercel y habría que rehacer la IP del límite de login (SEC-05), agrega un salto más y obliga a cambiar la API.

### Qué cambiaría con un dominio propio común
Con la web en `app.midominio` y la API en `api.midominio` (subdominios del **mismo** dominio), la cookie deja de ser de terceros:
- `POST /auth/login` respondería con una cookie `HttpOnly; Secure; SameSite=Lax` (o `Strict`) del subdominio de la API, y la web dejaría de guardar el token.
- `JwtAuthFilter` la leería además de `Authorization`, que la app móvil seguiría usando.
- CORS con `allowCredentials` y el origen exacto, `credentials: 'include'` en la web, protección CSRF en lo que escribe datos y un endpoint para cerrar sesión.

Es un cambio del contrato y del código de Alexander.

### Cuándo se vuelve a revisar
- Si el proyecto tiene dominio propio común.
- Si la web necesita scripts de terceros o relajar `script-src`.
- Si aparece un XSS, o una función que muestre HTML escrito por usuarios.
