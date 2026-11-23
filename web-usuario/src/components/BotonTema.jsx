import { useSyncExternalStore } from 'react'
import { alternarTema, suscribirTema, temaActual } from '../utils/tema.js'
import Icono from './Icono.jsx'

/** Interruptor de modo oscuro: luna en claro, sol en oscuro. */
export default function BotonTema() {
  const oscuro = useSyncExternalStore(suscribirTema, temaActual) === 'oscuro'
  return (
    <button
      type="button"
      className="btn btn-secundario boton-redondo"
      role="switch"
      aria-checked={oscuro}
      aria-label="Modo oscuro"
      title={oscuro ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
      onClick={alternarTema}
    >
      <Icono nombre={oscuro ? 'sol' : 'luna'} />
    </button>
  )
}
