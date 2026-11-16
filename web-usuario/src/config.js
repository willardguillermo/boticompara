// Configuración leída de las variables de entorno de Vite (.env)
export const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080/api').replace(/\/$/, '')
// El simulador solo se permite en desarrollo: en un build de producción siempre es false
export const USE_MOCK = import.meta.env.DEV && import.meta.env.VITE_USE_MOCK === 'true'
