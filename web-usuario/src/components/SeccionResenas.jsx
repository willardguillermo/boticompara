// H87 - "Lo que dicen tus clientes": reseñas de la botica del dueño, solo lectura (responder es H88).
// Usa GET /boticas/{id}/resenas?page=N tal como está en docs/API.md (páginas de 10, la más reciente primero).
import { useEffect, useState } from 'react'
import { api } from '../services/index.js'
import {
  etiquetaEstrellas, formatoFechaResena, hayMasPaginas, rellenoEstrellas, textoCantidad, textoPromedio, unirPaginas,
} from '../utils/resenas.js'
import Esqueleto from './Esqueleto.jsx'
import Icono from './Icono.jsx'

/** 5 estrellas: una vacía de fondo y encima la parte pintada (la fracción exacta del valor). */
function Estrellas({ valor, grande = false }) {
  return (
    <span className={`estrellas${grande ? ' estrellas-grandes' : ''}`} role="img" aria-label={etiquetaEstrellas(valor)}>
      {rellenoEstrellas(valor).map((relleno, i) => (
        <span key={i} className="estrella">
          <Icono nombre="estrella" />
          <span className="estrella-relleno" style={{ width: `${relleno}%` }}>
            <Icono nombre="estrella" />
          </span>
        </span>
      ))}
    </span>
  )
}

function EsqueletoResenas() {
  return (
    <div className="resenas-cargando" aria-hidden="true">
      <Esqueleto ancho="9rem" alto="2.2rem" />
      {[0, 1].map((i) => (
        <div key={i} className="resena">
          <Esqueleto ancho="40%" />
          <Esqueleto ancho="85%" />
        </div>
      ))}
    </div>
  )
}

export default function SeccionResenas({ boticaId }) {
  // datos: null mientras carga la primera página; luego promedio, total, page y totalPages de la última pedida
  const [datos, setDatos] = useState(null)
  const [resenas, setResenas] = useState([])
  const [error, setError] = useState(false)
  const [cargandoMas, setCargandoMas] = useState(false)
  const [errorMas, setErrorMas] = useState(false)
  // Sube con "Reintentar": vuelve a pedir la primera página
  const [intento, setIntento] = useState(0)

  useEffect(() => {
    // Si cambia la botica o se desmonta antes de responder, la respuesta vieja se ignora
    let vigente = true
    api.listarResenas(boticaId, 0).then(
      (r) => {
        if (!vigente) return
        setResenas(r.resenas)
        setDatos(r)
      },
      () => {
        if (vigente) setError(true)
      },
    )
    return () => {
      vigente = false
    }
  }, [boticaId, intento])

  function reintentar() {
    setError(false)
    setDatos(null)
    setIntento((n) => n + 1)
  }

  async function verMas() {
    setCargandoMas(true)
    setErrorMas(false)
    try {
      const r = await api.listarResenas(boticaId, datos.page + 1)
      setResenas((actuales) => unirPaginas(actuales, r.resenas))
      setDatos(r)
    } catch {
      setErrorMas(true)
    } finally {
      setCargandoMas(false)
    }
  }

  let contenido
  if (error) {
    contenido = (
      <div className="vacio" role="alert">
        <p>No se pudieron cargar las reseñas.</p>
        <button type="button" className="btn btn-secundario" onClick={reintentar}>
          <Icono nombre="actualizar" /> Reintentar
        </button>
      </div>
    )
  } else if (datos === null) {
    contenido = <EsqueletoResenas />
  } else if (datos.total === 0) {
    contenido = (
      <div className="vacio resenas-vacio">
        <Icono nombre="estrella" />
        <p>Aún no tienes reseñas</p>
        <p className="texto-suave">Cuando un comprador recoja su pedido y califique tu botica, la verás aquí.</p>
      </div>
    )
  } else {
    contenido = (
      <>
        <div className="resenas-resumen">
          <span className="resenas-promedio">{textoPromedio(datos.promedio)}</span>
          <div>
            <Estrellas valor={datos.promedio} grande />
            <p className="texto-suave">{textoCantidad(datos.total)}</p>
          </div>
        </div>

        <ul className="resenas-lista">
          {resenas.map((r) => (
            <li key={r.id} className="resena">
              <div className="resena-cabecera">
                <strong>{r.autor}</strong>
                <Estrellas valor={r.estrellas} />
                <time className="texto-suave" dateTime={r.creadoEn}>{formatoFechaResena(r.creadoEn)}</time>
              </div>
              {r.comentario ? <p>{r.comentario}</p> : <p className="texto-suave resena-sin-comentario">Sin comentario.</p>}
            </li>
          ))}
        </ul>

        {hayMasPaginas(datos.page, datos.totalPages) && (
          <div className="resenas-mas">
            {errorMas && <p className="resenas-error" role="alert">No se pudieron cargar más reseñas. Inténtalo de nuevo.</p>}
            <button type="button" className="btn btn-secundario" onClick={verMas} disabled={cargandoMas}>
              {cargandoMas ? 'Cargando...' : 'Ver más'}
            </button>
          </div>
        )}
      </>
    )
  }

  return (
    <section className="tarjeta seccion-resenas" aria-labelledby="titulo-resenas" aria-busy={datos === null && !error}>
      <h2 id="titulo-resenas">Lo que dicen tus clientes</h2>
      {contenido}
    </section>
  )
}
