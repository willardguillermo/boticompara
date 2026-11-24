// Reglas puras de "Lo que dicen tus clientes" en Mi botica (GET /boticas/{id}/resenas, H87). Solo lectura.

const unDecimal = new Intl.NumberFormat('es-PE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

/** 4.3 -> "4.3"; null (sin reseñas) -> "—". El backend ya lo manda con 1 decimal. */
export function textoPromedio(promedio) {
  return promedio === null || promedio === undefined ? '—' : unDecimal.format(promedio)
}

/** 1 -> "1 reseña"; 12 -> "12 reseñas"; 0 -> "Sin reseñas". */
export function textoCantidad(total) {
  if (!total) return 'Sin reseñas'
  return total === 1 ? '1 reseña' : `${total} reseñas`
}

/**
 * Cuánto se pinta cada una de las 5 estrellas, de 0 a 100. 4.3 -> [100, 100, 100, 100, 30].
 * Se pinta la fracción exacta (no se redondea a media estrella) para que coincida con el número.
 */
export function rellenoEstrellas(valor) {
  const v = Math.min(5, Math.max(0, Number(valor) || 0))
  return [0, 1, 2, 3, 4].map((i) => Math.round(Math.min(1, Math.max(0, v - i)) * 100))
}

/** "4.3 de 5 estrellas" o "4 de 5 estrellas", para el lector de pantalla. */
export function etiquetaEstrellas(valor) {
  const v = Number(valor) || 0
  return `${Number.isInteger(v) ? v : unDecimal.format(v)} de 5 estrellas`
}

const fechaLima = new Intl.DateTimeFormat('es-PE', {
  timeZone: 'America/Lima', day: 'numeric', month: 'long', year: 'numeric',
})

/** "2026-11-13T18:20:00.123456Z" -> "13 de noviembre de 2026" (día en hora de Lima). */
export function formatoFechaResena(iso) {
  return fechaLima.format(new Date(iso))
}

/** true si después de la página `page` (desde 0) todavía quedan páginas. */
export function hayMasPaginas(page, totalPages) {
  return page + 1 < totalPages
}

/**
 * Agrega una página a la lista ya mostrada, sin repetir reseñas: si un comprador publicó una entre
 * "Ver más" y "Ver más", la más antigua de una página pasa a la siguiente y llegaría dos veces.
 */
export function unirPaginas(mostradas, nuevas) {
  const vistas = new Set(mostradas.map((r) => r.id))
  return [...mostradas, ...nuevas.filter((r) => !vistas.has(r.id))]
}
