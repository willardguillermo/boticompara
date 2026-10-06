import Icono from './Icono.jsx'

/** Se muestra cuando entra un usuario COMPRADOR: esta web es solo para dueños. */
export default function AvisoSoloMovil({ nombre, onVolver }) {
  return (
    <div className="bloqueado">
      <span className="icono-grande icono-info">
        <Icono nombre="celular" />
      </span>
      <h2>{nombre ? `Hola, ${nombre.split(' ')[0]}` : 'Cuenta de comprador'}</h2>
      <p className="texto-suave">
        Esta web es para <strong>dueños de botica</strong>. Como comprador, busca medicamentos y
        compara precios desde la <strong>app móvil de BotiCompara</strong>.
      </p>
      <button type="button" className="btn btn-secundario" onClick={onVolver}>
        Entrar con otra cuenta
      </button>
    </div>
  )
}
