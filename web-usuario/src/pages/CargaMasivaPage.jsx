// H11 - Carga masiva de productos con CSV (POST /productos/carga-masiva)
// Solo con la botica APROBADO (guardia SoloAprobada en App.jsx).
import { useState } from 'react'
import { Link } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import Campo from '../components/Campo.jsx'
import Icono from '../components/Icono.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { api } from '../services/index.js'
import { ACCEPT_CSV, propsError, validarArchivoCsv } from '../utils/validacion.js'

const PLANTILLA = [
  'nombreComercial,principioActivo,presentacion,precio,stock,stockMinimo',
  'Panadol,Paracetamol,Tableta 500 mg x 10,8.50,40,5',
  'Ibuprofeno Genérico,Ibuprofeno,Tableta 400 mg x 10,3.20,100,10',
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
    setErrorArchivo(elegido ? validarArchivoCsv(elegido) : '')
  }

  async function subir(evento) {
    evento.preventDefault()
    const error = validarArchivoCsv(archivo)
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
      toast(`Carga terminada: ${respuesta.creados} creados y ${respuesta.actualizados} actualizados.`)
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
            Agrega o actualiza muchos productos a la vez con un archivo CSV. Si un producto ya existe (mismo
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
          Usa la plantilla: columnas <code>nombreComercial</code>, <code>principioActivo</code>,{' '}
          <code>presentacion</code>, <code>precio</code> (con punto decimal), <code>stock</code> y{' '}
          <code>stockMinimo</code> (opcional; si lo dejas vacío queda en 5). En Excel, guárdalo como
          &quot;CSV UTF-8 (delimitado por comas)&quot;. Máximo 1000 productos.
        </p>
        <button type="button" className="btn btn-secundario" onClick={descargarPlantilla}>
          <Icono nombre="bajar" /> Descargar plantilla
        </button>
      </section>

      <section className="tarjeta" aria-labelledby="titulo-subir">
        <h2 id="titulo-subir">2. Sube el archivo</h2>
        <form className="formulario" onSubmit={subir} noValidate>
          <Campo id="archivoCsv" etiqueta="Archivo CSV" error={errorArchivo} ayuda="Solo .csv, máximo 5 MB.">
            <input
              key={claveInput}
              type="file"
              accept={ACCEPT_CSV}
              onChange={elegir}
              disabled={cargando}
              {...propsError('archivoCsv', errorArchivo)}
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
