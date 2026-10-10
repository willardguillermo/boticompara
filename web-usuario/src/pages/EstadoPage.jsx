// H5 - Estado de la solicitud de la botica: PENDIENTE, APROBADO o RECHAZADO
// H4 - Carga de la licencia de funcionamiento
import { useState } from 'react'
import { Link } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import Campo from '../components/Campo.jsx'
import EstadoBadge from '../components/EstadoBadge.jsx'
import Icono from '../components/Icono.jsx'
import { useBotica } from '../context/BoticaContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { api } from '../services/index.js'
import { ACCEPT_LICENCIA, propsError, validarArchivoLicencia } from '../utils/validacion.js'

const TEXTOS = {
  PENDIENTE: {
    cambio: 'pendiente de revisión',
    titulo: 'Estamos revisando tu solicitud',
    texto: 'Un administrador de BotiCompara está validando los datos de tu botica. Mientras tanto, el catálogo está bloqueado.',
  },
  APROBADO: {
    cambio: 'aprobada',
    titulo: '¡Tu botica está aprobada!',
    texto: 'Ya puedes publicar tus productos. Los compradores los verán al buscar medicamentos en la app.',
  },
  RECHAZADO: {
    cambio: 'rechazada',
    titulo: 'Tu solicitud fue rechazada',
    texto: 'Revisa el motivo y comunícate con el administrador de BotiCompara para corregir tus datos.',
  },
}

export default function EstadoPage() {
  const { botica, recargar } = useBotica()
  const toast = useToast()
  const [actualizando, setActualizando] = useState(false)

  const info = TEXTOS[botica.estado]

  async function actualizar() {
    setActualizando(true)
    const anterior = botica.estado
    const nueva = await recargar()
    setActualizando(false)
    if (!nueva) return // el panel ya muestra el error
    toast(
      nueva.estado === anterior
        ? 'Estado actualizado: sin cambios por ahora.'
        : `Tu botica ahora está ${TEXTOS[nueva.estado]?.cambio ?? nueva.estado}.`,
      nueva.estado === 'RECHAZADO' ? 'error' : 'exito',
    )
  }

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Mi botica</h1>
          <p className="texto-suave">Estado de tu solicitud de registro en BotiCompara.</p>
        </div>
        <button type="button" className="btn btn-secundario" onClick={actualizar} disabled={actualizando}>
          <Icono nombre="actualizar" /> {actualizando ? 'Actualizando...' : 'Actualizar estado'}
        </button>
      </div>

      <section className={`tarjeta estado-tarjeta borde-${botica.estado}`} aria-live="polite">
        <div className="estado-cabecera">
          <h2>{botica.nombreComercial}</h2>
          <EstadoBadge estado={botica.estado} />
        </div>

        <h3>{info?.titulo}</h3>
        <p className="texto-suave">{info?.texto}</p>

        {botica.estado === 'RECHAZADO' && (
          <div className="motivo">
            <Alerta tipo="error">
              <p>
                <strong>Motivo del rechazo:</strong> {botica.motivoRechazo || 'No se indicó un motivo.'}
              </p>
            </Alerta>
          </div>
        )}

        {botica.estado === 'APROBADO' && (
          <Link to="/panel/catalogo" className="btn btn-primario">
            <Icono nombre="caja" /> Ir a mi catálogo
          </Link>
        )}

        <dl className="datos-botica">
          <div>
            <dt>RUC</dt>
            <dd>{botica.ruc}</dd>
          </div>
          <div>
            <dt>Razón social</dt>
            <dd>{botica.razonSocial}</dd>
          </div>
          <div>
            <dt>Dirección</dt>
            <dd>
              {botica.direccion}, {botica.distrito}
            </dd>
          </div>
          <div>
            <dt>Teléfono</dt>
            <dd>{botica.telefono || '—'}</dd>
          </div>
        </dl>
      </section>

      <SeccionLicencia tieneLicencia={botica.tieneLicencia} />
    </>
  )
}

function SeccionLicencia({ tieneLicencia }) {
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
