// H103 - Lista de notificaciones: la usan la campana y la actividad reciente del Inicio
import { Link } from 'react-router'
import { useNotificaciones } from '../context/NotificacionesContext.jsx'
import { useAhora } from '../hooks/useAhora.js'
import { unirPrecios } from '../utils/chat.js'
import { iconoNotificacion, rutaPedido, tiempoRelativo } from '../utils/notificaciones.js'
import Esqueleto from './Esqueleto.jsx'
import Icono from './Icono.jsx'

function Notificacion({ notificacion, ahora, onAbrir }) {
  const { tipo, titulo, mensaje, leida, creadoEn } = notificacion
  return (
    <li>
      <Link
        to={rutaPedido(notificacion)}
        className={`notificacion ${leida ? '' : 'notificacion-nueva'}`}
        onClick={() => onAbrir(notificacion)}
      >
        <span className="notificacion-icono">
          <Icono nombre={iconoNotificacion(tipo)} />
        </span>
        <span className="notificacion-texto">
          <span className="notificacion-titulo">
            {!leida && <span className="sr-only">Sin leer: </span>}
            {titulo}
          </span>
          <span className="notificacion-mensaje">{unirPrecios(mensaje)}</span>
          <time className="texto-suave" dateTime={creadoEn}>
            {tiempoRelativo(creadoEn, ahora)}
          </time>
        </span>
        {!leida && <span className="notificacion-punto" aria-hidden="true" />}
      </Link>
    </li>
  )
}

function EsqueletoLista({ filas }) {
  return (
    <div role="status">
      <span className="sr-only">Cargando notificaciones</span>
      {Array.from({ length: filas }, (_, i) => (
        <div key={i} className="notificacion" aria-hidden="true">
          <Esqueleto ancho="2.25rem" alto="2.25rem" />
          <span className="notificacion-texto">
            <Esqueleto ancho="60%" />
            <Esqueleto ancho="85%" alto="0.8em" />
            <Esqueleto ancho="30%" alto="0.8em" />
          </span>
        </div>
      ))}
    </div>
  )
}

/**
 * `maximo` limita cuántas se muestran; `onAbrir` se llama después de marcarla como leída.
 * `textoError` es el mensaje de cada pantalla: nunca el del servidor.
 * El backend solo devuelve los últimos 7 días; aquí no se filtra.
 */
export default function ListaNotificaciones({ maximo, onAbrir, textoError, filasEsqueleto = 3 }) {
  const { lista, errorLista, cargarLista, marcarLeida } = useNotificaciones()
  const ahora = useAhora()

  if (errorLista && !lista) {
    return (
      <div className="notificaciones-vacio" role="status">
        <p>{textoError}</p>
        <button type="button" className="boton-texto" onClick={cargarLista}>
          Reintentar
        </button>
      </div>
    )
  }
  if (lista === null) return <EsqueletoLista filas={filasEsqueleto} />
  if (lista.length === 0) {
    return (
      <div className="notificaciones-vacio">
        <Icono nombre="campana" />
        <p>No tienes notificaciones de los últimos 7 días.</p>
      </div>
    )
  }

  function abrir(notificacion) {
    marcarLeida(notificacion)
    onAbrir?.()
  }

  return (
    <ul className="notificaciones-lista">
      {lista.slice(0, maximo).map((n) => (
        <Notificacion key={n.id} notificacion={n} ahora={ahora} onAbrir={abrir} />
      ))}
    </ul>
  )
}
