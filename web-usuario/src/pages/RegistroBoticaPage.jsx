// H1 - Registro de la botica del dueño (queda PENDIENTE hasta que el admin la aprueba), por pasos:
//      paso 2 datos de la botica y paso 3 ubicación. Los datos viven aquí para no perderlos al ir y volver.
// H2 - Validación del RUC con SUNAT (hook useVerificacionRuc) en el paso 2
// H3 - Dirección, distrito y ubicación en el mapa en el paso 3
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import Campo from '../components/Campo.jsx'
import Icono from '../components/Icono.jsx'
import MapaUbicacion from '../components/MapaUbicacion.jsx'
import RegistroPasos from '../components/RegistroPasos.jsx'
import { useBotica } from '../context/BoticaContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { cuerpoBotica, esErrorDeRuc, useVerificacionRuc } from '../hooks/useVerificacionRuc.js'
import { api } from '../services/index.js'
import { DISTRITOS, distritoDeLista } from '../utils/distritos.js'
import { validarRuc } from '../utils/ruc.js'
import { propsError, telefonoInvalido } from '../utils/validacion.js'

const DATOS_VACIOS = { nombreComercial: '', ruc: '', razonSocial: '', direccion: '', distrito: '', telefono: '' }

/** Paso 2. La razón social se revisa después de consultar SUNAT, que puede completarla. */
function validarDatos(datos) {
  const errores = {}
  if (!datos.nombreComercial.trim()) errores.nombreComercial = 'Ingresa el nombre comercial.'
  const ruc = validarRuc(datos.ruc)
  if (!ruc.valido) errores.ruc = ruc.mensaje
  if (!datos.razonSocial.trim()) errores.razonSocial = 'Ingresa la razón social que figura en SUNAT.'
  if (telefonoInvalido(datos.telefono)) errores.telefono = 'Usa solo números (6 a 15 dígitos).'
  return errores
}

/** Paso 3. */
function validarUbicacion(datos) {
  const errores = {}
  if (!datos.direccion.trim()) errores.direccion = 'Ingresa la dirección de la botica.'
  if (!datos.distrito.trim()) errores.distrito = 'Elige el distrito.'
  return errores
}

