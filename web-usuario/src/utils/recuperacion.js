// H17: validación de la recuperación de contraseña (docs/API.md, POST /auth/recuperar y /auth/restablecer)
import { CORREO_VALIDO } from './validacion.js'

export const LARGO_CODIGO = 6
export const MIN_PASSWORD = 8

/** Deja solo dígitos y como máximo 6, para que el campo no acepte letras ni pegados largos. */
export function limpiarCodigo(texto) {
  return String(texto ?? '').replace(/\D/g, '').slice(0, LARGO_CODIGO)
}

export function codigoValido(codigo) {
  return /^\d{6}$/.test(String(codigo ?? ''))
}

/** Paso 1: devuelve el mensaje de error del correo o '' si es válido. */
export function validarCorreoRecuperacion(correo) {
  const valor = String(correo ?? '').trim()
  if (!valor) return 'Ingresa tu correo.'
  if (!CORREO_VALIDO.test(valor)) return 'El correo no tiene un formato válido.'
  return ''
}

/** Paso 2: devuelve los errores por campo; un objeto vacío si todo es válido. */
export function validarRestablecer({ codigo, password, confirmacion }) {
  const errores = {}
  if (!codigo) errores.codigo = 'Ingresa el código que te enviamos.'
  else if (!codigoValido(codigo)) errores.codigo = 'El código tiene 6 dígitos.'

  if (!password) errores.password = 'Ingresa tu nueva contraseña.'
  else if (password.length < MIN_PASSWORD) errores.password = `Debe tener al menos ${MIN_PASSWORD} caracteres.`

  if (!confirmacion) errores.confirmacion = 'Repite tu nueva contraseña.'
  else if (password !== confirmacion) errores.confirmacion = 'Las contraseñas no coinciden.'
  return errores
}
