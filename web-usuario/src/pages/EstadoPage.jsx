// H5 - Estado de la solicitud de la botica: PENDIENTE, APROBADO o RECHAZADO
import { useState } from 'react'
import { Link } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import Icono from '../components/Icono.jsx'
import { useBotica } from '../context/BoticaContext.jsx'
import { useToast } from '../context/ToastContext.jsx'

const ESTADOS = {
  PENDIENTE: {
    etiqueta: 'Pendiente de revisión',
    icono: 'reloj',
    titulo: 'Estamos revisando tu solicitud',
    texto: 'Un administrador de BotiCompara está validando los datos de tu botica. Mientras tanto, el catálogo está bloqueado.',
  },
  APROBADO: {
    etiqueta: 'Aprobada',
    icono: 'check',
    titulo: '¡Tu botica está aprobada!',
    texto: 'Ya puedes publicar tus productos. Los compradores los verán al buscar medicamentos en la app.',
  },
  RECHAZADO: {
    etiqueta: 'Rechazada',
    icono: 'equis',
    titulo: 'Tu solicitud fue rechazada',
    texto: 'Revisa el motivo y comunícate con el administrador de BotiCompara para corregir tus datos.',
  },
}

export function EstadoBadge({ estado }) {
  const info = ESTADOS[estado]
  return (
    <span className={`estado estado-${estado}`}>
      <Icono nombre={info?.icono ?? 'alerta'} /> {info?.etiqueta ?? estado}
    </span>
  )
}

export default function EstadoPage() {
  const { botica, recargar } = useBotica()
  const toast = useToast()
  const [actualizando, setActualizando] = useState(false)

  const info = ESTADOS[botica.estado]

  async function actualizar() {
    setActualizando(true)
    const anterior = botica.estado
    const nueva = await recargar()
    setActualizando(false)
    if (!nueva) return // el panel ya muestra el error
    toast(
      nueva.estado === anterior
        ? 'Estado actualizado: sin cambios por ahora.'
        : `Tu botica ahora está ${ESTADOS[nueva.estado]?.etiqueta.toLowerCase() ?? nueva.estado}.`,
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
    </>
  )
}
