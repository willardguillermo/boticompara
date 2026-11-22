import { useEffect, useState } from 'react'

/** Hora actual que se renueva cada minuto, para textos como "Vence en ..." o "hace 5 min". */
export function useAhora() {
  const [ahora, setAhora] = useState(() => Date.now())
  useEffect(() => {
    const intervalo = setInterval(() => setAhora(Date.now()), 60 * 1000)
    return () => clearInterval(intervalo)
  }, [])
  return ahora
}
