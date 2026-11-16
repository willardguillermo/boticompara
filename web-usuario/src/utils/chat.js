// H69 - Reglas del chat del asistente en la web (docs/API.md, sección 8, POST /chat)
import { MENSAJE_INESPERADO } from '../services/ApiError.js'

export const MAX_CARACTERES_CHAT = 500
export const MAX_HISTORIAL_CHAT = 10
// El servidor corta a los 50 s; la web espera un poco más para no cortar antes que él
export const TIEMPO_ESPERA_CHAT_MS = 60_000

export const MENSAJE_TARDO = 'El asistente tardó demasiado. Intenta de nuevo.'
export const MENSAJE_GENERICO = 'No se pudo obtener una respuesta del asistente. Intenta de nuevo.'

/**
 * Historial que se envía: solo lo que ya se mostró y salió bien (preguntas
 * respondidas y respuestas), sin tarjetas, del más antiguo al más reciente
 * y como máximo los 10 últimos.
 */
export function armarHistorial(mensajes) {
  return mensajes
    .filter((m) => m.estado === 'ok')
    .map(({ rol, texto }) => ({ rol, texto }))
    .slice(-MAX_HISTORIAL_CHAT)
}

/** Error que se lanza cuando se agotan los 60 s de espera. */
export function errorTiempoAgotado() {
  return new DOMException('Se agotó el tiempo de espera del asistente', 'TimeoutError')
}

/**
 * Texto que ve el usuario cuando falla el pedido.
 * - 429, 503 y 504: el `mensaje` del cuerpo, completo (termina con los números
 *   de ayuda). No depende del header Retry-After.
 * - 504 sin cuerpo o los 60 s agotados: "tardó demasiado".
 * - Cualquier otro: un mensaje genérico.
 * El 401 no llega aquí: lo maneja el flujo de sesión vencida.
 */
export function mensajeDeError(error) {
  if (error?.name === 'TimeoutError') return MENSAJE_TARDO
  const delCuerpo = error?.mensaje && error.mensaje !== MENSAJE_INESPERADO ? error.mensaje : null
  if ([429, 503, 504].includes(error?.status) && delCuerpo) return delCuerpo
  if (error?.status === 504) return MENSAJE_TARDO
  return MENSAJE_GENERICO
}

// Números de ayuda que se pueden tocar: SAMU (106) y Línea 113
const NUMEROS_AYUDA = /\b(106|113)\b/

/**
 * Parte el texto en trozos de texto plano y números de ayuda:
 * "Llama al 106." -> ['Llama al ', { telefono: '106' }, '.']
 */
export function partirNumerosAyuda(texto) {
  return String(texto)
    .split(NUMEROS_AYUDA)
    .map((trozo, i) => (i % 2 === 1 ? { telefono: trozo } : trozo))
    .filter((trozo) => trozo !== '')
}

/** 1.25 -> "1.3 km" (con 1 decimal, como el móvil) */
export function formatoDistancia(km) {
  return `${Number(km).toFixed(1)} km`
}
