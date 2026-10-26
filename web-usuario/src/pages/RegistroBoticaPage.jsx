// H1 - Registro de la botica del dueño (queda PENDIENTE hasta que el admin la aprueba), por pasos:
//      2 datos, 3 ubicación, 4 licencia, 5 revisión y envío, 6 pantalla final.
//      Los datos y el archivo viven aquí (en el navegador) para no perderlos al ir y volver.
// H2 - Validación del RUC con SUNAT (hook useVerificacionRuc) en el paso 2
// H3 - Dirección, distrito y ubicación en el mapa en el paso 3
// H4 - Licencia de funcionamiento en el paso 4; se sube recién al enviar, después de crear la botica
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import Campo from '../components/Campo.jsx'
import Icono from '../components/Icono.jsx'
import MapaUbicacion from '../components/MapaUbicacion.jsx'
import RegistroPasos from '../components/RegistroPasos.jsx'
import SelectorLicencia from '../components/SelectorLicencia.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { useBotica } from '../context/BoticaContext.jsx'
import { cuerpoBotica, esErrorDeRuc, useVerificacionRuc } from '../hooks/useVerificacionRuc.js'
import { api } from '../services/index.js'
import { DISTRITOS, distritoDeLista } from '../utils/distritos.js'
import { formatoTamano } from '../utils/formato.js'
import { validarRuc } from '../utils/ruc.js'
import { propsError, telefonoInvalido, validarArchivoLicencia } from '../utils/validacion.js'

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
  const { usuario } = useAuth()
  const { recargar } = useBotica()
  const navigate = useNavigate()

  const [paso, setPaso] = useState(2)
  const [datos, setDatos] = useState(DATOS_VACIOS)
  const [ubicacion, setUbicacion] = useState(null)
  const [errores, setErrores] = useState({})
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState('')
  const [archivo, setArchivo] = useState(null)
  const [errorLicencia, setErrorLicencia] = useState('')
  const [confirmado, setConfirmado] = useState(false)
  // Eligió "Lo haré después" en el paso 4 (se olvida si luego elige un archivo)
  const [licenciaDespues, setLicenciaDespues] = useState(false)
  // Paso 6: la licencia quedó pendiente (no se eligió o falló la subida)
  const [licenciaPendiente, setLicenciaPendiente] = useState(false)
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
    // Si vuelve a editar algo, debe confirmar de nuevo en la revisión
    if (numero < 5) setConfirmado(false)
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

  function siguienteUbicacion(e) {
    e.preventDefault()
    const nuevos = validarUbicacion(datos)
    setErrores(nuevos)
    setError('')
    if (!Object.keys(nuevos).length) irA(4)
  }

  // H4: mismas reglas que SeccionLicencia (validarArchivoLicencia); solo se guarda si es válido
  function elegirLicencia(elegido) {
    const mensaje = validarArchivoLicencia(elegido)
    setErrorLicencia(mensaje)
    setArchivo(mensaje ? null : elegido)
    if (!mensaje) setLicenciaDespues(false)
  }

  function siguienteLicencia(e) {
    e.preventDefault()
    if (!archivo) {
      setErrorLicencia(errorLicencia || validarArchivoLicencia(null))
      return
    }
    irA(5)
  }

  function dejarLicenciaParaDespues() {
    setArchivo(null)
    setErrorLicencia('')
    setLicenciaDespues(true)
    irA(5)
  }

  // Lista de pasos: el paso 4 avisa que la licencia quedó para después
  const descripciones = licenciaDespues ? { 4: 'Pendiente: la subirás después' } : undefined

  /**
   * Registra la botica (misma llamada y manejo de errores que antes) y, si hay archivo, sube la licencia.
   * No recarga la botica del contexto: si lo hiciera, SinBotica sacaría al usuario de esta ruta antes
   * de ver el paso 6. Se recarga al pulsar "Ir a mi panel".
   */
  async function enviar(e) {
    e.preventDefault()
    if (!confirmado || enviando) return
    setError('')
    setEnviando('Enviando...')
    try {
      const verificacion = await ruc.verificar(datos.ruc)
      if (!verificacion.valido) {
        setEnviando('')
        setErrores({ ruc: verificacion.mensaje || 'El RUC no es válido.' })
        irA(2)
        return
      }
      if (!datos.razonSocial.trim() && verificacion.fuente !== 'SUNAT') {
        setEnviando('')
        setErrores({ razonSocial: 'Ingresa la razón social que figura en SUNAT.' })
        irA(2)
        return
      }
      await api.registrarBotica(cuerpoBotica(datos, verificacion, ubicacion))
    } catch (err) {
      setEnviando('')
      // Un RUC inválido o ya registrado (409) se corrige en el paso 2
      if (esErrorDeRuc(err)) {
        setErrores({ ruc: err.mensaje })
        irA(2)
      } else {
        setError(err.mensaje ?? 'No se pudo registrar la botica.')
      }
      return
    }

    // La botica ya existe: si la licencia no sube, queda pendiente y se sube desde Mi botica
    let licenciaSubida = false
    if (archivo) {
      try {
        await api.subirLicencia(archivo)
        licenciaSubida = true
      } catch {
        licenciaSubida = false
      }
    }
    setLicenciaPendiente(!licenciaSubida)
    setEnviando('')
    setPaso(6)
  }

  async function irAlPanel() {
    setEnviando('Abriendo tu panel...')
    await recargar()
    navigate('/panel/estado', { replace: true })
  }

  const alerta = <Alerta tipo="error">{error && <p>{error}</p>}</Alerta>

  if (paso === 2) {
    const rucEnVivo = ruc.estadoRuc(datos.ruc, errores.ruc)
    return (
      <RegistroPasos
        paso={2}
        descripciones={descripciones}
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

  if (paso === 3) {
    return (
      <RegistroPasos
        paso={3}
        descripciones={descripciones}
        onIrAPaso={irA}
        pie={
          <>
            <button key="atras-ubicacion" type="button" className="btn btn-secundario" onClick={() => irA(2)}>
              <Icono nombre="flecha-atras" />
              Atrás
            </button>
            <button key="siguiente-ubicacion" type="submit" form="form-botica-ubicacion" className="btn btn-primario">
              Siguiente
              <Icono nombre="flecha" />
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

        <form id="form-botica-ubicacion" className="formulario" onSubmit={siguienteUbicacion} noValidate>
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

  if (paso === 4) {
    return (
      <RegistroPasos
        paso={4}
        descripciones={descripciones}
        onIrAPaso={irA}
        pie={
          <>
            <button key="atras-licencia" type="button" className="btn btn-secundario" onClick={() => irA(3)}>
              <Icono nombre="flecha-atras" />
              Atrás
            </button>
            <div key="acciones-licencia" className="pasos-pie-grupo">
              <button type="button" className="btn btn-secundario" onClick={dejarLicenciaParaDespues}>
                Lo haré después
              </button>
              <button type="submit" form="form-licencia" className="btn btn-primario">
                Subir y continuar
                <Icono nombre="flecha" />
              </button>
            </div>
          </>
        }
      >
        <div className="pasos-encabezado">
          <h1 ref={titulo} tabIndex={-1}>
            Licencia de funcionamiento
          </h1>
          <p>Sube una foto o PDF de tu licencia municipal. La revisaremos junto con tu solicitud.</p>
        </div>

        <form id="form-licencia" className="formulario" onSubmit={siguienteLicencia} noValidate>
          <SelectorLicencia archivo={archivo} error={errorLicencia} onElegir={elegirLicencia} />
          <Alerta tipo="info">
            <p>
              Si eliges «Lo haré después», podrás subirla desde Mi botica. Tu botica no será aprobada sin la
              licencia.
            </p>
          </Alerta>
        </form>
      </RegistroPasos>
    )
  }

  if (paso === 5) {
    return (
      <RegistroPasos
        paso={5}
        descripciones={descripciones}
        onIrAPaso={enviando ? undefined : irA}
        pie={
          <>
            <button
              key="atras-revision"
              type="button"
              className="btn btn-secundario"
              onClick={() => irA(4)}
              disabled={Boolean(enviando)}
            >
              <Icono nombre="flecha-atras" />
              Atrás
            </button>
            <button
              key="enviar"
              type="submit"
              form="form-revision"
              className="btn btn-primario"
              disabled={!confirmado || Boolean(enviando)}
            >
              {enviando || 'Enviar solicitud'}
            </button>
          </>
        }
      >
        <div className="pasos-encabezado">
          <h1 ref={titulo} tabIndex={-1}>
            Revisa tu solicitud
          </h1>
          <p>Confirma que todo esté bien antes de enviarla.</p>
        </div>

        <form id="form-revision" className="formulario" onSubmit={enviar} noValidate>
          {alerta}
          <div className="resumen-registro">
            <section className="resumen-tarjeta">
              <div>
                <h2>Cuenta</h2>
                <p>Cuenta ya creada</p>
                <p className="texto-suave">{usuario?.correo}</p>
              </div>
            </section>

            <section className="resumen-tarjeta">
              <div>
                <h2>Botica</h2>
                <p>{datos.nombreComercial}</p>
                <p className="texto-suave">
                  RUC {datos.ruc} · {datos.razonSocial}
                </p>
              </div>
              <button type="button" className="btn btn-secundario btn-icono" onClick={() => irA(2)} disabled={Boolean(enviando)}>
                <Icono nombre="editar" /> Editar
              </button>
            </section>

            <section className="resumen-tarjeta">
              <div>
                <h2>Ubicación</h2>
                <p>
                  {datos.direccion}, {datos.distrito}
                </p>
                <p className="texto-suave">
                  {ubicacion
                    ? `${ubicacion.latitud.toFixed(6)}, ${ubicacion.longitud.toFixed(6)}`
                    : 'Sin punto en el mapa'}
                </p>
              </div>
              <button type="button" className="btn btn-secundario btn-icono" onClick={() => irA(3)} disabled={Boolean(enviando)}>
                <Icono nombre="editar" /> Editar
              </button>
            </section>

            <section className="resumen-tarjeta">
              <div>
                <h2>Licencia</h2>
                {archivo ? (
                  <>
                    <p>{archivo.name}</p>
                    <p className="texto-suave">{formatoTamano(archivo.size)}</p>
                  </>
                ) : (
                  <>
                    <p className="texto-pendiente">Pendiente</p>
                    <p className="texto-suave">La subirás después desde Mi botica</p>
                  </>
                )}
              </div>
              <button type="button" className="btn btn-secundario btn-icono" onClick={() => irA(4)} disabled={Boolean(enviando)}>
                {archivo ? (
                  <>
                    <Icono nombre="editar" /> Editar
                  </>
                ) : (
                  <>
                    <Icono nombre="subir" /> Subir ahora
                  </>
                )}
              </button>
            </section>
          </div>

          <label className="casilla casilla-confirmar">
            <input
              type="checkbox"
              checked={confirmado}
              onChange={(e) => setConfirmado(e.target.checked)}
              disabled={Boolean(enviando)}
            />
            Confirmo que los datos son correctos y que estoy autorizado para registrar esta botica.
          </label>
        </form>
      </RegistroPasos>
    )
  }

  // Paso 6: pantalla final. La botica ya existe, pero el contexto se recarga recién en "Ir a mi panel"
  return (
    <RegistroPasos
      paso={6}
      descripciones={descripciones}
      pie={
        <button key="panel" type="button" className="btn btn-primario" onClick={irAlPanel} disabled={Boolean(enviando)}>
          {enviando || 'Ir a mi panel'}
          {!enviando && <Icono nombre="flecha" />}
        </button>
      }
    >
      <div className="registro-final">
        <span className="final-icono">
          <Icono nombre="check" />
        </span>
        <h1 ref={titulo} tabIndex={-1}>
          ¡Solicitud enviada!
        </h1>
        <span className="estado estado-PENDIENTE">
          <Icono nombre="reloj" /> En revisión
        </span>
        <p>
          Revisaremos los datos de tu botica y te avisaremos cuando esté aprobada. Mientras tanto puedes ver el
          estado en tu panel.
        </p>
      </div>
      {licenciaPendiente && (
        <Alerta tipo="aviso">
          <p>Falta tu licencia de funcionamiento. Súbela desde Mi botica para que podamos aprobar tu botica.</p>
        </Alerta>
      )}
    </RegistroPasos>
  )
}
