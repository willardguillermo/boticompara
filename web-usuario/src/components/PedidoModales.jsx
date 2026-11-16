// Diálogos de la pantalla de pedidos (sección 10 de docs/API.md)
// H78 - Detalle de un pedido (GET /pedidos/{id})
// H79 - Confirmación antes de "Entregado", porque descuenta stock
// H80 - Rechazo con motivo obligatorio de 10 a 300 caracteres
import { useEffect, useState } from 'react'
import { api } from '../services/index.js'
import { formatoFechaHora, formatoSoles } from '../utils/formato.js'
import {
  MOTIVO_MAX,
  MOTIVO_MIN,
  MOTIVO_SIN_STOCK,
  TEXTO_SIN_STOCK,
  sePuedeRechazar,
  siguienteAccion,
  sinStockParaAceptar,
  tiempoParaVencer,
} from '../utils/pedidos.js'
import Alerta from './Alerta.jsx'
import EstadoPedidoBadge from './EstadoPedidoBadge.jsx'
import Icono from './Icono.jsx'
import Modal from './Modal.jsx'

/** "Vence en 5 h 20 min" para los pedidos activos; "—" en los estados finales. */
export function PlazoPedido({ pedido, ahora }) {
  const plazo = tiempoParaVencer(pedido, ahora)
  if (!plazo) return <span className="texto-suave">—</span>
  return (
    <span className={plazo.urgente ? 'plazo-urgente' : undefined} title={`Vence el ${formatoFechaHora(pedido.venceEn)}`}>
      {plazo.texto}
    </span>
  )
}

const ACTIVOS = ['PENDIENTE', 'CONFIRMADA', 'LISTA']

