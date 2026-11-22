/** Barra gris que ocupa el lugar de un texto mientras carga. Decorativa: quien la usa anuncia la carga. */
export default function Esqueleto({ ancho = '100%', alto = '1em' }) {
  return <span className="esqueleto" style={{ width: ancho, height: alto }} aria-hidden="true" />
}
