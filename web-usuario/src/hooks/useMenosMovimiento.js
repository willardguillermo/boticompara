import { useSyncExternalStore } from 'react'

// true si el sistema pide reducir el movimiento (prefers-reduced-motion: reduce); sigue los cambios.
const CONSULTA = '(prefers-reduced-motion: reduce)'

function suscribir(alCambiar) {
  const media = window.matchMedia(CONSULTA)
  media.addEventListener('change', alCambiar)
  return () => media.removeEventListener('change', alCambiar)
}

const leer = () => window.matchMedia(CONSULTA).matches

export default function useMenosMovimiento() {
  return useSyncExternalStore(suscribir, leer, () => false)
}
