// H18 - Inicio de sesión del dueño de botica
// H20 - Si la cuenta es anterior al registro con código, el login responde 403 CORREO_NO_VERIFICADO: se envía un
//       código (/auth/verificacion/codigo), se pide con CodigoOtp (/auth/verificacion/confirmar) y se repite el login
//       con la contraseña que ya escribió (solo en memoria, en el estado de esta pantalla).
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import AvisoSoloMovil from '../components/AvisoSoloMovil.jsx'
import Campo from '../components/Campo.jsx'
import Marca from '../components/Marca.jsx'
import SpotlightCard from '../components/reactbits/SpotlightCard.jsx'
import VerificacionCorreo from '../components/VerificacionCorreo.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import useCuentaRegresiva from '../hooks/useCuentaRegresiva.js'
import { MODO_SIMULADO, api } from '../services/index.js'
import { ESPERA_POR_DEFECTO, segundosDeEspera } from '../utils/cuentaRegresiva.js'
import { propsError } from '../utils/validacion.js'

const CORREO_NO_VERIFICADO = 'CORREO_NO_VERIFICADO'

export default function LoginPage() {
  const { iniciarSesion, aviso } = useAuth()
  const navigate = useNavigate()
  const { state } = useLocation()

  // H17: al volver de restablecer la contraseña llega el aviso y el correo (sin la contraseña)
  const [correo, setCorreo] = useState(state?.correo ?? '')
  const [password, setPassword] = useState('')
  const [errores, setErrores] = useState({})
  const [error, setError] = useState('')
  const [comprador, setComprador] = useState(null)
  const [enviando, setEnviando] = useState(false)
  // H20: el correo no está verificado; { mensaje } del 403 mientras se pide el código
  const [verificacion, setVerificacion] = useState(null)
  const espera = useCuentaRegresiva()

  async function entrar() {
    const resultado = await iniciarSesion(correo, password)
    if (resultado.esComprador) {
      setComprador(resultado.usuario)
      setPassword('')
    } else {
      navigate(state?.desde ?? '/panel', { replace: true })
    }
  }

  /** Pide el código de verificación; un 429 se muestra con su espera, sin salir de la pantalla del código. */
  async function pedirCodigo() {
    try {
      await api.pedirCodigoVerificacion(correo)
      espera.iniciar(ESPERA_POR_DEFECTO)
    } catch (err) {
      if (err.status === 429) espera.iniciar(segundosDeEspera(err.retryAfter))
      throw err
    }
  }

  async function enviar(e) {
    e.preventDefault()
    const nuevos = {}
    if (!correo.trim()) nuevos.correo = 'Ingresa tu correo.'
    if (!password) nuevos.password = 'Ingresa tu contraseña.'
    setErrores(nuevos)
    setError('')
    if (Object.keys(nuevos).length) return

    setEnviando(true)
    try {
      await entrar()
    } catch (err) {
      if (err.status === 403 && err.codigo === CORREO_NO_VERIFICADO) {
        let aviso = ''
        try {
          await pedirCodigo()
        } catch (errCodigo) {
          aviso = errCodigo.mensaje ?? 'No se pudo enviar el código. Usa «Reenviar código».'
        }
        setVerificacion({ mensaje: err.mensaje ?? 'Verifica tu correo para entrar', aviso })
      } else {
        setError(err.mensaje ?? 'No se pudo iniciar sesión.')
      }
    } finally {
      setEnviando(false)
    }
  }

  function volverAlLogin() {
    setVerificacion(null)
    setError('')
  }

  return (
    <main className="acceso">
      {/* Luz suave que sigue al mouse (Spotlight Card); el borde lo pone .tarjeta */}
      <SpotlightCard className="tarjeta" intensity={0.5} spotlightSize={280} style={{ '--spotlight-card-border': 'transparent' }}>
        <Marca a="/login" degradado />
        {comprador ? (
          <AvisoSoloMovil nombre={comprador.nombre} onVolver={() => setComprador(null)} />
        ) : verificacion ? (
          <>
            <h1>{verificacion.mensaje}</h1>
            <p className="texto-suave">
              Tu cuenta es anterior a la verificación por código. Te enviamos uno a{' '}
              <strong className="correo-recuperacion">{correo.trim()}</strong>; vence en 15 minutos.
            </p>
            <Alerta tipo="error">{verificacion.aviso && <p>{verificacion.aviso}</p>}</Alerta>
            <VerificacionCorreo
              espera={espera}
              onVerificar={(codigo) => api.confirmarVerificacion({ correo, codigo })}
              // Correo verificado: se repite el login con la contraseña que ya escribió
              onVerificado={async () => {
                try {
                  await entrar()
                } catch (err) {
                  setVerificacion(null)
                  setError(err.mensaje ?? 'No se pudo iniciar sesión.')
                }
              }}
              onReenviar={() => api.pedirCodigoVerificacion(correo)}
              onCambiarCorreo={volverAlLogin}
              textoCambiar="Volver al inicio de sesión"
              textoBoton="Verificar y entrar"
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
          </>
        ) : (
          <>
            <h1>Panel de botica</h1>
            <p className="texto-suave">Ingresa para gestionar tu botica y tu catálogo.</p>

            <form className="formulario" onSubmit={enviar} noValidate>
              {state?.exito && !error && (
                <Alerta tipo="exito">
                  <p>{state.exito}</p>
                </Alerta>
              )}
              {/* H17 y H19: "Contraseña actualizada" es lo más reciente; no se mezcla con un aviso anterior
                  del contexto (por ejemplo "Tu cuenta fue eliminada" o "Tu sesión expiró") */}
              {aviso && !state?.exito && (
                <Alerta tipo="aviso">
                  <p>{aviso}</p>
                </Alerta>
              )}
              <Alerta tipo="error">{error && <p>{error}</p>}</Alerta>

              <Campo id="correo" etiqueta="Correo" error={errores.correo}>
                <input
                  {...propsError('correo', errores.correo)}
                  type="email"
                  autoComplete="email"
                  value={correo}
                  onChange={(e) => setCorreo(e.target.value)}
                  placeholder="tucorreo@botica.pe"
                />
              </Campo>
              <Campo id="password" etiqueta="Contraseña" error={errores.password}>
                <input
                  {...propsError('password', errores.password)}
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </Campo>
              <p className="enlace-olvido">
                <Link to="/recuperar">¿Olvidaste tu contraseña?</Link>
              </p>

              <button type="submit" className="btn btn-primario btn-bloque" disabled={enviando}>
                {enviando ? 'Ingresando...' : 'Ingresar'}
              </button>
            </form>

            <p className="acceso-pie">
              ¿Tienes una botica y aún no tienes cuenta? <Link to="/registro">Regístrate</Link>
            </p>

            {/* Con DEV directo el build de producción quita este bloque y su contraseña de prueba */}
            {import.meta.env.DEV && MODO_SIMULADO && (
              <div className="datos-prueba">
                <Alerta tipo="info">
                  <p>
                    <strong>Modo simulado.</strong> Prueba con <code>rosa@boticompara.nissidev.dev</code>{' '}
                    (aprobada), <code>lucia@boticompara.nissidev.dev</code> (rechazada) o{' '}
                    <code>nora@ejemplo.test</code> (correo sin verificar), contraseña <code>Boti2026!</code>
                  </p>
                </Alerta>
              </div>
            )}
          </>
        )}
      </SpotlightCard>
    </main>
  )
}
