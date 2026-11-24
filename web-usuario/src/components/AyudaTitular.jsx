// A7: junto al 400 "Este RUC pertenece a una persona...", dice dónde se corrige el nombre de la cuenta
import { Link } from 'react-router'
import { esErrorDeTitular } from '../utils/cuenta.js'

export default function AyudaTitular({ mensaje }) {
  if (!esErrorDeTitular(mensaje)) return null
  return (
    <p className="ayuda-titular">
      ¿Tu nombre no coincide con el de SUNAT? <Link to="/panel/cuenta">Corrígelo en Mi cuenta</Link> y vuelve a
      enviar la solicitud.
    </p>
  )
}
