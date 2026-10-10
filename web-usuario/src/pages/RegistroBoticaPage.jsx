// H1 - Registro de la botica del dueño (queda PENDIENTE hasta que el admin la aprueba)
// H2 - Validación del RUC: en vivo con el dígito verificador y confirmada con
//      GET /boticas/validar-ruc/{ruc} antes de enviar
// H3 - Ubicación de la botica marcada en un mapa (latitud y longitud opcionales)
import { useState } from 'react'
import { useNavigate } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import Campo from '../components/Campo.jsx'
import MapaUbicacion from '../components/MapaUbicacion.jsx'
import { useBotica } from '../context/BoticaContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { api } from '../services/index.js'
import { validarRuc } from '../utils/ruc.js'
import { propsError, telefonoInvalido } from '../utils/validacion.js'

const DISTRITOS = [
  'Ate', 'Barranco', 'Breña', 'Carabayllo', 'Chaclacayo', 'Chorrillos', 'Cieneguilla', 'Comas',
  'El Agustino', 'Independencia', 'Jesús María', 'La Molina', 'La Victoria', 'Lima', 'Lince',
  'Los Olivos', 'Lurigancho-Chosica', 'Lurín', 'Magdalena del Mar', 'Miraflores', 'Pueblo Libre',
  'Puente Piedra', 'Rímac', 'San Borja', 'San Isidro', 'San Juan de Lurigancho',
  'San Juan de Miraflores', 'San Luis', 'San Martín de Porres', 'San Miguel', 'Santa Anita',
  'Santiago de Surco', 'Surquillo', 'Villa El Salvador', 'Villa María del Triunfo',
]

const VACIO = { nombreComercial: '', ruc: '', razonSocial: '', direccion: '', distrito: '', telefono: '' }

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

export default function RegistroBoticaPage() {
  const { recargar } = useBotica()
  const toast = useToast()
  const navigate = useNavigate()

  const [datos, setDatos] = useState(VACIO)
  const [ubicacion, setUbicacion] = useState(null)
  const [errores, setErrores] = useState({})
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState('')

  const cambiar = (campo) => (e) => {
    const valor = campo === 'ruc' ? e.target.value.replace(/\D/g, '').slice(0, 11) : e.target.value
    setDatos((d) => ({ ...d, [campo]: valor }))
    setErrores((er) => ({ ...er, [campo]: undefined }))
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
    if (Object.keys(nuevos).length) return

    try {
      setEnviando('Verificando RUC...')
      const verificacion = await api.validarRuc(datos.ruc)
      if (!verificacion.valido) {
        setErrores({ ruc: verificacion.mensaje || 'El RUC no es válido.' })
        return
      }

      setEnviando('Registrando botica...')
      await api.registrarBotica({
        nombreComercial: datos.nombreComercial.trim(),
        ruc: datos.ruc,
        razonSocial: datos.razonSocial.trim(),
        direccion: datos.direccion.trim(),
        distrito: datos.distrito.trim(),
        telefono: datos.telefono.trim() || null,
        // H3: el backend exige ambas coordenadas o ninguna
        ...(ubicacion && { latitud: ubicacion.latitud, longitud: ubicacion.longitud }),
      })
      await recargar()
      toast('Botica registrada. Quedó pendiente de aprobación.')
      navigate('/panel/estado', { replace: true })
    } catch (err) {
      if (err.status === 400 && /ruc/i.test(err.mensaje)) setErrores({ ruc: err.mensaje })
      else if (err.status === 409 && /ruc/i.test(err.mensaje)) setErrores({ ruc: err.mensaje })
      else setError(err.mensaje ?? 'No se pudo registrar la botica.')
    } finally {
      setEnviando('')
    }
  }

  const rucEnVivo = errores.ruc ? { error: errores.ruc } : estadoRucEnVivo(datos.ruc)

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Registra tu botica</h1>
          <p className="texto-suave">
            Un administrador revisará tus datos. Cuando tu botica sea aprobada podrás publicar tu catálogo.
          </p>
        </div>
      </div>

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

        <Campo id="razonSocial" etiqueta="Razón social" error={errores.razonSocial}>
          <input
            {...propsError('razonSocial', errores.razonSocial)}
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
          <button type="submit" className="btn btn-primario" disabled={Boolean(enviando)}>
            {enviando || 'Enviar solicitud'}
          </button>
        </div>
      </form>
    </>
  )
}
