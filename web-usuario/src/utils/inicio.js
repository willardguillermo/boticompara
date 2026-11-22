// Reglas puras del Inicio del dueño (H103). Todo se calcula con la hora de Lima, no la del equipo.

const horaLima = new Intl.DateTimeFormat('en-US', { timeZone: 'America/Lima', hour: 'numeric', hourCycle: 'h23' })
const diaLima = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit' })

/** De 5:00 a 11:59 días, de 12:00 a 18:59 tardes y el resto noches. */
export function saludo(fecha = new Date()) {
  const hora = Number(horaLima.format(fecha))
  if (hora >= 5 && hora < 12) return 'Buenos días'
  if (hora >= 12 && hora < 19) return 'Buenas tardes'
  return 'Buenas noches'
}

/** "Rosa Quispe" -> "Rosa" */
export const primerNombre = (nombre) => String(nombre ?? '').trim().split(/\s+/)[0]

export const esHoyEnLima = (iso, ahora = new Date()) => diaLima.format(new Date(iso)) === diaLima.format(ahora)

/** Pedidos LISTA cuyo plazo para recoger termina hoy. */
export const porRecogerHoy = (pedidos, ahora = new Date()) => pedidos.filter((p) => esHoyEnLima(p.venceEn, ahora)).length

// GET /pedidos trae como máximo 100: con 100 puede haber más
export const MAX_PEDIDOS_LISTA = 100
