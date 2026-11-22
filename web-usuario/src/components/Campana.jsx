// H103 - Campana de notificaciones de la cabecera: contador, panel y aviso flotante
import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { Link } from 'react-router'
import { useNotificaciones } from '../context/NotificacionesContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { unirPrecios } from '../utils/chat.js'
import { etiquetaCampana, rutaPedido, textoContador } from '../utils/notificaciones.js'
import Icono from './Icono.jsx'
import ListaNotificaciones from './ListaNotificaciones.jsx'

const DURACION_AVISO_MS = 10 * 1000

function PanelNotificaciones({ id, onCerrar, onSalir }) {
  const { total, lista, cargarLista, marcarTodas } = useNotificaciones()
  const toast = useToast()
  const panel = useRef(null)
  const idTitulo = useId()
  const idPeriodo = useId()
  const haySinLeer = total > 0 || lista?.some((n) => !n.leida)

  useEffect(() => {
    panel.current.focus()
    cargarLista()
  }, [cargarLista])

  // En la fase de captura: con el asistente abierto, Escape cierra solo este panel
  useEffect(() => {
    function alPresionar(e) {
      if (e.key !== 'Escape') return
      e.preventDefault()
      e.stopPropagation()
      onCerrar()
    }
    document.addEventListener('keydown', alPresionar, true)
    return () => document.removeEventListener('keydown', alPresionar, true)
  }, [onCerrar])

  async function leerTodas() {
    panel.current.focus() // el botón desaparece: el foco no debe perderse
    if (!(await marcarTodas())) toast('No se pudieron marcar las notificaciones como leídas.', 'error')
  }

  return (
    <section
      ref={panel}
      id={id}
      className="campana-panel"
      role="dialog"
      aria-labelledby={idTitulo}
      aria-describedby={idPeriodo}
      tabIndex={-1}
      onBlur={(e) => e.relatedTarget && !e.currentTarget.parentNode.contains(e.relatedTarget) && onSalir()}
    >
      <div className="campana-cabecera">
        <div>
          <h2 id={idTitulo}>Notificaciones</h2>
          <p id={idPeriodo} className="texto-suave">
            Últimos 7 días
          </p>
        </div>
        {haySinLeer && (
          <button type="button" className="boton-texto" onClick={leerTodas}>
            Marcar todas como leídas
          </button>
        )}
      </div>
      <div className="campana-cuerpo">
        <ListaNotificaciones onAbrir={onCerrar} textoError="No pudimos cargar tus notificaciones." />
      </div>
    </section>
  )
}

/**
 * Aviso flotante de algo nuevo. Se va solo a los 10 s, salvo con el puntero o el foco encima.
 * `soloTexto`: sin enlace ni botón, porque no se ve (solo lo lee el lector de pantalla).
 */
function AvisoNuevo({ aviso, soloTexto, onCerrar }) {
  const { marcarLeida } = useNotificaciones()
  const [enPausa, setEnPausa] = useState(false)
  const { cantidad, notificacion } = aviso

  useEffect(() => {
    if (enPausa) return undefined
    const espera = setTimeout(onCerrar, DURACION_AVISO_MS)
    return () => clearTimeout(espera)
  }, [enPausa, onCerrar])

  return (
    <div
      className="aviso-notificacion"
      onMouseEnter={() => setEnPausa(true)}
      onMouseLeave={() => setEnPausa(false)}
      onFocus={() => setEnPausa(true)}
      onBlur={() => setEnPausa(false)}
    >
      <span className="notificacion-icono">
        <Icono nombre="campana" />
      </span>
      <div className="notificacion-texto">
        <span className="notificacion-titulo">
          {notificacion?.titulo ??
            (cantidad === 1 ? 'Tienes una notificación nueva' : `Tienes ${cantidad} notificaciones nuevas`)}
        </span>
        {notificacion && (
          <>
            <span className="notificacion-mensaje">{unirPrecios(notificacion.mensaje)}</span>
            {!soloTexto && (
              <Link to={rutaPedido(notificacion)} onClick={() => marcarLeida(notificacion)}>
                Ver pedido
              </Link>
            )}
          </>
        )}
      </div>
      {!soloTexto && (
        <button type="button" className="aviso-cerrar" onClick={onCerrar} aria-label="Cerrar aviso">
          <Icono nombre="equis" />
        </button>
      )}
    </div>
  )
}

export default function Campana() {
  const { total, aviso, cerrarAviso } = useNotificaciones()
  const [abierta, setAbierta] = useState(false)
  const contenedor = useRef(null)
  const boton = useRef(null)
  const idPanel = useId()
  const contador = textoContador(total)

  const salir = useCallback(() => setAbierta(false), [])
  const cerrar = useCallback(() => {
    setAbierta(false)
    boton.current?.focus()
  }, [])

  // Un clic fuera cierra sin mover el foco: ya está donde se hizo clic
  useEffect(() => {
    if (!abierta) return undefined
    const alPulsar = (e) => !contenedor.current.contains(e.target) && salir()
    document.addEventListener('pointerdown', alPulsar)
    return () => document.removeEventListener('pointerdown', alPulsar)
  }, [abierta, salir])

  return (
    <div className="campana" ref={contenedor}>
      <button
        ref={boton}
        type="button"
        className="btn btn-secundario boton-redondo campana-boton"
        aria-label={etiquetaCampana(total)}
        aria-haspopup="dialog"
        aria-expanded={abierta}
        aria-controls={abierta ? idPanel : undefined}
        onClick={() => (abierta ? cerrar() : setAbierta(true))}
      >
        <Icono nombre="campana" />
        {contador && (
          <span className="campana-contador" aria-hidden="true">
            {contador}
          </span>
        )}
      </button>
      {abierta && <PanelNotificaciones id={idPanel} onCerrar={cerrar} onSalir={salir} />}
      {/* Siempre montado para que el lector de pantalla anuncie lo que aparece dentro. Con el panel
          abierto solo se anuncia: lo nuevo ya se ve en la lista. */}
      <div className={abierta ? 'sr-only' : 'campana-aviso'} aria-live="polite">
        {aviso && <AvisoNuevo key={aviso.id} aviso={aviso} soloTexto={abierta} onCerrar={cerrarAviso} />}
      </div>
    </div>
  )
}
