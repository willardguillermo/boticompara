// Formulario de datos de la botica, compartido por el registro (H1) y la corrección (H7).
// H2 - Validación del RUC: en vivo con el dígito verificador y, cuando pasa, una sola consulta
//      a GET /boticas/validar-ruc/{ruc} (SUNAT) que trae la razón social, el estado y la condición
// H3 - Ubicación de la botica marcada en un mapa (latitud y longitud opcionales)
import { useRef, useState } from 'react'
import { api } from '../services/index.js'
import { validarRuc } from '../utils/ruc.js'
import { propsError, telefonoInvalido } from '../utils/validacion.js'
import Alerta from './Alerta.jsx'
import Campo from './Campo.jsx'
import MapaUbicacion from './MapaUbicacion.jsx'

const DISTRITOS = [
  'Ate', 'Barranco', 'Breña', 'Carabayllo', 'Chaclacayo', 'Chorrillos', 'Cieneguilla', 'Comas',
  'El Agustino', 'Independencia', 'Jesús María', 'La Molina', 'La Victoria', 'Lima', 'Lince',
  'Los Olivos', 'Lurigancho-Chosica', 'Lurín', 'Magdalena del Mar', 'Miraflores', 'Pueblo Libre',
  'Puente Piedra', 'Rímac', 'San Borja', 'San Isidro', 'San Juan de Lurigancho',
  'San Juan de Miraflores', 'San Luis', 'San Martín de Porres', 'San Miguel', 'Santa Anita',
  'Santiago de Surco', 'Surquillo', 'Villa El Salvador', 'Villa María del Triunfo',
]

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

/** Mensaje del RUC mientras se escribe: ayuda, error u OK. */
function estadoRucEnVivo(ruc) {
  if (!ruc) return { ayuda: '11 dígitos. Empieza con 10, 15, 17 o 20.' }
  const resultado = validarRuc(ruc)
  if (resultado.valido) return { ok: '✓ RUC válido' }
  // Mientras faltan dígitos no es un error todavía, salvo que el prefijo ya sea incorrecto
  return resultado.completo || resultado.mensaje.startsWith('El RUC debe empezar')
    ? { error: resultado.mensaje }
    : { ayuda: resultado.mensaje }
}

/** Mensaje del RUC según la respuesta de GET /boticas/validar-ruc/{ruc}. */
function estadoRucConsultado(resultado) {
  if (!resultado.valido) return { error: resultado.mensaje || 'El RUC no es válido.' }
  if (resultado.fuente === 'SUNAT') {
    return { ok: `✓ Verificado en SUNAT: ${resultado.razonSocial} · ${resultado.estado} · ${resultado.condicion}` }
  }
  return { ok: 'RUC válido. No se pudo consultar SUNAT ahora; se verificará al enviar.' }
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
  // H2: { ruc, cargando: true } mientras se consulta, luego { ruc, resultado }
  const [consulta, setConsulta] = useState(null)
  const [razonDeSunat, setRazonDeSunat] = useState(false)
  // SUNAT (Decolecta) tiene 100 consultas al mes: se guarda el último RUC consultado y la
  // consulta en curso para no repetir la llamada
  const ultimaConsulta = useRef(null)
  const consultaEnCurso = useRef(null)
  const rucActual = useRef(inicial.ruc)

  /** Devuelve la respuesta de validar-ruc, reutilizando la última o la que está en curso. */
  function consultarRuc(ruc) {
    if (ultimaConsulta.current?.ruc === ruc) return Promise.resolve(ultimaConsulta.current.resultado)
    if (consultaEnCurso.current?.ruc === ruc) return consultaEnCurso.current.promesa
    const promesa = api
      .validarRuc(ruc)
      .then((resultado) => {
        ultimaConsulta.current = { ruc, resultado }
        return resultado
      })
      .finally(() => {
        if (consultaEnCurso.current?.promesa === promesa) consultaEnCurso.current = null
      })
    consultaEnCurso.current = { ruc, promesa }
    return promesa
  }

  /** Muestra la respuesta solo si el RUC no cambió mientras se consultaba. */
  function aplicarConsulta(ruc, resultado) {
    if (rucActual.current !== ruc) return
    setConsulta({ ruc, resultado })
    if (resultado.valido && resultado.fuente === 'SUNAT') {
      setDatos((d) => ({ ...d, razonSocial: resultado.razonSocial }))
      setErrores((er) => ({ ...er, razonSocial: undefined }))
      setRazonDeSunat(true)
    }
  }

  function cambiarRuc(ruc) {
    if (ruc === rucActual.current) return
    rucActual.current = ruc
    setConsulta(null)
    // La razón social vuelve a ser editable; se borra solo si la habíamos puesto desde SUNAT
    if (razonDeSunat) {
      setDatos((d) => ({ ...d, razonSocial: '' }))
      setRazonDeSunat(false)
    }
    if (!validarRuc(ruc).valido) return

    if (ultimaConsulta.current?.ruc === ruc) {
      aplicarConsulta(ruc, ultimaConsulta.current.resultado)
      return
    }
    setConsulta({ ruc, cargando: true })
    consultarRuc(ruc).then(
      (resultado) => aplicarConsulta(ruc, resultado),
      // Si falla la red se muestra solo la validación local; al enviar se vuelve a intentar
      () => rucActual.current === ruc && setConsulta(null),
    )
  }

  const cambiar = (campo) => (e) => {
    const valor = campo === 'ruc' ? e.target.value.replace(/\D/g, '').slice(0, 11) : e.target.value
    setDatos((d) => ({ ...d, [campo]: valor }))
    setErrores((er) => ({ ...er, [campo]: undefined }))
    if (campo === 'ruc') cambiarRuc(valor)
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
      const verificacion = await consultarRuc(datos.ruc)
      aplicarConsulta(datos.ruc, verificacion)
      if (!verificacion.valido) {
        setErrores({ ruc: verificacion.mensaje || 'El RUC no es válido.' })
        return
      }
      const deSunat = verificacion.fuente === 'SUNAT'
      if (errorRazonSocial && !deSunat) return

      setEnviando(textoEnviando)
      await onGuardar({
        nombreComercial: datos.nombreComercial.trim(),
        ruc: datos.ruc,
        razonSocial: deSunat ? verificacion.razonSocial : datos.razonSocial.trim(),
        direccion: datos.direccion.trim(),
        distrito: datos.distrito.trim(),
        telefono: datos.telefono.trim() || null,
        // H3: el backend exige ambas coordenadas o ninguna
        ...(ubicacion && { latitud: ubicacion.latitud, longitud: ubicacion.longitud }),
      })
    } catch (err) {
      if (err.status === 400 && /ruc/i.test(err.mensaje)) setErrores({ ruc: err.mensaje })
      else if (err.status === 409 && /ruc/i.test(err.mensaje)) setErrores({ ruc: err.mensaje })
      else setError(err.mensaje ?? errorGenerico)
    } finally {
      setEnviando('')
    }
  }

  let rucEnVivo
  if (errores.ruc) rucEnVivo = { error: errores.ruc }
  else if (consulta?.ruc !== datos.ruc) rucEnVivo = estadoRucEnVivo(datos.ruc)
  else if (consulta.cargando) rucEnVivo = { ayuda: 'Consultando SUNAT...' }
  else rucEnVivo = estadoRucConsultado(consulta.resultado)

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

      <Campo id="direccion" etiqueta="Dirección" error={errores.direccion}>
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
