export default function Cargando({ texto = 'Cargando...' }) {
  return (
    <div className="cargando" role="status">
      <span className="spinner" aria-hidden="true" />
      {texto}
    </div>
  )
}
