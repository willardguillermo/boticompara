import { ESTADOS_PEDIDO, etiquetaReceta } from '../utils/pedidos.js'
import Icono from './Icono.jsx'

/** H77: distintivo de color según el estado del pedido. */
export default function EstadoPedidoBadge({ estado }) {
  const info = ESTADOS_PEDIDO[estado]
  return (
    <span className={`estado estado-pedido estado-pedido-${estado}`}>
      <Icono nombre={info?.icono ?? 'alerta'} /> {info?.etiqueta ?? estado}
    </span>
  )
}

/** H72: "Requiere receta" o "Receta retenida", con el mismo estilo que el estado. */
export function RecetaPedidoBadge({ pedido }) {
  const etiqueta = etiquetaReceta(pedido)
  if (!etiqueta) return null
  return (
    <span className="estado estado-pedido estado-pedido-receta">
      <Icono nombre="pedido" /> {etiqueta}
    </span>
  )
}
