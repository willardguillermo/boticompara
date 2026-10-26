const soles = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' })

/** 8.5 -> "S/ 8.50" */
export function formatoSoles(valor) {
  return soles.format(Number(valor))
}

/** 2 400 000 -> "2.3 MB"; 84 000 -> "82 KB" */
export function formatoTamano(bytes) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
