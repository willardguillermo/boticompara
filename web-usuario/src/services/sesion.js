// Token y usuario en localStorage (sobreviven a recargar la página)
const CLAVE_TOKEN = 'boticompara.token'
const CLAVE_USUARIO = 'boticompara.usuario'

function leer(clave) {
  try {
    return localStorage.getItem(clave)
  } catch {
    return null
  }
}

export function obtenerToken() {
  return leer(CLAVE_TOKEN)
}

export function obtenerUsuario() {
  try {
    return JSON.parse(leer(CLAVE_USUARIO))
  } catch {
    return null
  }
}

export function guardarSesion(token, usuario) {
  localStorage.setItem(CLAVE_TOKEN, token)
  localStorage.setItem(CLAVE_USUARIO, JSON.stringify(usuario))
}

export function borrarSesion() {
  try {
    localStorage.removeItem(CLAVE_TOKEN)
    localStorage.removeItem(CLAVE_USUARIO)
  } catch {
    // sin almacenamiento disponible: no hay nada que borrar
  }
}
