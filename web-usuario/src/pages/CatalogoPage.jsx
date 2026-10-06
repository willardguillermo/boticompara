// Catálogo de la botica del dueño
// H13 - Ver el catálogo de mi botica (GET /boticas/mia/productos)
// H14 - Buscar en mi catálogo por nombre comercial o principio activo (?q=)
// H8 / H9 - Agregar y editar productos (ver ProductoFormModal)
// H10 - Eliminar producto con confirmación (ver ConfirmarEliminarModal)
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import Cargando from '../components/Cargando.jsx'
import ConfirmarEliminarModal from '../components/ConfirmarEliminarModal.jsx'
import EstadoBadge from '../components/EstadoBadge.jsx'
import Icono from '../components/Icono.jsx'
import ProductoFormModal from '../components/ProductoFormModal.jsx'
import { useBotica } from '../context/BoticaContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { api } from '../services/index.js'
import { formatoSoles } from '../utils/formato.js'

/** Catálogo bloqueado mientras la botica no esté APROBADO. */
function CatalogoBloqueado({ estado }) {
  return (
    <section className="tarjeta bloqueado">
      <span className="icono-grande">
        <Icono nombre="candado" />
      </span>
      <h2>Catálogo bloqueado</h2>
      <p>
        <EstadoBadge estado={estado} />
      </p>
      <p className="texto-suave">
        {estado === 'RECHAZADO'
          ? 'Tu solicitud fue rechazada. Revisa el motivo en Mi botica antes de publicar productos.'
          : 'Podrás agregar y editar productos cuando un administrador apruebe tu botica.'}
      </p>
      <Link to="/panel/estado" className="btn btn-secundario">
        Ver estado de mi botica
      </Link>
    </section>
  )
}

function TablaProductos({ productos, onEditar, onEliminar }) {
  return (
    <table className="tabla">
      <thead>
        <tr>
          <th scope="col">Nombre comercial</th>
          <th scope="col">Principio activo</th>
          <th scope="col">Presentación</th>
          <th scope="col" className="numero">Precio</th>
          <th scope="col" className="numero">Stock</th>
          <th scope="col" className="acciones">
            <span className="sr-only">Acciones</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {productos.map((p) => (
          <tr key={p.id}>
            <td data-etiqueta="Nombre" className="celda-nombre">
              <strong>{p.nombreComercial}</strong>
            </td>
            <td data-etiqueta="Principio activo">{p.principioActivo}</td>
            <td data-etiqueta="Presentación">{p.presentacion}</td>
            <td data-etiqueta="Precio" className="numero">{formatoSoles(p.precio)}</td>
            <td data-etiqueta="Stock" className="numero">
              {p.stock === 0 ? <span className="sin-stock">Agotado</span> : p.stock}
            </td>
            <td className="acciones">
              <button
                type="button"
                className="btn btn-secundario btn-icono"
                onClick={() => onEditar(p)}
                aria-label={`Editar ${p.nombreComercial}`}
              >
                <Icono nombre="editar" /> Editar
              </button>
              <button
                type="button"
                className="btn btn-secundario btn-icono boton-eliminar"
                onClick={() => onEliminar(p)}
                aria-label={`Eliminar ${p.nombreComercial}`}
              >
                <Icono nombre="borrar" /> Eliminar
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export default function CatalogoPage() {
  const { botica } = useBotica()
  const toast = useToast()
  const aprobada = botica.estado === 'APROBADO'

  const [busqueda, setBusqueda] = useState('')
  const [productos, setProductos] = useState(null)
  const [error, setError] = useState('')
  const [recarga, setRecarga] = useState(0)
  const [editando, setEditando] = useState(null) // null | {} (nuevo) | producto
  const [eliminando, setEliminando] = useState(null)

  // Busca en el servidor con una pequeña espera mientras se escribe
  useEffect(() => {
    if (!aprobada) return undefined
    let activo = true
    const espera = setTimeout(() => {
      api
        .listarMisProductos(busqueda)
        .then((lista) => {
          if (!activo) return
          setProductos(lista)
          setError('')
        })
        .catch((err) => activo && setError(err.mensaje ?? 'No se pudo cargar el catálogo.'))
    }, 300)
    return () => {
      activo = false
      clearTimeout(espera)
    }
  }, [aprobada, busqueda, recarga])

  if (!aprobada) return <CatalogoBloqueado estado={botica.estado} />

  function alGuardar(producto, eraEdicion) {
    setEditando(null)
    setRecarga((n) => n + 1)
    toast(eraEdicion ? `Se actualizó "${producto.nombreComercial}".` : `Se agregó "${producto.nombreComercial}" al catálogo.`)
  }

  function alEliminar(producto) {
    setEliminando(null)
    setRecarga((n) => n + 1)
    toast(`Se eliminó "${producto.nombreComercial}" del catálogo.`)
  }

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Mi catálogo</h1>
          <p className="texto-suave">
            Productos de {botica.nombreComercial}. Los que tienen stock aparecen en la búsqueda de los compradores.
          </p>
        </div>
      </div>

      <div className="barra-catalogo">
        <div className="campo buscador">
          <Icono nombre="buscar" />
          <input
            type="search"
            aria-label="Buscar por nombre comercial o principio activo"
            placeholder="Buscar por nombre comercial o principio activo"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
          />
        </div>
        <button type="button" className="btn btn-primario" onClick={() => setEditando({})}>
          <Icono nombre="mas" /> Agregar producto
        </button>
      </div>

      <Alerta tipo="error">{error && <p>{error}</p>}</Alerta>

      <section className="tarjeta tabla-contenedor">
        {productos === null ? (
          <Cargando texto="Cargando catálogo..." />
        ) : productos.length === 0 ? (
          <p className="vacio">
            {busqueda.trim()
              ? `No hay productos que coincidan con "${busqueda.trim()}".`
              : 'Todavía no tienes productos en tu catálogo.'}
          </p>
        ) : (
          <TablaProductos productos={productos} onEditar={setEditando} onEliminar={setEliminando} />
        )}
      </section>

      {editando && (
        <ProductoFormModal producto={editando} onCerrar={() => setEditando(null)} onGuardado={alGuardar} />
      )}
      {eliminando && (
        <ConfirmarEliminarModal
          producto={eliminando}
          onCerrar={() => setEliminando(null)}
          onEliminado={alEliminar}
        />
      )}
    </>
  )
}
