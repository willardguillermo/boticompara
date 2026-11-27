// H20: espera para volver a pedir un código ("Reenviar código en 0:42").

export const ESPERA_POR_DEFECTO = 60

/**
 * Segundos que hay que esperar según un error de la API: el header Retry-After (en segundos o como fecha HTTP)
 * si vino y se pudo leer, si no `porDefecto`. Nunca menos de 1.
 */
export function segundosDeEspera(retryAfter, porDefecto = ESPERA_POR_DEFECTO, ahora = Date.now()) {
  const texto = String(retryAfter ?? '').trim()
  if (/^\d+$/.test(texto)) return Math.max(1, Number(texto))
  const fecha = texto ? Date.parse(texto) : NaN
  if (Number.isFinite(fecha)) return Math.max(1, Math.ceil((fecha - ahora) / 1000))
  return porDefecto
}

/** Segundos que faltan hasta `hasta` (milisegundos), redondeando hacia arriba; 0 si ya pasó. */
export function segundosRestantes(hasta, ahora = Date.now()) {
  if (!hasta) return 0
  return Math.max(0, Math.ceil((hasta - ahora) / 1000))
}

/** 75 → "1:15"; 5 → "0:05". */
export function formatoEspera(segundos) {
  const s = Math.max(0, Math.floor(segundos))
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

/** 38 → "00:38"; 75 → "01:15" (el reloj de "Reenviar en 00:38"). */
export function formatoReloj(segundos) {
  const s = Math.max(0, Math.floor(segundos))
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}
