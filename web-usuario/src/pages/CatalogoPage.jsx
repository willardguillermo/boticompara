// Catálogo de la botica del dueño
// H13 - Ver el catálogo de mi botica (GET /boticas/mia/productos)
// H14 - Buscar en mi catálogo por nombre comercial o principio activo (?q=)
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import Cargando from '../components/Cargando.jsx'
import EstadoBadge from '../components/EstadoBadge.jsx'
import Icono from '../components/Icono.jsx'
import { useBotica } from '../context/BoticaContext.jsx'
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

function TablaProductos({ productos }) {
  return (
    <table className="tabla">
      <thead>
        <tr>
          <th scope="col">Nombre comercial</th>
          <th scope="col">Principio activo</th>
          <th scope="col">Presentación</th>
          <th scope="col" className="numero">Precio</th>
          <th scope="col" className="numero">Stock</th>
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
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export default function CatalogoPage() {
  const { botica } = useBotica()
  const aprobada = botica.estado === 'APROBADO'

  const [busqueda, setBusqueda] = useState('')
  const [productos, setProductos] = useState(null)
  const [error, setError] = useState('')

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
  }, [aprobada, busqueda])

  if (!aprobada) return <CatalogoBloqueado estado={botica.estado} />

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
          <TablaProductos productos={productos} />
        )}
      </section>
    </>
  )
}
