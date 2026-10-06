import Icono from './Icono.jsx'

const ESTADOS_BOTICA = {
  PENDIENTE: { etiqueta: 'Pendiente de revisión', icono: 'reloj' },
  APROBADO: { etiqueta: 'Aprobada', icono: 'check' },
  RECHAZADO: { etiqueta: 'Rechazada', icono: 'equis' },
}

/** Etiqueta de color según el estado de la botica (amarillo, verde o rojo). */
export default function EstadoBadge({ estado }) {
  const info = ESTADOS_BOTICA[estado]
  return (
    <span className={`estado estado-${estado}`}>
      <Icono nombre={info?.icono ?? 'alerta'} /> {info?.etiqueta ?? estado}
    </span>
  )
}
