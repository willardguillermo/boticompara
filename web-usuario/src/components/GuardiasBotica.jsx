import { Navigate } from 'react-router'
import { useBotica } from '../context/BoticaContext.jsx'

/**
 * /panel: lleva a la pantalla que corresponde según la botica.
 * Aprobada: Inicio (H103). Pendiente o rechazada: "Mi botica". Las dos muestran el aviso de cambio (H6).
 */
export function InicioPanel() {
  const { botica } = useBotica()
  if (!botica) return <Navigate to="/panel/botica/registro" replace />
  return <Navigate to={botica.estado === 'APROBADO' ? '/panel/inicio' : '/panel/estado'} replace />
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

/** Pantallas que exigen botica APROBADO (carga masiva H11, Inicio H103); si no, vuelve a "Mi botica". */
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
