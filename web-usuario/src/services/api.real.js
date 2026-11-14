// Implementación real: llama al backend Spring Boot (docs/API.md)
import { API_URL } from '../config.js'
import { ApiError, MENSAJE_INESPERADO } from './ApiError.js'
import { obtenerToken } from './sesion.js'

/**
 * `signal` (opcional) permite cancelar el pedido con un AbortController.
 * Si se cancela, se propaga el error original del navegador (AbortError o
 * el motivo que se pasó a abort()), no un ApiError: quien canceló sabe por qué.
 */
async function solicitar(metodo, ruta, cuerpo, { signal } = {}) {
  // Con FormData el navegador arma el Content-Type multipart con su boundary
  const esFormData = cuerpo instanceof FormData
  const headers = { Accept: 'application/json' }
  if (cuerpo !== undefined && !esFormData) headers['Content-Type'] = 'application/json'
  const token = obtenerToken()
  if (token) headers.Authorization = `Bearer ${token}`

  let respuesta
  try {
    respuesta = await fetch(`${API_URL}${ruta}`, {
      method: metodo,
      headers,
      body: cuerpo === undefined || esFormData ? cuerpo : JSON.stringify(cuerpo),
      signal,
    })
  } catch (error) {
    if (signal?.aborted) throw signal.reason ?? error
    throw new ApiError({
      status: 0,
      error: 'Network Error',
      mensaje: 'No se pudo conectar con el servidor. Revisa tu conexión o inténtalo más tarde.',
    })
  }

  if (respuesta.status === 204) return null
  const datos = await respuesta.json().catch(() => null)
  // Cancelado mientras se leía el cuerpo: json() falló, pero no es un cuerpo vacío
  signal?.throwIfAborted()

  if (!respuesta.ok) {
    throw new ApiError({
      status: datos?.status ?? respuesta.status,
      error: datos?.error ?? respuesta.statusText,
      mensaje: datos?.mensaje ?? MENSAJE_INESPERADO,
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
  actualizarMiBotica: (datos) => solicitar('PUT', '/boticas/mia', datos),
  subirLicencia: (archivo) => {
    const formulario = new FormData()
    formulario.append('archivo', archivo)
    return solicitar('POST', '/boticas/mia/licencia', formulario)
  },

  // 4. Catálogo
  listarMisProductos: (q = '', { stockBajo = false } = {}) => {
    const parametros = new URLSearchParams()
    if (q.trim()) parametros.set('q', q.trim())
    if (stockBajo) parametros.set('stockBajo', 'true')
    const consulta = parametros.toString()
    return solicitar('GET', `/boticas/mia/productos${consulta ? `?${consulta}` : ''}`)
  },
  crearProducto: (datos) => solicitar('POST', '/productos', datos),
  actualizarProducto: (id, datos) => solicitar('PUT', `/productos/${id}`, datos),
  eliminarProducto: (id) => solicitar('DELETE', `/productos/${id}`),
  quitarEnlaceMedicamento: (id) => solicitar('DELETE', `/productos/${id}/medicamento`),
  cargarProductosCsv: (archivo) => {
    const formulario = new FormData()
    formulario.append('archivo', archivo)
    return solicitar('POST', '/productos/carga-masiva', formulario)
  },
  buscarMedicamentos: (q) => solicitar('GET', `/medicamentos?q=${encodeURIComponent(q.trim())}`),

  // 8. Asistente (H68). El plazo de 60 s lo pone quien llama, con `signal`.
  enviarChat: ({ mensaje, historial, lat, lng }, { signal } = {}) =>
    solicitar(
      'POST',
      '/chat',
      { mensaje, historial, ...(lat != null && lng != null && { lat, lng }) },
      { signal },
    ),
}