export default function RegistroBoticaPage() {
  const { recargar } = useBotica()
  const toast = useToast()
  const navigate = useNavigate()

  const [paso, setPaso] = useState(2)
  const [datos, setDatos] = useState(DATOS_VACIOS)
  const [ubicacion, setUbicacion] = useState(null)
  const [errores, setErrores] = useState({})
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState('')
  const ruc = useVerificacionRuc({ setDatos, setErrores })
  const titulo = useRef(null)
  const primerPaso = useRef(true)

  // Al cambiar de paso, el foco va al título del nuevo paso (no al entrar a la pantalla)
  useEffect(() => {
    if (primerPaso.current) {
      primerPaso.current = false
      return
    }
    window.scrollTo({ top: 0 })
    titulo.current?.focus()
  }, [paso])

  function irA(numero) {
    setError('')
    setPaso(numero)
  }

  const cambiar = (campo) => (e) => {
    const valor = campo === 'ruc' ? e.target.value.replace(/\D/g, '').slice(0, 11) : e.target.value
    setDatos((d) => ({ ...d, [campo]: valor }))
    setErrores((er) => ({ ...er, [campo]: undefined }))
    if (campo === 'ruc') ruc.cambiarRuc(valor)
  }

  // H3: completa dirección y distrito con lo que devuelve el mapa (solo los campos que vengan)
  function usarDireccionDelMapa({ direccion, distrito }) {
    const deLista = distrito && (distritoDeLista(distrito) ?? distrito)
    setDatos((d) => ({ ...d, ...(direccion && { direccion }), ...(deLista && { distrito: deLista }) }))
    setErrores((er) => ({ ...er, direccion: undefined, distrito: undefined }))
  }

  async function siguiente(e) {
    e.preventDefault()
    const nuevos = validarDatos(datos)
    setErrores(nuevos)
    setError('')
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
      // fuente LOCAL (SUNAT no respondió): avanza con la razón social escrita, como el formulario anterior
      if (errorRazonSocial && verificacion.fuente !== 'SUNAT') return
      irA(3)
    } catch (err) {
      if (esErrorDeRuc(err)) setErrores({ ruc: err.mensaje })
      else setError(err.mensaje ?? 'No se pudo verificar el RUC.')
    } finally {
      setEnviando('')
    }
  }

  // PROVISIONAL hasta el paso 5: el paso 3 registra la botica como lo hacía el formulario anterior
  async function enviar(e) {
    e.preventDefault()
    const nuevos = validarUbicacion(datos)
    setErrores(nuevos)
    setError('')
    if (Object.keys(nuevos).length) return

    try {
      setEnviando('Verificando RUC...')
      const verificacion = await ruc.verificar(datos.ruc)
      if (!verificacion.valido) {
        setErrores({ ruc: verificacion.mensaje || 'El RUC no es válido.' })
        irA(2)
        return
      }
      if (!datos.razonSocial.trim() && verificacion.fuente !== 'SUNAT') {
        setErrores({ razonSocial: 'Ingresa la razón social que figura en SUNAT.' })
        irA(2)
        return
      }

      setEnviando('Registrando botica...')
      await api.registrarBotica(cuerpoBotica(datos, verificacion, ubicacion))
      await recargar()
      toast('Botica registrada. Quedó pendiente de aprobación.')
      navigate('/panel/estado', { replace: true })
    } catch (err) {
      // Un RUC inválido o ya registrado (409) se corrige en el paso 2
      if (esErrorDeRuc(err)) {
        setErrores({ ruc: err.mensaje })
        irA(2)
      } else {
        setError(err.mensaje ?? 'No se pudo registrar la botica.')
      }
    } finally {
      setEnviando('')
    }
  }

  const alerta = <Alerta tipo="error">{error && <p>{error}</p>}</Alerta>

  if (paso === 2) {
    const rucEnVivo = ruc.estadoRuc(datos.ruc, errores.ruc)
    return (
      <RegistroPasos
        paso={2}
        pie={
          // Cada botón del pie lleva su key: si React reutilizara el mismo <button> al cambiar de paso,
          // el clic en "Atrás" terminaría como submit del formulario del paso 2 y regresaría al paso 3
          <button key="siguiente" type="submit" form="form-botica-datos" className="btn btn-primario" disabled={Boolean(enviando)}>
            {enviando || 'Siguiente'}
            {!enviando && <Icono nombre="flecha" />}
          </button>
        }
      >
        <Alerta tipo="exito">
          <p>Tu cuenta fue creada. Si sales ahora, puedes iniciar sesión más tarde y continuar desde aquí.</p>
        </Alerta>
        <div className="pasos-encabezado">
          <h1 ref={titulo} tabIndex={-1}>
            Datos de tu botica
          </h1>
          <p>Así te verán los compradores en los resultados de búsqueda.</p>
        </div>

        <form id="form-botica-datos" className="formulario" onSubmit={siguiente} noValidate>
          {alerta}
          <div className="pasos-campos">
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
            <div className="campo-ancho">
              <Campo
                id="razonSocial"
                etiqueta="Razón social"
                error={errores.razonSocial}
                ayuda={ruc.razonDeSunat ? 'Tomada de SUNAT' : undefined}
              >
                <input
                  {...propsError('razonSocial', errores.razonSocial)}
                  readOnly={ruc.razonDeSunat}
                  value={datos.razonSocial}
                  onChange={cambiar('razonSocial')}
                  placeholder="Salud Total Farma S.A.C."
                />
              </Campo>
            </div>
            <Campo id="telefono" etiqueta="Teléfono de la botica" opcional error={errores.telefono}>
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
        </form>
      </RegistroPasos>
    )
  }

  return (
    <RegistroPasos
      paso={3}
      onIrAPaso={irA}
      pie={
        <>
          <button key="atras" type="button" className="btn btn-secundario" onClick={() => irA(2)} disabled={Boolean(enviando)}>
            Atrás
          </button>
          <button key="enviar" type="submit" form="form-botica-ubicacion" className="btn btn-primario" disabled={Boolean(enviando)}>
            {enviando || 'Enviar solicitud'}
          </button>
        </>
      }
    >
      <div className="pasos-encabezado">
        <h1 ref={titulo} tabIndex={-1}>
          ¿Dónde está tu botica?
        </h1>
        <p>Marca el punto exacto en el mapa para que los compradores te encuentren.</p>
      </div>

      <form id="form-botica-ubicacion" className="formulario" onSubmit={enviar} noValidate>
        {alerta}
        <div className="pasos-campos">
          <Campo
            id="direccion"
            etiqueta="Dirección"
            error={errores.direccion}
            ayuda={ruc.ayudaDireccion(datos.ruc, datos.direccion)}
          >
            <input
              {...propsError('direccion', errores.direccion)}
              autoComplete="street-address"
              value={datos.direccion}
              onChange={cambiar('direccion')}
              placeholder="Av. Lima Sur 450"
            />
          </Campo>
          <Campo id="distrito" etiqueta="Distrito" error={errores.distrito}>
            <select {...propsError('distrito', errores.distrito)} value={datos.distrito} onChange={cambiar('distrito')}>
              <option value="">Elige un distrito</option>
              {/* Un distrito del mapa que no está en la lista se conserva como opción */}
              {datos.distrito && !DISTRITOS.includes(datos.distrito) && (
                <option value={datos.distrito}>{datos.distrito}</option>
              )}
              {DISTRITOS.map((d) => (
                <option key={d} value={d}>
                  {d}
                </option>
              ))}
            </select>
          </Campo>
        </div>

        <div className="campo">
          <span className="etiqueta-campo">
            Ubicación en el mapa <span className="opcional">(opcional)</span>
          </span>
          <MapaUbicacion valor={ubicacion} onCambio={setUbicacion} onUsarDireccion={usarDireccionDelMapa} recuadro />
        </div>
      </form>
    </RegistroPasos>
  )
}
