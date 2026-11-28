// H1, H15, H20: máquina de pasos del registro del dueño con el correo verificado (docs/API.md, sección 1).
// Sin React, para probarla con node --test. El token de verificación vive solo en este estado (en memoria):
// nunca en localStorage ni en la URL.
import { esErrorDeTitular } from './cuenta.js'

export const PASOS_REGISTRO = [
  { id: 'correo', titulo: 'Tu correo', descripcion: 'Te enviamos un código', hecho: 'Código enviado' },
  { id: 'codigo', titulo: 'Verificación', descripcion: 'Código de 6 dígitos', hecho: 'Correo verificado' },
  { id: 'cuenta', titulo: 'Tu cuenta', descripcion: 'Nombre y contraseña' },
  { id: 'botica', titulo: 'Tu botica', descripcion: 'RUC, dirección y mapa' },
  { id: 'licencia', titulo: 'Licencia', descripcion: 'Opcional: puedes subirla después' },
]

const ORDEN = PASOS_REGISTRO.map((p) => p.id)

/** Número del paso (1 a 5) para la lista de pasos. */
export const numeroDePaso = (id) => ORDEN.indexOf(id) + 1

export const ESTADO_INICIAL = { paso: 'correo', correo: '', token: null, aviso: '' }

/** Pasos a los que se puede volver con un clic: los ya hechos después de verificar el correo. */
export function puedeVolverA(estado, destino) {
  if (!estado.token) return false
  const actual = ORDEN.indexOf(estado.paso)
  const indice = ORDEN.indexOf(destino)
  return indice >= ORDEN.indexOf('cuenta') && indice < actual
}

/**
 * Acciones:
 * - codigoEnviado { correo }: paso 2. Un código nuevo anula el token anterior (contrato).
 * - cambiarCorreo: vuelve al paso 1 y olvida el token.
 * - verificado { token }: paso 3.
 * - cuentaLista, boticaLista: avanzan al paso siguiente (exigen el token).
 * - volver { paso }: a un paso ya hecho (ver puedeVolverA).
 * - error { paso, aviso }: el registro falló; va al paso que hay que corregir. Si es el correo, el token ya no
 *   sirve y se olvida; en los demás se conserva (el contrato dice que sigue sirviendo si la botica falló).
 */
export function registroReducer(estado, accion) {
  switch (accion.tipo) {
    case 'codigoEnviado':
      return { ...estado, paso: 'codigo', correo: accion.correo, token: null, aviso: '' }
    case 'cambiarCorreo':
      return { ...estado, paso: 'correo', token: null, aviso: '' }
    case 'verificado':
      return estado.paso === 'codigo' && accion.token
        ? { ...estado, paso: 'cuenta', token: accion.token, aviso: '' }
        : estado
    case 'cuentaLista':
      return estado.paso === 'cuenta' && estado.token ? { ...estado, paso: 'botica', aviso: '' } : estado
    case 'boticaLista':
      return estado.paso === 'botica' && estado.token ? { ...estado, paso: 'licencia', aviso: '' } : estado
    case 'volver':
      return puedeVolverA(estado, accion.paso) ? { ...estado, paso: accion.paso, aviso: '' } : estado
    case 'error':
      if (accion.paso === 'correo') return { ...estado, paso: 'correo', token: null, aviso: accion.aviso ?? '' }
      return ORDEN.includes(accion.paso) ? { ...estado, paso: accion.paso, aviso: accion.aviso ?? '' } : estado
    default:
      return estado
  }
}

/** El token de verificación venció, ya se usó o es de otro correo (400 de validar-ruc y de POST /auth/registro). */
export function esTokenVencido(err) {
  return err?.status === 400 && /verificaci[oó]n del correo venci/i.test(err?.mensaje ?? '')
}

/**
 * A qué paso hay que volver según el error de POST /auth/registro (docs/API.md):
 * - 'correo': el token venció o el correo ya está registrado (409).
 * - 'botica': RUC inválido, de una botica aprobada (409), RUC de persona que no es el titular (A7) o coordenadas.
 * - null: otro error; se muestra donde está.
 */
export function pasoTrasError(err) {
  if (!err || (err.status !== 400 && err.status !== 409)) return null
  const mensaje = err.mensaje ?? ''
  if (esTokenVencido(err)) return 'correo'
  if (/correo/i.test(mensaje) && err.status === 409) return 'correo'
  if (esErrorDeTitular(mensaje) || /ruc|botica|coordenada|latitud|longitud/i.test(mensaje)) return 'botica'
  return null
}

/** Cuerpo de POST /auth/registro para un dueño: la cuenta y la botica juntas. */
export function cuerpoRegistro({ token, correo, cuenta, botica }) {
  return {
    tokenVerificacion: token,
    nombre: cuenta.nombre.trim(),
    correo: correo.trim().toLowerCase(),
    password: cuenta.password,
    telefono: cuenta.telefono.trim() || null,
    rol: 'DUENO_BOTICA',
    botica,
  }
}
