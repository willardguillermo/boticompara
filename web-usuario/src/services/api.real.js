// Implementación real: llama al backend Spring Boot (docs/API.md)
import { API_URL } from '../config.js'
import { ApiError } from './ApiError.js'
import { obtenerToken } from './sesion.js'

async function solicitar(metodo, ruta, cuerpo) {
  const headers = { Accept: 'application/json' }
  if (cuerpo !== undefined) headers['Content-Type'] = 'application/json'
  const token = obtenerToken()
  if (token) headers.Authorization = `Bearer ${token}`

  let respuesta
  try {
    respuesta = await fetch(`${API_URL}${ruta}`, {
      method: metodo,
      headers,
      body: cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined,
    })
  } catch {
    throw new ApiError({
      status: 0,
      error: 'Network Error',
      mensaje: 'No se pudo conectar con el servidor. Revisa tu conexión o inténtalo más tarde.',
    })
  }

  if (respuesta.status === 204) return null
  const datos = await respuesta.json().catch(() => null)

  if (!respuesta.ok) {
    throw new ApiError({
      status: datos?.status ?? respuesta.status,
      error: datos?.error ?? respuesta.statusText,
      mensaje: datos?.mensaje ?? 'Ocurrió un error inesperado. Inténtalo nuevamente.',
      timestamp: datos?.timestamp,
    })
  }
  return datos
}

export const apiReal = {
  // 1. Autenticación
  login: (credenciales) => solicitar('POST', '/auth/login', credenciales),
  registro: (datos) => solicitar('POST', '/auth/registro', datos),

  // 2. Perfil
  obtenerPerfil: () => solicitar('GET', '/usuarios/me'),

  // 3. Botica
  validarRuc: (ruc) => solicitar('GET', `/boticas/validar-ruc/${encodeURIComponent(ruc)}`),
  registrarBotica: (datos) => solicitar('POST', '/boticas', datos),
  obtenerMiBotica: () => solicitar('GET', '/boticas/mia'),

  // 4. Catálogo
  listarMisProductos: (q = '') =>
    solicitar('GET', `/boticas/mia/productos${q.trim() ? `?q=${encodeURIComponent(q.trim())}` : ''}`),
  crearProducto: (datos) => solicitar('POST', '/productos', datos),
  actualizarProducto: (id, datos) => solicitar('PUT', `/productos/${id}`, datos),
  eliminarProducto: (id) => solicitar('DELETE', `/productos/${id}`),
}
