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

const fechaHoraLima = new Intl.DateTimeFormat('es-PE', {
  timeZone: 'America/Lima', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
})

/** "2026-11-12T15:30:00Z" -> "12/11/2026, 10:30 a. m." (hora de Lima) */
export function formatoFechaHora(iso) {
  return fechaHoraLima.format(new Date(iso))
}
