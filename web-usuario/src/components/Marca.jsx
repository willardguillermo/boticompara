import { Link } from 'react-router'
import Icono from './Icono.jsx'

export default function Marca({ a = '/panel' }) {
  return (
    <Link to={a} className="marca">
      <span className="marca-logo">
        <Icono nombre="cruz" />
      </span>
      BotiCompara
    </Link>
  )
}
