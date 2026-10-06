import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // 5173 es el origen permitido por CORS en la API (docs/API.md)
  server: { port: 5173, strictPort: true },
})
