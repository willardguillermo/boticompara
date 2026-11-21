// H5 - Estado de la solicitud de la botica: PENDIENTE, APROBADO o RECHAZADO
// H4 - Carga de la licencia de funcionamiento (SeccionLicencia)
// H7 - Si fue rechazada, enlace para corregir y reenviar
// H6 - Aviso destacado, una sola vez por cambio, cuando la botica se aprueba o se rechaza
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import EstadoBadge from '../components/EstadoBadge.jsx'
import Icono from '../components/Icono.jsx'
import StarBorder from '../components/reactbits/StarBorder.jsx'
import SeccionLicencia from '../components/SeccionLicencia.jsx'
import { useBotica } from '../context/BoticaContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { esCambioSinVer, guardarEstadoVisto, leerEstadoVisto } from '../utils/estadoVisto.js'

// Botón principal con una estrella que recorre el borde despacio (Star Border de React Bits).
// Conserva el aspecto de .btn-primario; con movimiento reducido la estrella queda quieta.
function BotonPrincipal({ a, children }) {
  return (
    <StarBorder as={Link} to={a} className="btn btn-primario" radius={10} duration={9} hover="none" clickPulse={false}>
      {children}
    </StarBorder>
  )
}

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
    texto: 'Revisa el motivo, corrige tus datos y reenvía la solicitud para que la revisemos de nuevo.',
  },
}

const AVISOS_CAMBIO = {
  APROBADO: { tipo: 'exito', icono: 'check', texto: '¡Tu botica fue aprobada! Ya puedes publicar tu catálogo.' },
  RECHAZADO: { tipo: 'aviso', icono: 'alerta', texto: 'Tu solicitud fue observada: revisa el motivo y corrígela.' },
}

export default function EstadoPage() {
  const { botica, recargar } = useBotica()
  const toast = useToast()
  const [actualizando, setActualizando] = useState(false)
  // Estado que el dueño ya había visto al entrar (H6) y estado cuyo aviso cerró
  const [vistoAlEntrar] = useState(() => leerEstadoVisto(botica.id))
  const [cerrado, setCerrado] = useState(null)

  const info = TEXTOS[botica.estado]
  const avisoCambio =
    esCambioSinVer(botica.estado, vistoAlEntrar) && botica.estado !== cerrado ? AVISOS_CAMBIO[botica.estado] : null

  // Lo que se muestra queda como visto: el aviso no se repite en la próxima visita
  useEffect(() => {
    guardarEstadoVisto(botica.id, botica.estado)
  }, [botica.id, botica.estado])

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

      {avisoCambio && (
        <div className={`alerta alerta-${avisoCambio.tipo} aviso-cambio`} role="status">
          <Icono nombre={avisoCambio.icono} />
          <p>
            <strong>{avisoCambio.texto}</strong>
          </p>
          <button
            type="button"
            className="aviso-cerrar"
            onClick={() => setCerrado(botica.estado)}
            aria-label="Cerrar aviso"
          >
            <Icono nombre="equis" />
          </button>
        </div>
      )}

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
            <BotonPrincipal a="/panel/botica/corregir">
              <Icono nombre="editar" /> Corregir y reenviar
            </BotonPrincipal>
          </div>
        )}

        {botica.estado === 'APROBADO' && (
          <BotonPrincipal a="/panel/catalogo">
            <Icono nombre="caja" /> Ir a mi catálogo
          </BotonPrincipal>
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
