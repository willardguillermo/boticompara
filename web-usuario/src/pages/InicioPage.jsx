// H103 - Inicio del dueño: saludo, 4 números del día y actividad reciente.
// Los números salen de endpoints que ya existen y se piden una vez al abrir (docs/API.md, sección 12).
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import AvisoCambioEstado from '../components/AvisoCambioEstado.jsx'
import Esqueleto from '../components/Esqueleto.jsx'
import Icono from '../components/Icono.jsx'
import ListaNotificaciones from '../components/ListaNotificaciones.jsx'
import Counter from '../components/reactbits/Counter.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { useBotica } from '../context/BoticaContext.jsx'
import { useNotificaciones } from '../context/NotificacionesContext.jsx'
import { useAhora } from '../hooks/useAhora.js'
import { api } from '../services/index.js'
import { MAX_PEDIDOS_LISTA, porRecogerHoy, primerNombre, saludo } from '../utils/inicio.js'

const MAX_ACTIVIDAD = 5

/** Cada tarjeta se carga por separado: si una falla, las demás se ven igual. */
const TARJETAS = [
  {
    clave: 'pendientes',
    titulo: 'Pedidos pendientes',
    icono: 'pedido',
    a: '/panel/pedidos?estado=PENDIENTE',
    cargar: async () => {
      const { length } = await api.listarPedidos('PENDIENTE')
      return {
        valor: length,
        sufijo: length >= MAX_PEDIDOS_LISTA ? '+' : '',
        detalle: length ? 'Por confirmar o rechazar' : 'Todo al día',
      }
    },
  },
  {
    clave: 'recoger',
    titulo: 'Por recoger hoy',
    icono: 'reloj',
    a: '/panel/pedidos?estado=LISTA',
    cargar: async () => {
      const valor = porRecogerHoy(await api.listarPedidos('LISTA'))
      return { valor, detalle: valor ? 'Su plazo termina hoy' : 'Ningún plazo termina hoy' }
    },
  },
  {
    clave: 'stock',
    titulo: 'Productos con stock bajo',
    icono: 'caja',
    a: '/panel/catalogo?stockBajo=true',
    cargar: async () => {
      const { length } = await api.listarMisProductos('', { stockBajo: true })
      return { valor: length, detalle: length ? 'Por reponer' : 'Ninguno por reponer' }
    },
  },
  {
    clave: 'calificacion',
    titulo: 'Calificación promedio',
    icono: 'estrella',
    a: '/panel/estado',
    cargar: async (boticaId) => {
      const { promedio, total } = await api.listarResenas(boticaId)
      return {
        valor: promedio,
        decimales: 1,
        detalle: total ? `De 5, en ${total === 1 ? '1 reseña' : `${total} reseñas`}` : 'Sin reseñas todavía',
      }
    },
  },
]

// El número rueda al aparecer (Counter de React Bits). El lector de pantalla lee el número completo
// y con movimiento reducido se muestra quieto.
function Numero({ valor, decimales, sufijo }) {
  if (valor === null) return <span aria-label="Sin calificación">—</span>
  return (
    <Counter value={valor} decimals={decimales} suffix={sufijo} locale="es-PE" fontSize="inherit" duration={0.9} stagger={0.06} />
  )
}

function TarjetaResumen({ tarjeta, boticaId }) {
  const { titulo, icono, a, cargar } = tarjeta
  const [dato, setDato] = useState(null) // null: cargando; { error: true }: falló
  const [intento, setIntento] = useState(0)

  useEffect(() => {
    let activo = true
    cargar(boticaId)
      .then((d) => activo && setDato(d))
      .catch(() => activo && setDato({ error: true }))
    return () => {
      activo = false
    }
  }, [cargar, boticaId, intento])

  const contenido = (
    <>
      <span className="resumen-inicio-titulo">
        {titulo}
        <span className="resumen-inicio-icono">
          <Icono nombre={icono} />
        </span>
      </span>
      {dato === null ? (
        <>
          <span className="sr-only">Cargando</span>
          <span className="resumen-inicio-numero">
            <Esqueleto ancho="3.5rem" alto="1em" />
          </span>
          <Esqueleto ancho="70%" alto="0.9rem" />
        </>
      ) : dato.error ? (
        <span className="resumen-inicio-detalle">No se pudo cargar.</span>
      ) : (
        <>
          <span className="resumen-inicio-numero">
            <Numero {...dato} />
          </span>
          <span className="resumen-inicio-detalle">{dato.detalle}</span>
        </>
      )}
    </>
  )

  // Con error la tarjeta deja de ser enlace para poder llevar el botón de reintentar
  if (dato?.error) {
    return (
      <div className="resumen-inicio">
        {contenido}
        <button
          type="button"
          className="boton-texto"
          aria-label={`Reintentar: ${titulo}`}
          onClick={() => {
            setDato(null)
            setIntento((n) => n + 1)
          }}
        >
          Reintentar
        </button>
      </div>
    )
  }
  return (
    <Link to={a} className="resumen-inicio" aria-busy={dato === null}>
      {contenido}
    </Link>
  )
}

export default function InicioPage() {
  const { usuario } = useAuth()
  const { botica } = useBotica()
  const { cargarLista } = useNotificaciones()
  const ahora = useAhora()

  useEffect(() => {
    cargarLista()
  }, [cargarLista])

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>
            {saludo(new Date(ahora))}, {primerNombre(usuario.nombre)}
          </h1>
          <p className="texto-suave">Así va {botica.nombreComercial} hoy.</p>
        </div>
      </div>

      <AvisoCambioEstado />

      <section aria-labelledby="titulo-resumen">
        <h2 id="titulo-resumen" className="sr-only">
          Resumen de tu botica
        </h2>
        <ul className="resumen-inicio-lista">
          {TARJETAS.map((t) => (
            <li key={t.clave}>
              <TarjetaResumen tarjeta={t} boticaId={botica.id} />
            </li>
          ))}
        </ul>
      </section>

      <section className="actividad" aria-labelledby="titulo-actividad">
        <h2 id="titulo-actividad">Actividad reciente</h2>
        <div className="tarjeta actividad-tarjeta">
          <ListaNotificaciones maximo={MAX_ACTIVIDAD} />
        </div>
      </section>
    </>
  )
}
