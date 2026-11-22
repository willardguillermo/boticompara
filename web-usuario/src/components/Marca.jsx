import { Link } from 'react-router'
import Icono from './Icono.jsx'
import GradientText from './reactbits/GradientText.jsx'

// degradado: el nombre lleva un degradado muy lento (Gradient Text de React Bits); se usa en
// login y registro. Con movimiento reducido el degradado queda quieto.
// Tres tonos de index.css (verde azulado, cian y verde) con 3:1 o más contra el fondo: el cambio de
// matiz se nota sobre el gris verdoso sin perder contraste.
const COLORES_DEGRADADO = ['var(--rb-gradiente-1)', 'var(--rb-gradiente-2)', 'var(--rb-gradiente-3)']

export default function Marca({ a = '/panel', degradado = false }) {
  return (
    <Link to={a} className="marca">
      <span className="marca-logo">
        <Icono nombre="cruz" />
      </span>
      {degradado ? <GradientText colors={COLORES_DEGRADADO} animationSpeed={14} glow={0.18}>BotiCompara</GradientText> : 'BotiCompara'}
    </Link>
  )
}
