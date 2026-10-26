// H4 - Zona para soltar o elegir la licencia de funcionamiento en el registro por pasos.
// Solo elige el archivo: quien lo usa lo valida (validarArchivoLicencia) y decide cuándo subirlo.
import { useRef, useState } from 'react'
import { formatoTamano } from '../utils/formato.js'
import { ACCEPT_LICENCIA } from '../utils/validacion.js'
import Icono from './Icono.jsx'

export default function SelectorLicencia({ archivo, error, onElegir }) {
  const input = useRef(null)
  const [arrastrando, setArrastrando] = useState(false)

  const abrir = () => input.current?.click()

  function elegir(e) {
    const elegido = e.target.files?.[0]
    // Vaciarlo permite volver a elegir el mismo archivo
    e.target.value = ''
    if (elegido) onElegir(elegido)
  }

  function soltar(e) {
    e.preventDefault()
    setArrastrando(false)
    const soltado = e.dataTransfer.files?.[0]
    if (soltado) onElegir(soltado)
  }

  return (
    <div
      className={`zona-licencia${arrastrando ? ' zona-arrastrando' : ''}${error ? ' zona-error' : ''}`}
      onDragOver={(e) => {
        e.preventDefault()
        setArrastrando(true)
      }}
      onDragLeave={() => setArrastrando(false)}
      onDrop={soltar}
    >
      <input
        ref={input}
        type="file"
        accept={ACCEPT_LICENCIA}
        className="sr-only"
        tabIndex={-1}
        aria-hidden="true"
        onChange={elegir}
      />

      {archivo ? (
        <div className="archivo-elegido">
          <Icono nombre="check" />
          <span className="archivo-texto">
            <span className="archivo-nombre">{archivo.name}</span>
            <span className="archivo-tamano">{formatoTamano(archivo.size)}</span>
          </span>
          <button key="cambiar" type="button" className="btn btn-secundario btn-icono" onClick={abrir}>
            Cambiar
          </button>
        </div>
      ) : (
        <button
          key="elegir"
          type="button"
          className="zona-boton"
          onClick={abrir}
          aria-describedby={error ? 'licencia-mensaje' : undefined}
        >
          <Icono nombre="subir" />
          <span>
            Arrastra tu archivo aquí o <span className="zona-enlace">elige uno</span>
          </span>
          <span className="zona-ayuda">PDF, JPG o PNG · máximo 5 MB</span>
        </button>
      )}

      {error && (
        <span id="licencia-mensaje" className="error-campo" role="alert">
          {error}
        </span>
      )}
    </div>
  )
}
