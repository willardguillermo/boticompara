export const CORREO_VALIDO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** Teléfono opcional: solo dígitos, de 6 a 15. */
export function telefonoInvalido(telefono) {
  const valor = telefono.trim()
  return valor !== '' && !/^\d{6,15}$/.test(valor)
}

/** Props de accesibilidad para un control con posible error. */
export function propsError(id, error) {
  return { id, 'aria-invalid': Boolean(error), 'aria-describedby': `${id}-mensaje` }
}

// H4: licencia de funcionamiento (docs/API.md, POST /boticas/mia/licencia)
export const TIPOS_LICENCIA = {
  'application/pdf': ['pdf'],
  'image/jpeg': ['jpg', 'jpeg'],
  'image/png': ['png'],
}
export const MAX_BYTES_LICENCIA = 5 * 1024 * 1024
export const ACCEPT_LICENCIA = [
  ...Object.keys(TIPOS_LICENCIA),
  ...Object.values(TIPOS_LICENCIA).flat().map((ext) => `.${ext}`),
].join(',')

/** Devuelve el mensaje de error del archivo de licencia o '' si es válido. */
export function validarArchivoLicencia(archivo) {
  if (!archivo) return 'Selecciona un archivo.'
  const extension = archivo.name?.split('.').pop()?.toLowerCase() ?? ''
  const tipoValido = archivo.type
    ? archivo.type in TIPOS_LICENCIA
    : Object.values(TIPOS_LICENCIA).flat().includes(extension)
  if (!tipoValido) return 'Formato no permitido. Sube un PDF, JPG o PNG.'
  if (archivo.size === 0) return 'El archivo está vacío.'
  if (archivo.size > MAX_BYTES_LICENCIA) {
    // Redondeo hacia arriba para no mostrar "5.0 MB" en un archivo apenas mayor al límite
    const mb = (Math.ceil((archivo.size / (1024 * 1024)) * 10) / 10).toFixed(1)
    return `El archivo pesa ${mb} MB; el máximo es 5 MB.`
  }
  return ''
}
