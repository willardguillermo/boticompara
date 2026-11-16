// Punto único de acceso a la API. Las pantallas importan solo `api` de aquí
// y no saben si hablan con Spring Boot o con la simulación (VITE_USE_MOCK).
import { USE_MOCK } from '../config.js'
import { apiReal } from './api.real.js'
import { borrarSesion } from './sesion.js'

export { ApiError } from './ApiError.js'

export const EVENTO_SESION_EXPIRADA = 'boticompara:sesion-expirada'

// El simulador se carga con import() solo en desarrollo. En un build de producción
// import.meta.env.DEV es false, la rama se descarta y api.mock.js no llega a dist/,
// aunque VITE_USE_MOCK diga true.
async function cargarImplementacion() {
  if (import.meta.env.DEV && USE_MOCK) {
    const { apiMock, registrarAyudaConsola } = await import('./api.mock.js')
    registrarAyudaConsola()
    return apiMock
  }
  return apiReal
}

const implementacion = await cargarImplementacion()

// En login un 401 significa credenciales incorrectas, no sesión vencida
const SIN_CONTROL_401 = new Set(['login'])

/** Envuelve cada función: si la API responde 401 se cierra la sesión. */
export const api = Object.fromEntries(
  Object.entries(implementacion).map(([nombre, fn]) => [
    nombre,
    async (...args) => {
      try {
        return await fn(...args)
      } catch (error) {
        if (error?.status === 401 && !SIN_CONTROL_401.has(nombre)) {
          borrarSesion()
          window.dispatchEvent(new Event(EVENTO_SESION_EXPIRADA))
        }
        throw error
      }
    },
  ]),
)

export const MODO_SIMULADO = USE_MOCK
