// Formulario de datos de la botica de la corrección (H7). El registro (H1) va por pasos en RegistroBoticaPage.
// H2 - Validación del RUC con SUNAT: en el hook useVerificacionRuc
// H3 - Ubicación de la botica marcada en un mapa (latitud y longitud opcionales)
import { useState } from 'react'
import { cuerpoBotica, esErrorDeRuc, useVerificacionRuc } from '../hooks/useVerificacionRuc.js'
import { DISTRITOS } from '../utils/distritos.js'
import { validarRuc } from '../utils/ruc.js'
import { propsError, telefonoInvalido } from '../utils/validacion.js'
import Alerta from './Alerta.jsx'
import Campo from './Campo.jsx'
import MapaUbicacion from './MapaUbicacion.jsx'

const DATOS_VACIOS = { nombreComercial: '', ruc: '', razonSocial: '', direccion: '', distrito: '', telefono: '' }

function validar(datos) {
  const errores = {}
  if (!datos.nombreComercial.trim()) errores.nombreComercial = 'Ingresa el nombre comercial.'
  const ruc = validarRuc(datos.ruc)
  if (!ruc.valido) errores.ruc = ruc.mensaje
  if (!datos.razonSocial.trim()) errores.razonSocial = 'Ingresa la razón social que figura en SUNAT.'
  if (!datos.direccion.trim()) errores.direccion = 'Ingresa la dirección de la botica.'
  if (!datos.distrito.trim()) errores.distrito = 'Ingresa el distrito.'
  if (telefonoInvalido(datos.telefono)) errores.telefono = 'Usa solo números (6 a 15 dígitos).'
  return errores
}

/**
 * onGuardar(payload) recibe el cuerpo de POST /boticas ya validado y con el RUC confirmado.
 * Si lanza un ApiError, el formulario lo muestra (en el campo RUC cuando corresponde).
 */
export default function BoticaForm({
  inicial = DATOS_VACIOS,
  ubicacionInicial = null,
  textoBoton,
  textoEnviando,
  errorGenerico,
  onGuardar,
  children,
}) {
  const [datos, setDatos] = useState(inicial)
  const [ubicacion, setUbicacion] = useState(ubicacionInicial)
  const [errores, setErrores] = useState({})
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState('')
  const ruc = useVerificacionRuc({ rucInicial: inicial.ruc, setDatos, setErrores })
  const { razonDeSunat } = ruc

  const cambiar = (campo) => (e) => {
    const valor = campo === 'ruc' ? e.target.value.replace(/\D/g, '').slice(0, 11) : e.target.value
    setDatos((d) => ({ ...d, [campo]: valor }))
    setErrores((er) => ({ ...er, [campo]: undefined }))
    if (campo === 'ruc') ruc.cambiarRuc(valor)
  }

  // H3: completa dirección y distrito con lo que devuelve el mapa (solo los campos que vengan)
  function usarDireccionDelMapa({ direccion, distrito }) {
    setDatos((d) => ({ ...d, ...(direccion && { direccion }), ...(distrito && { distrito }) }))
    setErrores((er) => ({ ...er, direccion: undefined, distrito: undefined }))
  }

  async function enviar(e) {
    e.preventDefault()
    const nuevos = validar(datos)
    setErrores(nuevos)
    setError('')
    // La razón social puede faltar todavía si SUNAT la va a completar
    const { razonSocial: errorRazonSocial, ...otrosErrores } = nuevos
    if (Object.keys(otrosErrores).length) return

    try {
      setEnviando('Verificando RUC...')
      // Reutiliza la consulta hecha al escribir el RUC (o la que sigue en curso)
      const verificacion = await ruc.verificar(datos.ruc)
      if (!verificacion.valido) {
        setErrores({ ruc: verificacion.mensaje || 'El RUC no es válido.' })
        return
      }
      if (errorRazonSocial && verificacion.fuente !== 'SUNAT') return

      setEnviando(textoEnviando)
      await onGuardar(cuerpoBotica(datos, verificacion, ubicacion))
    } catch (err) {
      if (esErrorDeRuc(err)) setErrores({ ruc: err.mensaje })
      else setError(err.mensaje ?? errorGenerico)
    } finally {
      setEnviando('')
    }
  }

  const rucEnVivo = ruc.estadoRuc(datos.ruc, errores.ruc)
  const ayudaDireccion = ruc.ayudaDireccion(datos.ruc, datos.direccion)

  return (
    <form className="tarjeta formulario" onSubmit={enviar} noValidate>
      <Alerta tipo="error">{error && <p>{error}</p>}</Alerta>

      <div className="fila-campos">
        <Campo id="nombreComercial" etiqueta="Nombre comercial" error={errores.nombreComercial}>
          <input
            {...propsError('nombreComercial', errores.nombreComercial)}
            value={datos.nombreComercial}
            onChange={cambiar('nombreComercial')}
            placeholder="Botica Salud Total"
          />
        </Campo>
        <Campo id="ruc" etiqueta="RUC" {...rucEnVivo}>
          <input
            {...propsError('ruc', rucEnVivo.error)}
            inputMode="numeric"
            autoComplete="off"
            maxLength={11}
            value={datos.ruc}
            onChange={cambiar('ruc')}
            placeholder="20601234565"
          />
        </Campo>
      </div>

      <Campo
        id="razonSocial"
        etiqueta="Razón social"
        error={errores.razonSocial}
        ayuda={razonDeSunat ? 'Tomada de SUNAT' : undefined}
      >
        <input
          {...propsError('razonSocial', errores.razonSocial)}
          readOnly={razonDeSunat}
          value={datos.razonSocial}
          onChange={cambiar('razonSocial')}
          placeholder="Salud Total Farma S.A.C."
        />
      </Campo>

      <Campo id="direccion" etiqueta="Dirección" error={errores.direccion} ayuda={ayudaDireccion}>
        <input
          {...propsError('direccion', errores.direccion)}
          autoComplete="street-address"
          value={datos.direccion}
          onChange={cambiar('direccion')}
          placeholder="Av. Lima Sur 450"
        />
      </Campo>

      <div className="fila-campos">
        <Campo id="distrito" etiqueta="Distrito" error={errores.distrito}>
          <input
            {...propsError('distrito', errores.distrito)}
            list="lista-distritos"
            value={datos.distrito}
            onChange={cambiar('distrito')}
          />
          <datalist id="lista-distritos">
            {DISTRITOS.map((d) => (
              <option key={d} value={d} />
            ))}
          </datalist>
        </Campo>
        <Campo id="telefono" etiqueta="Teléfono" opcional error={errores.telefono}>
          <input
            {...propsError('telefono', errores.telefono)}
            type="tel"
            inputMode="numeric"
            value={datos.telefono}
            onChange={cambiar('telefono')}
            placeholder="014567890"
          />
        </Campo>
      </div>

      <div className="campo">
        <span className="etiqueta-campo">
          Ubicación en el mapa <span className="opcional">(opcional)</span>
        </span>
        <MapaUbicacion valor={ubicacion} onCambio={setUbicacion} onUsarDireccion={usarDireccionDelMapa} />
      </div>

      <div className="acciones-form">
        {children}
        <button type="submit" className="btn btn-primario" disabled={Boolean(enviando)}>
          {enviando || textoBoton}
        </button>
      </div>
    </form>
  )
}
