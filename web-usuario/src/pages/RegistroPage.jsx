// H1, H15, H20 - Registro del dueño por pasos con el correo verificado (docs/API.md, sección 1):
//   1 correo → POST /auth/registro/codigo
//   2 código → POST /auth/registro/verificar (da el tokenVerificacion)
//   3 cuenta: nombre, contraseña y teléfono
//   4 botica: RUC (POST /auth/registro/validar-ruc con el token), razón social de SUNAT, dirección, distrito y mapa
//   5 licencia (opcional) → POST /auth/registro con todo; con el 201 queda la sesión y, si eligió un archivo,
//     se sube con POST /boticas/mia/licencia.
// La contraseña, el código y el token viven solo en el estado de esta pantalla: nunca en localStorage ni en la URL.
import { useEffect, useReducer, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import Campo from '../components/Campo.jsx'
import Icono from '../components/Icono.jsx'
import MapaUbicacion from '../components/MapaUbicacion.jsx'
import Marca from '../components/Marca.jsx'
import RegistroPasos from '../components/RegistroPasos.jsx'
import SelectorLicencia from '../components/SelectorLicencia.jsx'
import VerificacionCorreo from '../components/VerificacionCorreo.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import useCuentaRegresiva from '../hooks/useCuentaRegresiva.js'
import { cuerpoBotica, esErrorDeRuc, useVerificacionRuc } from '../hooks/useVerificacionRuc.js'
import { MODO_SIMULADO, api } from '../services/index.js'
import { esErrorDeTitular } from '../utils/cuenta.js'
import { ESPERA_POR_DEFECTO, formatoEspera, segundosDeEspera } from '../utils/cuentaRegresiva.js'
import { DISTRITOS, distritoDeLista } from '../utils/distritos.js'
import { conPuntoFinal } from '../utils/formato.js'
import {
  cuerpoRegistro,
  ESTADO_INICIAL,
  esTokenVencido,
  numeroDePaso,
  PASOS_REGISTRO,
  pasoTrasError,
  puedeVolverA,
  registroReducer,
} from '../utils/registro.js'
import { validarRuc } from '../utils/ruc.js'
import { CORREO_VALIDO, propsError, telefonoInvalido, validarArchivoLicencia } from '../utils/validacion.js'

const CUENTA_VACIA = { nombre: '', telefono: '', password: '', confirmar: '' }
const BOTICA_VACIA = { nombreComercial: '', ruc: '', razonSocial: '', telefono: '', direccion: '', distrito: '' }
const ID_PASO = Object.fromEntries(PASOS_REGISTRO.map((p, i) => [i + 1, p.id]))

function validarCuenta(cuenta) {
  const errores = {}
  if (!cuenta.nombre.trim()) errores.nombre = 'Ingresa tu nombre completo.'
  else if (cuenta.nombre.trim().length > 100) errores.nombre = 'El nombre admite máximo 100 caracteres.'
  if (telefonoInvalido(cuenta.telefono)) errores.telefono = 'Usa solo números (6 a 15 dígitos).'
  if (cuenta.password.length < 8) errores.password = 'La contraseña debe tener al menos 8 caracteres.'
  else if (cuenta.password.length > 72) errores.password = 'La contraseña admite máximo 72 caracteres.'
  if (cuenta.confirmar !== cuenta.password) errores.confirmar = 'Las contraseñas no coinciden.'
  return errores
}

/** La razón social se revisa después de consultar SUNAT, que puede completarla. */
function validarBotica(datos) {
  const errores = {}
  if (!datos.nombreComercial.trim()) errores.nombreComercial = 'Ingresa el nombre comercial.'
  const ruc = validarRuc(datos.ruc)
  if (!ruc.valido) errores.ruc = ruc.mensaje
  if (!datos.razonSocial.trim()) errores.razonSocial = 'Ingresa la razón social que figura en SUNAT.'
  if (telefonoInvalido(datos.telefono)) errores.telefono = 'Usa solo números (6 a 15 dígitos).'
  if (!datos.direccion.trim()) errores.direccion = 'Ingresa la dirección de la botica.'
  if (!datos.distrito.trim()) errores.distrito = 'Elige el distrito.'
  return errores
}

export default function RegistroPage() {
  const { registrarDueno, entrar } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()

  const [estado, despachar] = useReducer(registroReducer, ESTADO_INICIAL)
  const [correo, setCorreo] = useState('')
  const [cuenta, setCuenta] = useState(CUENTA_VACIA)
  const [datos, setDatos] = useState(BOTICA_VACIA)
  const [ubicacion, setUbicacion] = useState(null)
  const [archivo, setArchivo] = useState(null)
  const [errorLicencia, setErrorLicencia] = useState('')
  const [errores, setErrores] = useState({})
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState('')
  // La cuenta ya se creó pero la licencia no subió: { usuario, mensaje }
  const [creada, setCreada] = useState(null)
  // Reenviar el código (paso 2) y pedirlo tras un 429 (paso 1): el límite es por correo, por eso van aparte
  const espera = useCuentaRegresiva()
  const esperaPedir = useCuentaRegresiva()
  const titulo = useRef(null)
  const primerPaso = useRef(true)

  // H2 durante el registro: validar-ruc con el token, porque la cuenta todavía no existe
  const ruc = useVerificacionRuc({
    setDatos,
    setErrores,
    consultar: (numero) => api.validarRucRegistro({ tokenVerificacion: estado.token, ruc: numero }),
  })

  // Al cambiar de paso, el foco va al título del nuevo paso (no al entrar a la pantalla)
  useEffect(() => {
    if (primerPaso.current) {
      primerPaso.current = false
      return
    }
    window.scrollTo({ top: 0 })
    titulo.current?.focus()
  }, [estado.paso])

  const paso = estado.paso
  const numero = numeroDePaso(paso)

  function irA(destino) {
    setError('')
    setErrores({})
    despachar({ tipo: 'volver', paso: destino })
  }

  /** El token venció (o ya no sirve): vuelve al correo, que queda escrito, con el aviso del servidor. */
  function volverAlCorreo(mensaje) {
    setEnviando('')
    setErrores({})
    despachar({ tipo: 'error', paso: 'correo', aviso: mensaje })
  }

  // ---------- Paso 1: correo ----------
  async function pedirCodigo(e) {
    e.preventDefault()
    const limpio = correo.trim().toLowerCase()
    setError('')
    if (!CORREO_VALIDO.test(limpio)) {
      setErrores({ correo: 'Ingresa un correo válido.' })
      return
    }
    setErrores({})
    if (esperaPedir.segundos > 0) return
    setEnviando('Enviando código...')
    try {
      await api.pedirCodigoRegistro(limpio)
      espera.iniciar(ESPERA_POR_DEFECTO)
      despachar({ tipo: 'codigoEnviado', correo: limpio })
    } catch (err) {
      if (err.status === 409) setErrores({ correo: err.mensaje })
      else setError(err.mensaje ?? 'No se pudo enviar el código.')
      if (err.status === 429) esperaPedir.iniciar(segundosDeEspera(err.retryAfter))
    } finally {
      setEnviando('')
    }
  }

  // ---------- Paso 3: cuenta ----------
  const cambiarCuenta = (campo) => (e) => {
    setCuenta((c) => ({ ...c, [campo]: e.target.value }))
    setErrores((er) => ({ ...er, [campo]: undefined }))
  }

  function siguienteCuenta(e) {
    e.preventDefault()
    const nuevos = validarCuenta(cuenta)
    setErrores(nuevos)
    setError('')
    if (!Object.keys(nuevos).length) despachar({ tipo: 'cuentaLista' })
  }

  // ---------- Paso 4: botica ----------
  const cambiarBotica = (campo) => (e) => {
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

  async function siguienteBotica(e) {
    e.preventDefault()
    const nuevos = validarBotica(datos)
    setErrores(nuevos)
    setError('')
    const { razonSocial: errorRazonSocial, ...otros } = nuevos
    if (Object.keys(otros).length) return

    setEnviando('Verificando RUC...')
    try {
      // Reutiliza la consulta hecha al escribir el RUC (o la que sigue en curso)
      const verificacion = await ruc.verificar(datos.ruc)
      if (!verificacion.valido) {
        setErrores({ ruc: verificacion.mensaje || 'El RUC no es válido.' })
        return
      }
      // fuente LOCAL (SUNAT no respondió): sigue con la razón social escrita
      if (errorRazonSocial && verificacion.fuente !== 'SUNAT') return
      despachar({ tipo: 'boticaLista' })
    } catch (err) {
      if (esTokenVencido(err)) volverAlCorreo(err.mensaje)
      else if (esErrorDeRuc(err)) setErrores({ ruc: err.mensaje })
      else if (err.status === 429 && !errorRazonSocial) {
        // Límite de consultas a SUNAT: el backend validará el RUC al crear la cuenta
        despachar({ tipo: 'boticaLista' })
      } else setError(err.mensaje ?? 'No se pudo verificar el RUC.')
    } finally {
      setEnviando('')
    }
  }

  // ---------- Paso 5: licencia y envío ----------
  function elegirLicencia(elegido) {
    const mensaje = validarArchivoLicencia(elegido)
    setErrorLicencia(mensaje)
    setArchivo(mensaje ? null : elegido)
  }

  function irAlPanel(usuario, ruta = '/panel') {
    entrar(usuario)
    navigate(ruta, { replace: true })
  }

  async function crearCuenta(conLicencia) {
    if (enviando) return
    if (conLicencia && !archivo) {
      setErrorLicencia(errorLicencia || validarArchivoLicencia(null))
      return
    }
    setError('')
    setEnviando('Creando tu cuenta...')
    const verificacion = { fuente: ruc.razonDeSunat ? 'SUNAT' : 'LOCAL', razonSocial: datos.razonSocial }
    let usuario
    try {
      usuario = await registrarDueno(
        cuerpoRegistro({
          token: estado.token,
          correo: estado.correo,
          cuenta,
          botica: cuerpoBotica(datos, verificacion, ubicacion),
        }),
      )
    } catch (err) {
      setEnviando('')
      const destino = pasoTrasError(err)
      if (destino === 'correo' && err.status === 409) {
        despachar({ tipo: 'error', paso: 'correo', aviso: '' })
        setErrores({ correo: err.mensaje })
      } else if (destino === 'correo') {
        volverAlCorreo(err.mensaje)
      } else if (destino === 'botica') {
        // Se queda el token y todo lo escrito: solo hay que corregir la botica (o el nombre, en A7)
        const delRuc = esErrorDeTitular(err.mensaje) || /ruc/i.test(err.mensaje)
        setErrores(delRuc ? { ruc: err.mensaje } : {})
        despachar({ tipo: 'error', paso: 'botica', aviso: delRuc ? '' : err.mensaje })
      } else {
        setError(err.mensaje ?? 'No se pudo crear la cuenta.')
      }
      return
    }

    if (conLicencia) {
      setEnviando('Subiendo tu licencia...')
      try {
        await api.subirLicencia(archivo)
      } catch (err) {
        // La cuenta ya existe: no se vuelve a enviar el registro; la licencia se sube desde Mi botica
        setEnviando('')
        setCreada({ usuario, mensaje: err.mensaje ?? 'No se pudo subir la licencia.' })
        return
      }
    }
    toast('Cuenta creada. Revisaremos tu botica y te avisaremos cuando esté aprobada.')
    irAlPanel(usuario)
  }

  // ---------- Pantallas ----------
  const alerta = (
    <Alerta tipo="error">
      {(error || estado.aviso) && <p>{error || estado.aviso}</p>}
    </Alerta>
  )
  const encabezado = (texto, bajada) => (
    <div className="pasos-encabezado">
      <h1 ref={titulo} tabIndex={-1}>
        {texto}
      </h1>
      <p>{bajada}</p>
    </div>
  )
  const marco = (pie, contenido) => (
    <main className="registro-pagina">
      <Marca a="/" degradado />
      <RegistroPasos
        paso={numero}
        pasos={PASOS_REGISTRO}
        pie={pie}
        onIrAPaso={enviando || creada ? undefined : (n) => irA(ID_PASO[n])}
        esVisitable={(n) => puedeVolverA(estado, ID_PASO[n])}
      >
        {contenido}
      </RegistroPasos>
    </main>
  )

  if (paso === 'correo') {
    return marco(
      <>
        <p key="pie-correo">
          ¿Ya tienes cuenta? <Link to="/login">Inicia sesión</Link>
        </p>
        <button
          key="pedir"
          type="submit"
          form="form-correo"
          className="btn btn-primario"
          disabled={Boolean(enviando) || esperaPedir.segundos > 0}
        >
          {enviando ||
            (esperaPedir.segundos > 0 ? `Espera ${formatoEspera(esperaPedir.segundos)}` : 'Enviar código')}
          {!enviando && !esperaPedir.segundos && <Icono nombre="flecha" />}
        </button>
      </>,
      <>
        {encabezado('Crea tu cuenta', 'Empieza con tu correo: te enviaremos un código de 6 dígitos para verificarlo.')}
        <form id="form-correo" className="formulario" onSubmit={pedirCodigo} noValidate>
          {alerta}
          <Campo id="correo" etiqueta="Correo" error={errores.correo}>
            <input
              {...propsError('correo', errores.correo)}
              type="email"
              autoComplete="email"
              value={correo}
              onChange={(e) => {
                setCorreo(e.target.value)
                setErrores({})
                // Otro correo puede tener su propio límite: que lo diga el servidor
                if (esperaPedir.segundos > 0) esperaPedir.iniciar(0)
              }}
              placeholder="tu@correo.com"
            />
          </Campo>
        </form>
        {/* H21: el registro solo pide datos de contacto y de acceso */}
        <p className="aviso-privacidad">
          <Icono nombre="candado" /> BotiCompara no te pide datos de salud ni historial clínico.
        </p>
      </>,
    )
  }

  if (paso === 'codigo') {
    return marco(
      null,
      <>
        {encabezado(
          'Revisa tu correo',
          <>
            Escribe el código que enviamos a <strong className="correo-recuperacion">{estado.correo}</strong>. Vence en
            15 minutos; si no lo ves, revisa la carpeta de spam.
          </>,
        )}
        <VerificacionCorreo
          espera={espera}
          onVerificar={async (codigo) => {
            const { tokenVerificacion } = await api.verificarCodigoRegistro({ correo: estado.correo, codigo })
            return tokenVerificacion
          }}
          onVerificado={(token) => despachar({ tipo: 'verificado', token })}
          onReenviar={() => api.pedirCodigoRegistro(estado.correo)}
          onCambiarCorreo={() => {
            setError('')
            despachar({ tipo: 'cambiarCorreo' })
          }}
        />
        {import.meta.env.DEV && MODO_SIMULADO && (
          <div className="datos-prueba">
            <Alerta tipo="info">
              <p>
                <strong>Modo simulado.</strong> El código válido es <code>123456</code>.
              </p>
            </Alerta>
          </div>
        )}
      </>,
    )
  }

  if (paso === 'cuenta') {
    return marco(
      <button key="siguiente-cuenta" type="submit" form="form-cuenta" className="btn btn-primario">
        Siguiente
        <Icono nombre="flecha" />
      </button>,
      <>
        {encabezado('Tu cuenta', 'Con esta cuenta administrarás tu botica y tu catálogo.')}
        <form id="form-cuenta" className="formulario" onSubmit={siguienteCuenta} noValidate>
          {alerta}
          <p className="correo-verificado">
            <Icono nombre="check" /> {estado.correo} <span className="texto-suave">· verificado</span>
          </p>
          <Campo id="nombre" etiqueta="Nombre completo" error={errores.nombre}>
            <input
              {...propsError('nombre', errores.nombre)}
              autoComplete="name"
              value={cuenta.nombre}
              onChange={cambiarCuenta('nombre')}
              placeholder="Como figura en tu DNI"
            />
          </Campo>
          <div className="pasos-campos">
            <Campo id="password" etiqueta="Contraseña" error={errores.password} ayuda="Mínimo 8 caracteres.">
              <input
                {...propsError('password', errores.password)}
                type="password"
                autoComplete="new-password"
                value={cuenta.password}
                onChange={cambiarCuenta('password')}
              />
            </Campo>
            <Campo id="confirmar" etiqueta="Repite la contraseña" error={errores.confirmar}>
              <input
                {...propsError('confirmar', errores.confirmar)}
                type="password"
                autoComplete="new-password"
                value={cuenta.confirmar}
                onChange={cambiarCuenta('confirmar')}
              />
            </Campo>
            <Campo id="telefono" etiqueta="Teléfono" opcional error={errores.telefono}>
              <input
                {...propsError('telefono', errores.telefono)}
                type="tel"
                inputMode="numeric"
                autoComplete="tel"
                value={cuenta.telefono}
                onChange={cambiarCuenta('telefono')}
                placeholder="987654321"
              />
            </Campo>
          </div>
        </form>
      </>,
    )
  }

  if (paso === 'botica') {
    const rucEnVivo = ruc.estadoRuc(datos.ruc, errores.ruc)
    return marco(
      <>
        <button key="atras-botica" type="button" className="btn btn-secundario" onClick={() => irA('cuenta')} disabled={Boolean(enviando)}>
          <Icono nombre="flecha-atras" />
          Atrás
        </button>
        <button key="siguiente-botica" type="submit" form="form-botica" className="btn btn-primario" disabled={Boolean(enviando)}>
          {enviando || 'Siguiente'}
          {!enviando && <Icono nombre="flecha" />}
        </button>
      </>,
      <>
        {encabezado('Tu botica', 'Así te verán los compradores. Marca en el mapa dónde está tu local.')}
        <form id="form-botica" className="formulario" onSubmit={siguienteBotica} noValidate>
          {alerta}
          <div className="pasos-campos">
            <Campo id="nombreComercial" etiqueta="Nombre comercial" error={errores.nombreComercial}>
              <input
                {...propsError('nombreComercial', errores.nombreComercial)}
                value={datos.nombreComercial}
                onChange={cambiarBotica('nombreComercial')}
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
                onChange={cambiarBotica('ruc')}
                placeholder="20601234565"
              />
            </Campo>
            {esErrorDeTitular(rucEnVivo.error) && (
              <p className="campo-ancho ayuda-titular">
                El nombre de tu cuenta debe coincidir con el del titular en SUNAT.{' '}
                <button type="button" className="btn-enlace" onClick={() => irA('cuenta')}>
                  Corregir mi nombre
                </button>
              </p>
            )}
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
                  onChange={cambiarBotica('razonSocial')}
                  placeholder="Salud Total Farma S.A.C."
                />
              </Campo>
            </div>
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
                onChange={cambiarBotica('direccion')}
                placeholder="Av. Lima Sur 450"
              />
            </Campo>
            <Campo id="distrito" etiqueta="Distrito" error={errores.distrito}>
              <select {...propsError('distrito', errores.distrito)} value={datos.distrito} onChange={cambiarBotica('distrito')}>
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
            <Campo id="telefonoBotica" etiqueta="Teléfono de la botica" opcional error={errores.telefono}>
              <input
                {...propsError('telefonoBotica', errores.telefono)}
                type="tel"
                inputMode="numeric"
                value={datos.telefono}
                onChange={cambiarBotica('telefono')}
                placeholder="014567890"
              />
            </Campo>
          </div>

          <div className="campo">
            <span className="etiqueta-campo">
              Ubicación en el mapa <span className="opcional">(opcional)</span>
            </span>
            <MapaUbicacion valor={ubicacion} onCambio={setUbicacion} onUsarDireccion={usarDireccionDelMapa} recuadro />
          </div>
        </form>
      </>,
    )
  }

  // Paso 5: licencia (opcional) y envío
  if (creada) {
    return marco(
      <button key="ir-mi-botica" type="button" className="btn btn-primario" onClick={() => irAlPanel(creada.usuario, '/panel/estado')}>
        Ir a Mi botica
        <Icono nombre="flecha" />
      </button>,
      <>
        {encabezado('Tu cuenta está creada', 'Tu botica quedó registrada y en revisión.')}
        <Alerta tipo="aviso">
          <p>
            No pudimos subir tu licencia: {conPuntoFinal(creada.mensaje)} Tu cuenta y tu botica ya existen, así que no tienes que
            registrarte de nuevo. Vuelve a intentarlo desde <strong>Mi botica</strong>.
          </p>
        </Alerta>
      </>,
    )
  }

  return marco(
    <>
      <button key="atras-licencia" type="button" className="btn btn-secundario" onClick={() => irA('botica')} disabled={Boolean(enviando)}>
        <Icono nombre="flecha-atras" />
        Atrás
      </button>
      <div key="acciones-licencia" className="pasos-pie-grupo">
        <button type="button" className="btn btn-secundario" onClick={() => crearCuenta(false)} disabled={Boolean(enviando)}>
          Subir después
        </button>
        <button type="submit" form="form-licencia" className="btn btn-primario" disabled={Boolean(enviando)}>
          {enviando || 'Crear cuenta'}
          {!enviando && <Icono nombre="flecha" />}
        </button>
      </div>
    </>,
    <>
      {encabezado(
        'Licencia de funcionamiento',
        'Opcional ahora: sube una foto o PDF de tu licencia municipal, o hazlo después desde Mi botica.',
      )}
      <form
        id="form-licencia"
        className="formulario"
        onSubmit={(e) => {
          e.preventDefault()
          crearCuenta(true)
        }}
        noValidate
      >
        {alerta}
        <SelectorLicencia archivo={archivo} error={errorLicencia} onElegir={elegirLicencia} />
        <Alerta tipo="info">
          <p>
            Al crear la cuenta enviaremos la solicitud de <strong>{datos.nombreComercial.trim()}</strong> a revisión.
            Tu botica no será aprobada sin la licencia.
          </p>
        </Alerta>
      </form>
    </>,
  )
}
