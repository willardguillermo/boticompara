import { Navigate } from 'react-router'
import { useBotica } from '../context/BoticaContext.jsx'
import { hayCambioSinVer } from '../utils/estadoVisto.js'

/**
 * /panel: lleva a la pantalla que corresponde según la botica.
 * Si fue aprobada o rechazada y el dueño aún no lo vio, va a "Mi botica" para ver el aviso (H6).
 */
export function InicioPanel() {
  const { botica } = useBotica()
  if (!botica) return <Navigate to="/panel/botica/registro" replace />
  const destino = botica.estado === 'APROBADO' && !hayCambioSinVer(botica) ? '/panel/catalogo' : '/panel/estado'
  return <Navigate to={destino} replace />
}

/** Pantallas que necesitan botica: si aún no la registró, va directo al registro. */
export function ConBotica({ children }) {
  const { botica } = useBotica()
  if (!botica) return <Navigate to="/panel/botica/registro" replace />
  return children
}

/** H7: la corrección solo se permite con la botica RECHAZADA; si no, vuelve a "Mi botica". */
export function SoloRechazada({ children }) {
  const { botica } = useBotica()
  if (botica?.estado !== 'RECHAZADO') return <Navigate to="/panel/estado" replace />
  return children
}

/** Pantallas del catálogo que exigen botica APROBADO (H11); si no, vuelve a "Mi botica". */
export function SoloAprobada({ children }) {
  const { botica } = useBotica()
  if (botica?.estado !== 'APROBADO') return <Navigate to="/panel/estado" replace />
  return children
}

/** El registro de botica solo se muestra si todavía no tiene una (un dueño = una botica). */
export function SinBotica({ children }) {
  const { botica } = useBotica()
  if (botica) return <Navigate to="/panel/estado" replace />
  return children
}
