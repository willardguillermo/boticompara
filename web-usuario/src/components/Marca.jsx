import { Link } from 'react-router'
import Icono from './Icono.jsx'
import GradientText from './reactbits/GradientText.jsx'

// degradado: el nombre lleva un degradado muy lento (Gradient Text de React Bits); se usa en
// login y registro. Con movimiento reducido el degradado queda quieto.
export default function Marca({ a = '/panel', degradado = false }) {
  return (
    <Link to={a} className="marca">
      <span className="marca-logo">
        <Icono nombre="cruz" />
      </span>
      {degradado ? <GradientText animationSpeed={20}>BotiCompara</GradientText> : 'BotiCompara'}
    </Link>
  )
}