/** H78: todos los datos del pedido y las acciones que permite su estado. */
export function DetallePedidoModal({ pedido, ahora, onCerrar, onActualizado, onAvanzar, onEntregar, onRechazar }) {
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  const accion = siguienteAccion(pedido)
  const activo = ACTIVOS.includes(pedido.estado)
  const sinStock = sinStockParaAceptar(pedido)

  // Trae la versión más reciente del pedido (puede haber cambiado desde que se cargó la lista)
  useEffect(() => {
    let vigente = true
    api
      .obtenerPedido(pedido.id)
      .then((actual) => vigente && onActualizado(actual))
      .catch((err) => vigente && setError(err.mensaje ?? 'No se pudo cargar el pedido.'))
    return () => {
      vigente = false
    }
  }, [pedido.id, onActualizado])

  async function avanzar() {
    if (accion.confirmar) {
      onEntregar(pedido)
      return
    }
    setGuardando(true)
    setError('')
    try {
      await onAvanzar(pedido)
    } catch (err) {
      setError(err.mensaje ?? 'No se pudo cambiar el estado del pedido.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={`Pedido ${pedido.codigo}`} onCerrar={onCerrar}>
      <div className="formulario">
        <p>
          <EstadoPedidoBadge estado={pedido.estado} />
        </p>
        <dl className="detalle-pedido">
          <div>
            <dt>Producto</dt>
            <dd>
              <strong>{pedido.nombreComercial}</strong>
              <span className="texto-suave">{pedido.presentacion}</span>
            </dd>
          </div>
          <div>
            <dt>Cantidad</dt>
            <dd>{pedido.cantidad}</dd>
          </div>
          <div>
            <dt>Precio unitario</dt>
            <dd>{formatoSoles(pedido.precioUnitario)}</dd>
          </div>
          <div>
            <dt>Total</dt>
            <dd>
              <strong>{formatoSoles(pedido.total)}</strong>
            </dd>
          </div>
          <div>
            <dt>Stock actual</dt>
            <dd>{pedido.stockActual}</dd>
          </div>
          {pedido.estado === 'PENDIENTE' && typeof pedido.disponible === 'number' && (
            <div>
              <dt>Disponible</dt>
              <dd>
                <span className={sinStock ? 'disponible-insuficiente' : undefined}>{pedido.disponible}</span>
              </dd>
            </div>
          )}
          <div>
            <dt>Comprador</dt>
            <dd>{pedido.compradorNombre}</dd>
          </div>
          <div>
            <dt>Reservado el</dt>
            <dd>{formatoFechaHora(pedido.creadoEn)}</dd>
          </div>
          <div>
            <dt>Último cambio</dt>
            <dd>{formatoFechaHora(pedido.actualizadoEn)}</dd>
          </div>
          <div>
            <dt>{pedido.estado === 'LISTA' ? 'Recoger hasta' : 'Vence'}</dt>
            <dd>
              {formatoFechaHora(pedido.venceEn)}
              {activo && (
                <span>
                  <PlazoPedido pedido={pedido} ahora={ahora} />
                </span>
              )}
            </dd>
          </div>
          {pedido.motivoRechazo && (
            <div className="detalle-ancho">
              <dt>Motivo del rechazo</dt>
              <dd>{pedido.motivoRechazo}</dd>
            </div>
          )}
        </dl>

        {sinStock ? (
          <Alerta tipo="aviso">
            <p id="detalle-sin-stock">
              <strong>{TEXTO_SIN_STOCK}.</strong> Hay {pedido.disponible}{' '}
              {pedido.disponible === 1 ? 'unidad disponible' : 'unidades disponibles'} y el pedido es de{' '}
              {pedido.cantidad}. Recházalo con un motivo para avisar al comprador.
            </p>
          </Alerta>
        ) : (
          pedido.estado === 'PENDIENTE' && (
            <p className="texto-suave">
              Al confirmarlo, sus unidades quedan comprometidas: no podrás bajar el stock del producto por debajo de
              ellas hasta que lo entregues.
            </p>
          )
        )}
        {pedido.estado === 'CONFIRMADA' && (
          <p className="texto-suave">
            Al marcarlo listo, el comprador recibe un correo y tiene 48 horas para recogerlo.
          </p>
        )}

        <Alerta tipo="error">{error && <p>{error}</p>}</Alerta>

        <div className="acciones-form">
          <button type="button" className="btn btn-secundario" onClick={onCerrar}>
            Cerrar
          </button>
          {sePuedeRechazar(pedido) && (
            <button
              type="button"
              className="btn btn-secundario btn-icono boton-eliminar"
              onClick={() => onRechazar(pedido)}
              disabled={guardando}
            >
              <Icono nombre="equis" /> Rechazar
            </button>
          )}
          {accion && (
            <button
              type="button"
              className="btn btn-primario btn-icono"
              onClick={avanzar}
              disabled={guardando || sinStock}
              aria-describedby={sinStock ? 'detalle-sin-stock' : undefined}
            >
              <Icono nombre={accion.icono} /> {guardando ? 'Guardando...' : accion.texto}
            </button>
          )}
        </div>
      </div>
    </Modal>
  )
}

/** H79: "Entregado" descuenta el stock, por eso se confirma antes. */
export function ConfirmarEntregaModal({ pedido, onCerrar, onConfirmar }) {
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  async function confirmar() {
    setGuardando(true)
    setError('')
    try {
      await onConfirmar()
    } catch (err) {
      setError(err.mensaje ?? 'No se pudo marcar el pedido como entregado.')
      setGuardando(false)
    }
  }

  const quedara = pedido.stockActual - pedido.cantidad

  return (
    <Modal titulo="¿Marcar como entregado?" onCerrar={onCerrar} pequeno>
      <div className="formulario">
        <p>
          Confirma que el comprador <strong>{pedido.compradorNombre}</strong> recogió el pedido{' '}
          <strong>{pedido.codigo}</strong>: {pedido.cantidad} × {pedido.nombreComercial} ({pedido.presentacion}).
        </p>
        <p>
          Se descontarán <strong>{pedido.cantidad}</strong> {pedido.cantidad === 1 ? 'unidad' : 'unidades'} del stock
          {quedara >= 0 ? ` (quedarán ${quedara}).` : '.'} Este cambio no se puede deshacer.
        </p>
        <Alerta tipo="error">{error && <p>{error}</p>}</Alerta>
        <div className="acciones-form">
          <button type="button" className="btn btn-secundario" onClick={onCerrar} disabled={guardando}>
            Cancelar
          </button>
          <button type="button" className="btn btn-primario" onClick={confirmar} disabled={guardando}>
            {guardando ? 'Guardando...' : 'Sí, fue entregado'}
          </button>
        </div>
      </div>
    </Modal>
  )
}

/** H80: el motivo le llega al comprador por correo. */
export function RechazarPedidoModal({ pedido, onCerrar, onRechazar }) {
  // Regla nueva: si no alcanza el stock, se sugiere el motivo (se puede editar)
  const [motivo, setMotivo] = useState(() => (sinStockParaAceptar(pedido) ? MOTIVO_SIN_STOCK : ''))
  const [tocado, setTocado] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  const largo = motivo.trim().length
  const errorMotivo =
    largo < MOTIVO_MIN
      ? `Escribe al menos ${MOTIVO_MIN} caracteres.`
      : largo > MOTIVO_MAX
        ? `Usa como máximo ${MOTIVO_MAX} caracteres.`
        : ''
  const mostrarError = tocado && errorMotivo

  async function enviar(e) {
    e.preventDefault()
    setTocado(true)
    if (errorMotivo) return
    setGuardando(true)
    setError('')
    try {
      await onRechazar(motivo)
    } catch (err) {
      setError(err.mensaje ?? 'No se pudo rechazar el pedido.')
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={`Rechazar pedido ${pedido.codigo}`} onCerrar={onCerrar} pequeno>
      <form className="formulario" onSubmit={enviar} noValidate>
        <p>
          {pedido.cantidad} × {pedido.nombreComercial} ({pedido.presentacion}) para {pedido.compradorNombre}. El
          comprador recibirá un correo con el motivo y las unidades vuelven a estar disponibles.
        </p>
        <div className="campo">
          <label htmlFor="motivo-rechazo">Motivo del rechazo</label>
          <textarea
            id="motivo-rechazo"
            rows={4}
            maxLength={MOTIVO_MAX + 50}
            value={motivo}
            onChange={(e) => setMotivo(e.target.value)}
            onBlur={() => setTocado(true)}
            placeholder="Ej.: No nos queda stock de esa presentación"
            aria-invalid={mostrarError ? 'true' : 'false'}
            aria-describedby="motivo-rechazo-contador motivo-rechazo-mensaje"
            required
            autoFocus
          />
          <div className="pie-campo">
            <span id="motivo-rechazo-mensaje" className={mostrarError ? 'error-campo' : 'ayuda'}>
              {mostrarError || `Entre ${MOTIVO_MIN} y ${MOTIVO_MAX} caracteres.`}
            </span>
            <span
              id="motivo-rechazo-contador"
              className={`contador ${largo > MOTIVO_MAX ? 'contador-excedido' : ''}`}
            >
              {largo}/{MOTIVO_MAX}
            </span>
          </div>
        </div>
        <Alerta tipo="error">{error && <p>{error}</p>}</Alerta>
        <div className="acciones-form">
          <button type="button" className="btn btn-secundario" onClick={onCerrar} disabled={guardando}>
            Cancelar
          </button>
          <button type="submit" className="btn btn-peligro" disabled={guardando}>
            {guardando ? 'Rechazando...' : 'Rechazar pedido'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
