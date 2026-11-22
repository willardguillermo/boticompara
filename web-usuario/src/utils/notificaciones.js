// Reglas puras de las notificaciones del dueño (H103, sección 12 de docs/API.md)

const MINUTO = 60 * 1000
const HORA = 60 * MINUTO
const DIA = 24 * HORA

const fechaLima = new Intl.DateTimeFormat('es-PE', {
  timeZone: 'America/Lima', day: '2-digit', month: '2-digit', year: 'numeric',
})

/**
 * "hace un momento", "hace 5 min", "hace 3 h", "hace 2 días"; desde una semana, la fecha en Lima.
 * Una fecha futura (reloj del equipo atrasado) sale como "hace un momento".
 */
export function tiempoRelativo(iso, ahora = Date.now()) {
  const pasado = ahora - new Date(iso).getTime()
  if (pasado < MINUTO) return 'hace un momento'
  if (pasado < HORA) return `hace ${Math.floor(pasado / MINUTO)} min`
  if (pasado < DIA) return `hace ${Math.floor(pasado / HORA)} h`
  const dias = Math.floor(pasado / DIA)
  if (dias < 7) return dias === 1 ? 'hace 1 día' : `hace ${dias} días`
  return fechaLima.format(new Date(iso))
}

const MAXIMO_CONTADOR = 9

/** Número de la campana: 0 no se muestra (''), más de 9 sale como "9+" (contrato). */
export function textoContador(total) {
  if (!total || total < 0) return ''
  return total > MAXIMO_CONTADOR ? `${MAXIMO_CONTADOR}+` : String(total)
}

/** Nombre accesible del botón; sin el dato todavía (null) no inventa un número. */
export const etiquetaCampana = (total) => (total === null ? 'Notificaciones' : `Notificaciones, ${total} sin leer`)

/** "(3) BotiCompara"; sin no leídas devuelve el título de siempre. */
export function tituloPestana(total, titulo) {
  const contador = textoContador(total)
  return contador ? `(${contador}) BotiCompara` : titulo
}

/** Avisa solo si sube: nunca en la primera carga (anterior === null). */
export const subioContador = (anterior, actual) => anterior !== null && actual > anterior

/** Ruta que abre el detalle de ese pedido. */
export const rutaPedido = ({ pedidoId }) => `/panel/pedidos?pedido=${pedidoId}`

/** Ícono por tipo; un tipo que la web aún no conoce usa el de la campana. */
const ICONOS = { NUEVA_RESERVA: 'pedido', RESERVA_CANCELADA: 'equis', RESERVA_VENCIDA: 'reloj' }
export const iconoNotificacion = (tipo) => ICONOS[tipo] ?? 'campana'
