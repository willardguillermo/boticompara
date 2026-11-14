// Pedidos de la botica (sección 10 de docs/API.md)
// H77 - Ver los pedidos de mi botica (GET /pedidos)
// H81 - Filtrar por estado (?estado=), guardado en la URL
// H78 - Detalle de un pedido (ver PedidoModales)
// H79 - Confirmar, marcar listo para recoger y entregar (PATCH /pedidos/{id}/estado)
// H80 - Rechazar con motivo (PATCH /pedidos/{id}/rechazar)
import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import Cargando from '../components/Cargando.jsx'
import EstadoPedidoBadge from '../components/EstadoPedidoBadge.jsx'
import Icono from '../components/Icono.jsx'
import {
  ConfirmarEntregaModal,
  DetallePedidoModal,
  PlazoPedido,
  RechazarPedidoModal,
} from '../components/PedidoModales.jsx'
import { useBotica } from '../context/BoticaContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { api } from '../services/index.js'
import { formatoFechaHora, formatoSoles } from '../utils/formato.js'
import {
  ESTADOS_PEDIDO,
  TEXTO_SIN_STOCK,
  esEstadoPedido,
  sePuedeRechazar,
  siguienteAccion,
  sinStockParaAceptar,
} from '../utils/pedidos.js'

/** Hora actual que se renueva cada minuto, para el "Vence en ..." */
function useAhora() {
  const [ahora, setAhora] = useState(() => Date.now())
  useEffect(() => {
    const intervalo = setInterval(() => setAhora(Date.now()), 60 * 1000)
    return () => clearInterval(intervalo)
  }, [])
  return ahora
}

