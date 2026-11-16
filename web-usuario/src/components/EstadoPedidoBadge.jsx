import { ESTADOS_PEDIDO } from '../utils/pedidos.js'
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
