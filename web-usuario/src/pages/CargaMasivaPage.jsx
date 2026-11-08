// H11 - Carga masiva de productos con CSV o Excel .xlsx (POST /productos/carga-masiva)
// D4 - Columna opcional medicamentoId para enlazar con el catálogo maestro (GET /medicamentos para buscar el ID)
// Solo con la botica APROBADO (guardia SoloAprobada en App.jsx).
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import Campo from '../components/Campo.jsx'
import Cargando from '../components/Cargando.jsx'
import Icono from '../components/Icono.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { api } from '../services/index.js'
import { ACCEPT_CARGA, propsError, validarArchivoCarga } from '../utils/validacion.js'

// Una fila enlazada al catálogo maestro (Panadol, ID 2 en el catálogo maestro) y otra con medicamentoId vacío
const PLANTILLA = [
  'nombreComercial,principioActivo,presentacion,precio,stock,stockMinimo,medicamentoId',
  'Panadol,Paracetamol,Tableta 500 mg x 10,8.50,40,5,2',
  'Ibuprofeno Genérico,Ibuprofeno,Tableta 400 mg x 10,3.20,100,10,',
].join('\r\n')

/** Genera la plantilla en el navegador. El BOM hace que Excel lea bien las tildes. */
function descargarPlantilla() {
  const blob = new Blob(['﻿', PLANTILLA, '\r\n'], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = 'plantilla-productos.csv'
  document.body.append(enlace)
  enlace.click()
  enlace.remove()
  URL.revokeObjectURL(url)
}

const MIN_BUSQUEDA = 2

/** D4: el dueño busca en el catálogo maestro el ID que va en la columna medicamentoId. */
function BuscadorMedicamento() {
  const [busqueda, setBusqueda] = useState('')
  const [medicamentos, setMedicamentos] = useState(null)
  const [error, setError] = useState('')
  const termino = busqueda.trim()
  const buscando = termino.length >= MIN_BUSQUEDA

  // Busca en el servidor con una pequeña espera mientras se escribe, como el catálogo
  useEffect(() => {
    if (!buscando) return undefined
    let activo = true
    const espera = setTimeout(() => {
      api
        .buscarMedicamentos(termino)
        .then((lista) => {
          if (!activo) return
          setMedicamentos(lista)
          setError('')
        })
        .catch((err) => {
          if (!activo || err.status === 401) return
          setMedicamentos(null)
          setError(err.mensaje ?? 'No se pudo buscar en el catálogo de medicamentos.')
        })
    }, 300)
    return () => {
      activo = false
      clearTimeout(espera)
    }
  }, [buscando, termino])

  function cambiar(evento) {
    setBusqueda(evento.target.value)
    setMedicamentos(null)
    setError('')
  }

  return (
    <section className="tarjeta" aria-labelledby="titulo-buscar-medicamento">
      <h2 id="titulo-buscar-medicamento">Buscar ID de medicamento</h2>
      <p className="texto-suave">
        Escribe el nombre comercial o el principio activo y copia el número de la columna ID en{' '}
        <code>medicamentoId</code>.
      </p>
      <div className="campo buscador">
        <Icono nombre="buscar" />
        <input
          type="search"
          aria-label="Buscar medicamento por nombre comercial o principio activo"
          placeholder="Por ejemplo: paracetamol"
          value={busqueda}
          onChange={cambiar}
        />
      </div>

      <Alerta tipo="error">{error && <p>{error}</p>}</Alerta>

      {buscando && !error && (
        <div className="tabla-contenedor tabla-medicamentos" aria-live="polite">
          {medicamentos === null ? (
            <Cargando texto="Buscando medicamentos..." />
          ) : medicamentos.length === 0 ? (
            <p className="texto-suave">No hay medicamentos que coincidan con &quot;{termino}&quot;.</p>
          ) : (
            <table className="tabla">
              <thead>
                <tr>
                  <th scope="col">Nombre comercial</th>
                  <th scope="col">Principio activo</th>
                  <th scope="col">Presentación</th>
                  <th scope="col" className="numero">ID</th>
                </tr>
              </thead>
              <tbody>
                {medicamentos.map((m) => (
                  <tr key={m.id}>
                    <td data-etiqueta="Nombre" className="celda-nombre">
                      <strong>{m.nombreComercial}</strong>
                    </td>
                    <td data-etiqueta="Principio activo">{m.principioActivo}</td>
                    <td data-etiqueta="Presentación">
                      {m.concentracion ? `${m.presentacion} ${m.concentracion}` : m.presentacion}
                    </td>
                    <td data-etiqueta="ID" className="numero">
                      <code>{m.id}</code>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </section>
  )
}

function ResumenCarga({ resultado }) {
  const { creados, actualizados, errores } = resultado
  return (
    <section className="tarjeta" aria-labelledby="titulo-resultado">
      <h2 id="titulo-resultado">Resultado de la carga</h2>
      <div className="resumen-carga">
        <div className="resumen-item resumen-exito">
          <span className="resumen-numero">{creados}</span>
          <span>{creados === 1 ? 'producto creado' : 'productos creados'}</span>
        </div>
        <div className="resumen-item resumen-info">
          <span className="resumen-numero">{actualizados}</span>
          <span>{actualizados === 1 ? 'producto actualizado' : 'productos actualizados'}</span>
        </div>
        <div className={`resumen-item ${errores.length ? 'resumen-error' : ''}`}>
          <span className="resumen-numero">{errores.length}</span>
          <span>{errores.length === 1 ? 'fila con errores' : 'filas con errores'}</span>
        </div>
      </div>

      {errores.length === 0 ? (
        <Alerta tipo="exito">
          <p>Todas las filas se cargaron correctamente.</p>
        </Alerta>
      ) : (
        <>
          <p className="texto-suave">
            Las filas válidas ya se guardaron. Corrige estas filas en tu archivo y vuelve a subirlo: los
            productos que ya existen solo se actualizan.
          </p>
          <div className="tabla-contenedor tabla-errores">
            <table className="tabla">
              <thead>
                <tr>
                  <th scope="col" className="numero">Fila</th>
                  <th scope="col">Error</th>
                </tr>
              </thead>
              <tbody>
                {errores.map((e) => (
                  <tr key={`${e.fila}-${e.mensaje}`}>
                    <td data-etiqueta="Fila" className="numero">{e.fila}</td>
                    <td data-etiqueta="Error">{e.mensaje}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  )
}

export default function CargaMasivaPage() {
  const toast = useToast()
  const [archivo, setArchivo] = useState(null)
  const [errorArchivo, setErrorArchivo] = useState('')
  const [errorApi, setErrorApi] = useState('')
  const [cargando, setCargando] = useState(false)
  const [resultado, setResultado] = useState(null)
  // Cambiar la key vacía el <input type="file">, que no se puede controlar con value
  const [claveInput, setClaveInput] = useState(0)

  function elegir(evento) {
    const elegido = evento.target.files?.[0] ?? null
    setArchivo(elegido)
    setErrorApi('')
    setErrorArchivo(elegido ? validarArchivoCarga(elegido) : '')
  }

  async function subir(evento) {
    evento.preventDefault()
    const error = validarArchivoCarga(archivo)
    if (error) {
      setErrorArchivo(error)
      return
    }
    setCargando(true)
    setErrorApi('')
    setResultado(null)
    try {
      const respuesta = await api.cargarProductosCsv(archivo)
      setResultado({ ...respuesta, errores: [...respuesta.errores].sort((a, b) => a.fila - b.fila) })
      setArchivo(null)
      setClaveInput((k) => k + 1)
      const { creados, actualizados } = respuesta
      toast(
        `Carga terminada: ${creados} ${creados === 1 ? 'creado' : 'creados'} y ` +
          `${actualizados} ${actualizados === 1 ? 'actualizado' : 'actualizados'}.`,
      )
    } catch (err) {
      // Con 401 la sesión ya se cerró y se redirige al login
      if (err.status !== 401) setErrorApi(err.mensaje ?? 'No se pudo cargar el archivo.')
    } finally {
      setCargando(false)
    }
  }

  return (
    <div className="carga-masiva">
      <div className="encabezado-pagina">
        <div>
          <h1>Carga masiva</h1>
          <p className="texto-suave">
            Agrega o actualiza muchos productos a la vez con un archivo CSV o Excel. Si un producto ya existe (mismo
            nombre comercial y presentación), se actualizan su precio y su stock.
          </p>
        </div>
        <Link to="/panel/catalogo" className="btn btn-secundario">
          Volver al catálogo
        </Link>
      </div>

      <section className="tarjeta" aria-labelledby="titulo-plantilla">
        <h2 id="titulo-plantilla">1. Prepara tu archivo</h2>
        <p className="texto-suave">
          Usa la plantilla. Las columnas se reconocen por su nombre en la primera fila, no por su orden, y son las
          mismas en CSV y en Excel (.xlsx):
        </p>
        <ul className="lista-columnas texto-suave">
          <li>
            <code>nombreComercial</code>, <code>principioActivo</code> y <code>presentacion</code>.
          </li>
          <li>
            <code>precio</code> (con punto decimal) y <code>stock</code>.
          </li>
          <li>
            <code>stockMinimo</code> (opcional): si lo dejas vacío queda en 5.
          </li>
          <li>
            <code>medicamentoId</code> (opcional): enlaza el producto con el catálogo maestro de medicamentos. Si lo
            llenas, el principio activo se toma del medicamento y se ignora el de la fila. Si lo dejas vacío, el
            producto conserva el enlace que ya tenía. Busca el ID más abajo.
          </li>
        </ul>
        <p className="texto-suave">
          Si la llenas en Excel, puedes subirla tal cual como .xlsx o, si prefieres CSV, guardarla como &quot;CSV UTF-8
          (delimitado por comas)&quot;. Máximo 1000 productos.
        </p>
        <button type="button" className="btn btn-secundario" onClick={descargarPlantilla}>
          <Icono nombre="bajar" /> Descargar plantilla
        </button>
      </section>

      <BuscadorMedicamento />

      <section className="tarjeta" aria-labelledby="titulo-subir">
        <h2 id="titulo-subir">2. Sube el archivo</h2>
        <form className="formulario" onSubmit={subir} noValidate>
          <Campo id="archivoCarga" etiqueta="Archivo CSV o Excel" error={errorArchivo} ayuda="CSV o .xlsx, máximo 5 MB.">
            <input
              key={claveInput}
              type="file"
              accept={ACCEPT_CARGA}
              onChange={elegir}
              disabled={cargando}
              {...propsError('archivoCarga', errorArchivo)}
            />
          </Campo>

          <Alerta tipo="error">{errorApi && <p>{errorApi}</p>}</Alerta>

          <div className="acciones-form">
            <button type="submit" className="btn btn-primario" disabled={cargando || !archivo || Boolean(errorArchivo)}>
              <Icono nombre="subir" /> {cargando ? 'Cargando...' : 'Subir'}
            </button>
          </div>
        </form>
      </section>

      {resultado && <ResumenCarga resultado={resultado} />}
    </div>
  )
}