function TablaPedidos({ pedidos, ahora, procesando, onVer, onAvanzar, onRechazar }) {
  return (
    <table className="tabla tabla-pedidos">
      <thead>
        <tr>
          <th scope="col">Código</th>
          <th scope="col">Fecha</th>
          <th scope="col">Comprador</th>
          <th scope="col">Producto</th>
          <th scope="col" className="numero">Cant.</th>
          <th scope="col" className="numero">Total</th>
          <th scope="col">Estado</th>
          <th scope="col">Plazo</th>
          <th scope="col" className="acciones">
            <span className="sr-only">Acciones</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {pedidos.map((p) => {
          const accion = siguienteAccion(p)
          const ocupado = procesando === p.id
          const sinStock = sinStockParaAceptar(p)
          const idAviso = `sin-stock-${p.id}`
          return (
            <tr key={p.id}>
              <td data-etiqueta="Código" className="celda-nombre">
                <strong className="codigo-pedido">{p.codigo}</strong>
              </td>
              <td data-etiqueta="Fecha">{formatoFechaHora(p.creadoEn)}</td>
              <td data-etiqueta="Comprador">{p.compradorNombre}</td>
              <td data-etiqueta="Producto">
                <span className="producto-pedido">
                  {p.nombreComercial}
                  <span className="texto-suave">{p.presentacion}</span>
                </span>
              </td>
              <td data-etiqueta="Cantidad" className="numero">
                <span className="cantidad-pedido">
                  {p.cantidad}
                  {p.estado === 'PENDIENTE' && typeof p.disponible === 'number' && (
                    <span className={sinStock ? 'disponible-insuficiente' : 'texto-suave'}>
                      Disponible: {p.disponible}
                    </span>
                  )}
                </span>
              </td>
              <td data-etiqueta="Total" className="numero">{formatoSoles(p.total)}</td>
              <td data-etiqueta="Estado">
                <EstadoPedidoBadge estado={p.estado} />
              </td>
              <td data-etiqueta="Plazo">
                <PlazoPedido pedido={p} ahora={ahora} />
              </td>
              <td className="acciones">
                {sinStock && (
                  <span id={idAviso} className="aviso-sin-stock">
                    {TEXTO_SIN_STOCK}
                  </span>
                )}
                <button
                  type="button"
                  className="btn btn-secundario btn-icono"
                  onClick={() => onVer(p)}
                  aria-label={`Ver detalle del pedido ${p.codigo}`}
                >
                  <Icono nombre="buscar" /> Detalle
                </button>
                {sinStock && sePuedeRechazar(p) && (
                  <button
                    type="button"
                    className="btn btn-secundario btn-icono boton-eliminar"
                    onClick={() => onRechazar(p)}
                    aria-label={`Rechazar el pedido ${p.codigo}`}
                  >
                    <Icono nombre="equis" /> Rechazar
                  </button>
                )}
                {accion && (
                  <button
                    type="button"
                    className="btn btn-primario btn-icono"
                    onClick={() => onAvanzar(p)}
                    disabled={ocupado || sinStock}
                    aria-label={`${accion.texto}: pedido ${p.codigo}`}
                    aria-describedby={sinStock ? idAviso : undefined}
                  >
                    <Icono nombre={accion.icono} /> {ocupado ? 'Guardando...' : accion.texto}
                  </button>
                )}
              </td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}

export default function PedidosPage() {
  const { botica } = useBotica()
  const toast = useToast()
  const ahora = useAhora()

  // H81: el filtro vive en la URL (?estado=PENDIENTE) para no perderlo al recargar
  const [parametros, setParametros] = useSearchParams()
  const estadoUrl = parametros.get('estado')
  const filtro = esEstadoPedido(estadoUrl) ? estadoUrl : ''

  const [pedidos, setPedidos] = useState(null)
  const [error, setError] = useState('')
  const [recarga, setRecarga] = useState(0)
  // Consulta que terminó por última vez; si no coincide con la actual, se está cargando
  const consulta = `${filtro}|${recarga}`
  const [consultaLista, setConsultaLista] = useState(null)
  const cargando = consultaLista !== consulta
  const [procesando, setProcesando] = useState(null) // id del pedido que se está guardando desde la tabla
  // Un solo diálogo a la vez por tipo: detalle y, encima, entrega o rechazo
  const [detalleId, setDetalleId] = useState(null)
  const [entregando, setEntregando] = useState(null)
  const [rechazando, setRechazando] = useState(null)

  // Un estado inválido en la URL se quita en lugar de mostrar un error
  useEffect(() => {
    if (estadoUrl !== null && !esEstadoPedido(estadoUrl)) setParametros({}, { replace: true })
  }, [estadoUrl, setParametros])

  useEffect(() => {
    let activo = true
    api
      .listarPedidos(filtro)
      .then((lista) => {
        if (!activo) return
        setPedidos(lista)
        setError('')
      })
      .catch((err) => activo && setError(err.mensaje ?? 'No se pudieron cargar los pedidos.'))
      .finally(() => activo && setConsultaLista(`${filtro}|${recarga}`))
    return () => {
      activo = false
    }
  }, [filtro, recarga])

  /** Reemplaza solo la fila del pedido, sin volver a pedir la lista. */
  const reemplazar = useCallback((pedido) => {
    setPedidos((lista) => lista?.map((p) => (p.id === pedido.id ? pedido : p)) ?? lista)
  }, [])

  /** Tras un 409 (cambio ya no válido, falta de stock, vencido) se trae el estado real de la fila. */
  const refrescarFila = useCallback(
    (id) => api.obtenerPedido(id).then(reemplazar).catch(() => {}),
    [reemplazar],
  )

  /** H79: lanza el error para que quien llama muestre el mensaje del cuerpo. */
  const avanzar = useCallback(
    async (pedido) => {
      const accion = siguienteAccion(pedido)
      try {
        const actualizado = await api.cambiarEstadoPedido(pedido.id, accion.estado)
        reemplazar(actualizado)
        toast(`Pedido ${actualizado.codigo} ${accion.exito}.`)
        return actualizado
      } catch (err) {
        if (err.status === 409 || err.status === 404) refrescarFila(pedido.id)
        throw err
      }
    },
    [reemplazar, refrescarFila, toast],
  )

  /** Desde la tabla: "Entregado" pide confirmación porque descuenta stock. */
  async function avanzarDesdeTabla(pedido) {
    if (siguienteAccion(pedido).confirmar) {
      setEntregando(pedido)
      return
    }
    setProcesando(pedido.id)
    try {
      await avanzar(pedido)
    } catch (err) {
      toast(err.mensaje ?? 'No se pudo cambiar el estado del pedido.', 'error')
    } finally {
      setProcesando(null)
    }
  }

  async function rechazar(pedido, motivo) {
    try {
      const actualizado = await api.rechazarPedido(pedido.id, motivo)
      reemplazar(actualizado)
      setRechazando(null)
      toast(`Pedido ${actualizado.codigo} rechazado. Se avisará al comprador por correo.`)
    } catch (err) {
      if (err.status === 409 || err.status === 404) refrescarFila(pedido.id)
      throw err
    }
  }

  function cambiarFiltro(estado) {
    setParametros(estado ? { estado } : {})
  }

  const detalle = detalleId === null ? null : pedidos?.find((p) => p.id === detalleId) ?? null

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Pedidos</h1>
          <p className="texto-suave">
            Reservas que los compradores hicieron en {botica.nombreComercial} desde la app. Confírmalas, prepáralas y
            márcalas como entregadas cuando las recojan.
          </p>
        </div>
      </div>

      <div className="barra-catalogo barra-pedidos">
        <div className="campo filtro-estado">
          <label htmlFor="filtro-estado">Estado</label>
          <select id="filtro-estado" value={filtro} onChange={(e) => cambiarFiltro(e.target.value)}>
            <option value="">Todos</option>
            {Object.entries(ESTADOS_PEDIDO).map(([estado, info]) => (
              <option key={estado} value={estado}>
                {info.plural}
              </option>
            ))}
          </select>
        </div>
        <button
          type="button"
          className="btn btn-secundario btn-icono"
          onClick={() => setRecarga((n) => n + 1)}
          disabled={cargando}
        >
          <Icono nombre="actualizar" /> {cargando && pedidos !== null ? 'Actualizando...' : 'Actualizar'}
        </button>
      </div>

      <Alerta tipo="error">{error && <p>{error}</p>}</Alerta>

      <section className="tarjeta tabla-contenedor" aria-busy={cargando}>
        {pedidos === null ? (
          error ? null : <Cargando texto="Cargando pedidos..." />
        ) : pedidos.length === 0 ? (
          <div className="vacio">
            {filtro ? (
              <>
                <p>No hay pedidos en este estado ({ESTADOS_PEDIDO[filtro].plural.toLowerCase()}).</p>
                <button type="button" className="boton-texto" onClick={() => cambiarFiltro('')}>
                  Ver todos los pedidos
                </button>
              </>
            ) : (
              <>
                <p>Aún no tienes pedidos.</p>
                <p>
                  {botica.estado === 'APROBADO'
                    ? 'Cuando un comprador reserve uno de tus productos desde la app, aparecerá aquí y te llegará un correo.'
                    : 'Recibirás pedidos cuando un administrador apruebe tu botica y tus productos aparezcan en la búsqueda.'}
                </p>
              </>
            )}
          </div>
        ) : (
          <TablaPedidos
            pedidos={pedidos}
            ahora={ahora}
            procesando={procesando}
            onVer={(p) => setDetalleId(p.id)}
            onAvanzar={avanzarDesdeTabla}
            onRechazar={setRechazando}
          />
        )}
      </section>

      {detalle && (
        <DetallePedidoModal
          pedido={detalle}
          ahora={ahora}
          onCerrar={() => setDetalleId(null)}
          onActualizado={reemplazar}
          onAvanzar={avanzar}
          onEntregar={setEntregando}
          onRechazar={setRechazando}
        />
      )}
      {entregando && (
        <ConfirmarEntregaModal
          pedido={entregando}
          onCerrar={() => setEntregando(null)}
          onConfirmar={async () => {
            await avanzar(entregando)
            setEntregando(null)
          }}
        />
      )}
      {rechazando && (
        <RechazarPedidoModal
          pedido={rechazando}
          onCerrar={() => setRechazando(null)}
          onRechazar={(motivo) => rechazar(rechazando, motivo)}
        />
      )}
    </>
  )
}
