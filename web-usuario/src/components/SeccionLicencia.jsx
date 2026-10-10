// H4 - Carga de la licencia de funcionamiento (PDF, JPG o PNG, máximo 5 MB).
// Se usa en "Mi botica" y en la corrección de una botica rechazada (H7).
import { useState } from 'react'
import { useBotica } from '../context/BoticaContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { api } from '../services/index.js'
import { ACCEPT_LICENCIA, propsError, validarArchivoLicencia } from '../utils/validacion.js'
import Alerta from './Alerta.jsx'
import Campo from './Campo.jsx'

export default function SeccionLicencia({ tieneLicencia }) {
  const { recargar } = useBotica()
  const toast = useToast()
  const [reemplazando, setReemplazando] = useState(false)
  const [archivo, setArchivo] = useState(null)
  const [errorArchivo, setErrorArchivo] = useState('')
  const [errorApi, setErrorApi] = useState('')
  const [subiendo, setSubiendo] = useState(false)
  // Cambiar la key vacía el <input type="file">, que no se puede controlar con value
  const [claveInput, setClaveInput] = useState(0)

  const mostrarSelector = !tieneLicencia || reemplazando

  function limpiar() {
    setArchivo(null)
    setErrorArchivo('')
    setErrorApi('')
    setClaveInput((k) => k + 1)
  }

  function elegir(evento) {
    const elegido = evento.target.files?.[0] ?? null
    setArchivo(elegido)
    setErrorApi('')
    setErrorArchivo(elegido ? validarArchivoLicencia(elegido) : '')
  }

  function cancelar() {
    limpiar()
    setReemplazando(false)
  }

  async function subir(evento) {
    evento.preventDefault()
    const error = validarArchivoLicencia(archivo)
    if (error) {
      setErrorArchivo(error)
      return
    }
    setSubiendo(true)
    setErrorApi('')
    try {
      const respuesta = await api.subirLicencia(archivo)
      toast(respuesta?.mensaje ?? 'Licencia cargada correctamente')
      limpiar()
      setReemplazando(false)
      await recargar()
    } catch (err) {
      // Con 401 la sesión ya se cerró y se redirige al login
      if (err.status !== 401) setErrorApi(err.mensaje ?? 'No se pudo subir la licencia.')
    } finally {
      setSubiendo(false)
    }
  }

  return (
    <section className="tarjeta seccion-licencia" aria-labelledby="titulo-licencia">
      <h2 id="titulo-licencia">Licencia de funcionamiento</h2>

      {tieneLicencia ? (
        <div className="licencia-cargada">
          <Alerta tipo="exito">
            <p>
              <strong>✓ Licencia cargada</strong>
            </p>
          </Alerta>
          {!reemplazando && (
            <button type="button" className="btn btn-secundario" onClick={() => setReemplazando(true)}>
              Reemplazar
            </button>
          )}
        </div>
      ) : (
        <Alerta tipo="aviso">
          <p>
            El administrador de BotiCompara necesita tu licencia de funcionamiento para aprobar tu botica.
            Súbela en PDF, JPG o PNG.
          </p>
        </Alerta>
      )}

      {mostrarSelector && (
        <form className="formulario" onSubmit={subir} noValidate>
          <Campo
            id="licencia"
            etiqueta={tieneLicencia ? 'Nuevo archivo de licencia' : 'Archivo de licencia'}
            error={errorArchivo}
            ayuda="PDF, JPG o PNG, máximo 5 MB."
          >
            <input
              key={claveInput}
              type="file"
              accept={ACCEPT_LICENCIA}
              onChange={elegir}
              disabled={subiendo}
              {...propsError('licencia', errorArchivo)}
            />
          </Campo>

          <Alerta tipo="error">{errorApi && <p>{errorApi}</p>}</Alerta>

          <div className="acciones-form">
            {reemplazando && (
              <button type="button" className="btn btn-secundario" onClick={cancelar} disabled={subiendo}>
                Cancelar
              </button>
            )}
            <button type="submit" className="btn btn-primario" disabled={subiendo || !archivo || Boolean(errorArchivo)}>
              {subiendo ? 'Subiendo...' : 'Subir licencia'}
            </button>
          </div>
        </form>
      )}
    </section>
  )
}
