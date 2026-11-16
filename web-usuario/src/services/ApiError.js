/**
 * Error con el formato único de la API (docs/API.md):
 * { status, error, mensaje, timestamp }
 */
export class ApiError extends Error {
  constructor({ status, error, mensaje, timestamp }) {
    super(mensaje)
    this.name = 'ApiError'
    this.status = status
    this.error = error
    this.mensaje = mensaje
    this.timestamp = timestamp ?? new Date().toISOString()
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

/** Crea un ApiError con el texto HTTP estándar para el código. */
export function crearError(status, mensaje) {
  return new ApiError({ status, error: TEXTOS_HTTP[status] ?? 'Error', mensaje })
}
