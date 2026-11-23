import { readFileSync } from 'node:fs'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

/**
 * Cabeceras de vercel.json para probarlas con `npm run preview`. El backend es siempre el primer origen
 * de connect-src: aquí se cambia por el de VITE_API_URL (docs/despliegue/vercel.md).
 */
function cabecerasDeVercel(urlApi) {
  const { headers } = JSON.parse(readFileSync(new URL('./vercel.json', import.meta.url), 'utf8'))
  const backend = new URL(urlApi).origin
  return Object.fromEntries(
    headers
      .flatMap((regla) => regla.headers)
      .map(({ key, value }) => [key, value.replace(/connect-src \S+/, `connect-src ${backend}`)]),
  )
}

// https://vite.dev/config/
export default defineConfig(({ mode, isPreview }) => {
  const { VITE_API_URL } = loadEnv(mode, import.meta.dirname)
  return {
    plugins: [react()],
    // 5173 es el origen permitido por CORS en la API (docs/API.md)
    server: { port: 5173, strictPort: true },
    preview: isPreview ? { headers: cabecerasDeVercel(VITE_API_URL || 'http://localhost:8080/api') } : {},
  }
})
