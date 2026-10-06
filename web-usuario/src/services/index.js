// Punto único de acceso a la API. Las pantallas importan solo `api` de aquí
// y no saben si hablan con Spring Boot o con la simulación (VITE_USE_MOCK).
import { USE_MOCK } from '../config.js'
import { apiMock, registrarAyudaConsola } from './api.mock.js'
import { apiReal } from './api.real.js'
import { borrarSesion } from './sesion.js'

export { ApiError } from './ApiError.js'

export const EVENTO_SESION_EXPIRADA = 'boticompara:sesion-expirada'

const implementacion = USE_MOCK ? apiMock : apiReal
if (USE_MOCK) registrarAyudaConsola()

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
