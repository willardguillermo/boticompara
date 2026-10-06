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

const TEXTOS_HTTP = {
  400: 'Bad Request',
  401: 'Unauthorized',
  403: 'Forbidden',
  404: 'Not Found',
  409: 'Conflict',
}

/** Crea un ApiError con el texto HTTP estándar para el código. */
export function crearError(status, mensaje) {
  return new ApiError({ status, error: TEXTOS_HTTP[status] ?? 'Error', mensaje })
}
