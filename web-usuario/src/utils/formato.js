const soles = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' })

/** 8.5 -> "S/ 8.50" */
export function formatoSoles(valor) {
  return soles.format(Number(valor))
}
