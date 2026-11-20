// Estados de un pedido (sección 10 de docs/API.md) y textos para la pantalla de pedidos

/** Los 7 estados de chk_reserva_estado, en el orden del filtro (H81). */
export const ESTADOS_PEDIDO = {
  PENDIENTE: { etiqueta: 'Pendiente', plural: 'Pendientes', icono: 'reloj' },
  CONFIRMADA: { etiqueta: 'Confirmado', plural: 'Confirmados', icono: 'check' },
  LISTA: { etiqueta: 'Listo para recoger', plural: 'Listos para recoger', icono: 'caja' },
  ENTREGADA: { etiqueta: 'Entregado', plural: 'Entregados', icono: 'check' },
  RECHAZADA: { etiqueta: 'Rechazado', plural: 'Rechazados', icono: 'equis' },
  CANCELADA: { etiqueta: 'Cancelado por el comprador', plural: 'Cancelados', icono: 'equis' },
  VENCIDA: { etiqueta: 'Vencido', plural: 'Vencidos', icono: 'alerta' },
}

export const esEstadoPedido = (estado) => Object.hasOwn(ESTADOS_PEDIDO, estado)

const ACTIVOS = ['PENDIENTE', 'CONFIRMADA', 'LISTA']

/** H79: única transición válida desde cada estado (PATCH /pedidos/{id}/estado). */
const SIGUIENTE = {
  PENDIENTE: { estado: 'CONFIRMADA', texto: 'Confirmar', icono: 'check', exito: 'confirmado' },
  CONFIRMADA: { estado: 'LISTA', texto: 'Marcar listo para recoger', icono: 'caja', exito: 'marcado como listo para recoger' },
  LISTA: { estado: 'ENTREGADA', texto: 'Entregado', icono: 'check', exito: 'marcado como entregado', confirmar: true },
}

export const siguienteAccion = (pedido) => SIGUIENTE[pedido.estado] ?? null

/** H80: solo se rechaza desde PENDIENTE o CONFIRMADA (desde LISTA el comprador ya puede estar en camino). */
export const sePuedeRechazar = (pedido) => pedido.estado === 'PENDIENTE' || pedido.estado === 'CONFIRMADA'

/**
 * Regla nueva: un PENDIENTE solo se acepta si su "disponible" (stock menos las otras reservas
 * activas) alcanza. Si el backend aún no manda "disponible", no se bloquea: decide el 409.
 */
export const sinStockParaAceptar = (pedido) =>
  pedido.estado === 'PENDIENTE' && typeof pedido.disponible === 'number' && pedido.disponible < pedido.cantidad

export const TEXTO_SIN_STOCK = 'No hay stock suficiente para aceptar este pedido'
export const MOTIVO_SIN_STOCK = 'Sin stock disponible'

/**
 * H72: condición de venta del medicamento enlazado (condicionVenta del pedido). Solo las que
 * piden receta llevan etiqueta; SIN_RECETA y null (sin información) no muestran nada.
 * RECETA_RETENIDA no debería llegar como pedido (POST /reservas lo impide desde el 20/11/2026),
 * pero una reserva anterior a esa condición sí puede existir.
 */
const ETIQUETAS_RECETA = {
  // En la tabla va la versión corta por espacio; en el detalle, la completa
  CON_RECETA: { corta: 'Requiere receta', completa: 'Requiere receta médica' },
  RECETA_RETENIDA: { corta: 'Receta retenida', completa: 'Receta retenida' },
}

export const etiquetaReceta = (pedido, { completa = false } = {}) => {
  const etiquetas = ETIQUETAS_RECETA[pedido.condicionVenta]
  if (!etiquetas) return null
  return completa ? etiquetas.completa : etiquetas.corta
}

/** El recordatorio solo tiene sentido mientras el pedido aún se puede entregar. */
export const pedirReceta = (pedido) => etiquetaReceta(pedido) !== null && ACTIVOS.includes(pedido.estado)

export const TEXTO_RECETA = 'Pide la receta médica antes de entregar'

export const MOTIVO_MIN = 10
export const MOTIVO_MAX = 300

function duracion(ms) {
  const minutos = Math.max(1, Math.floor(ms / 60000))
  const dias = Math.floor(minutos / 1440)
  const horas = Math.floor((minutos % 1440) / 60)
  const resto = minutos % 60
  if (dias) return horas ? `${dias} d ${horas} h` : `${dias} d`
  if (horas) return resto ? `${horas} h ${resto} min` : `${horas} h`
  return `${resto} min`
}

/**
 * Cuánto falta para que venza un pedido activo.
 * Devuelve { texto, urgente } o null si el estado ya es final.
 * Con venceEn pasado y aún activo: el proceso de H84 lo marcará VENCIDA en unos minutos.
 */
export function tiempoParaVencer(pedido, ahora = Date.now()) {
  if (!ACTIVOS.includes(pedido.estado)) return null
  const falta = new Date(pedido.venceEn).getTime() - ahora
  if (falta <= 0) return { texto: 'Plazo vencido', urgente: true }
  return { texto: `Vence en ${duracion(falta)}`, urgente: falta <= 6 * 60 * 60 * 1000 }
}
