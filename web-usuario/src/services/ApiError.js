/**
 * Error con el formato único de la API (docs/API.md):
 * { status, error, mensaje, timestamp } y, a veces, `codigo` (por ejemplo CORREO_NO_VERIFICADO, H20).
 * `retryAfter` es el header Retry-After de un 429 (texto tal cual llegó, o null).
 */
export class ApiError extends Error {
  constructor({ status, error, mensaje, timestamp, codigo = null, retryAfter = null }) {
    super(mensaje)
    this.name = 'ApiError'
    this.status = status
    this.error = error
    this.mensaje = mensaje
    this.timestamp = timestamp ?? new Date().toISOString()
    this.codigo = codigo
    this.retryAfter = retryAfter
  }
}

/** Se usa cuando la respuesta de error no trae `mensaje` (por ejemplo, un 504 de un proxy sin JSON). */
export const MENSAJE_INESPERADO = 'Ocurrió un error inesperado. Inténtalo nuevamente.'

const TEXTOS_HTTP = {
  400: 'Bad Request',
  401: 'Unauthorized',
  403: 'Forbidden',
  404: 'Not Found',
  409: 'Conflict',
  429: 'Too Many Requests',
  503: 'Service Unavailable',
  504: 'Gateway Timeout',
}

/** Crea un ApiError con el texto HTTP estándar para el código; `extra` puede traer codigo y retryAfter. */
export function crearError(status, mensaje, extra = {}) {
  return new ApiError({ status, error: TEXTOS_HTTP[status] ?? 'Error', mensaje, ...extra })
}
