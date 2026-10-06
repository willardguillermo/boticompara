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
