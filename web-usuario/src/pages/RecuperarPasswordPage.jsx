// H17 - Recuperar la contraseña del dueño de botica (docs/API.md, /auth/recuperar y /auth/restablecer)
// El código y la contraseña solo viven en el estado del componente: nunca en localStorage ni en la URL.
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import Campo from '../components/Campo.jsx'
import Marca from '../components/Marca.jsx'
import SpotlightCard from '../components/reactbits/SpotlightCard.jsx'
import { MODO_SIMULADO, api } from '../services/index.js'
import { LARGO_CODIGO, MIN_PASSWORD, limpiarCodigo, validarCorreoRecuperacion, validarRestablecer } from '../utils/recuperacion.js'
import { propsError } from '../utils/validacion.js'

const AVISO_PASSWORD_ACTUALIZADA = 'Contraseña actualizada, ingresa con tu nueva contraseña.'

export default function RecuperarPasswordPage() {
  const navigate = useNavigate()

  const [paso, setPaso] = useState('correo')
  const [correo, setCorreo] = useState('')
  const [codigo, setCodigo] = useState('')
  const [password, setPassword] = useState('')
  const [confirmacion, setConfirmacion] = useState('')
  const [errores, setErrores] = useState({})
  const [error, setError] = useState('')
  // Siempre el mensaje que manda el servidor, para no revelar si el correo existe
  const [mensaje, setMensaje] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [reenviando, setReenviando] = useState(false)

  async function pedirCodigo() {
    const respuesta = await api.recuperarPassword(correo)
    setMensaje(respuesta?.mensaje ?? '')
  }

  async function enviarCorreo(e) {
    e.preventDefault()
    const errorCorreo = validarCorreoRecuperacion(correo)
    setErrores(errorCorreo ? { correo: errorCorreo } : {})
    setError('')
    if (errorCorreo) return

    setEnviando(true)
    try {
      await pedirCodigo()
      setPaso('codigo')
    } catch (err) {
      setError(err.mensaje ?? 'No se pudo enviar el código.')
    } finally {
      setEnviando(false)
    }
  }

  async function reenviar() {
    setError('')
    setMensaje('')
    setReenviando(true)
    try {
      await pedirCodigo()
    } catch (err) {
      setError(err.mensaje ?? 'No se pudo reenviar el código.')
    } finally {
      setReenviando(false)
    }
  }

  function usarOtroCorreo() {
    setPaso('correo')
    setCodigo('')
    setPassword('')
    setConfirmacion('')
    setErrores({})
    setError('')
    setMensaje('')
  }

  async function restablecer(e) {
    e.preventDefault()
    const nuevos = validarRestablecer({ codigo, password, confirmacion })
    setErrores(nuevos)
    setError('')
    if (Object.keys(nuevos).length) return

    setEnviando(true)
    try {
      await api.restablecerPassword({ correo, token: codigo, password })
      navigate('/login', { replace: true, state: { exito: AVISO_PASSWORD_ACTUALIZADA, correo: correo.trim() } })
    } catch (err) {
      setError(err.mensaje ?? 'No se pudo actualizar la contraseña.')
      setEnviando(false)
    }
  }

  return (
    <main className="acceso">
      <SpotlightCard className="tarjeta" intensity={0.5} spotlightSize={280} style={{ '--spotlight-card-border': 'transparent' }}>
        <Marca a="/login" degradado />

        {paso === 'correo' ? (
          <>
            <h1>Recupera tu contraseña</h1>
            <p className="texto-suave">Escribe el correo de tu cuenta y te enviaremos un código de {LARGO_CODIGO} dígitos.</p>

            <form className="formulario" onSubmit={enviarCorreo} noValidate>
              <Alerta tipo="error">{error && <p>{error}</p>}</Alerta>

              <Campo id="correo" etiqueta="Correo" error={errores.correo}>
                <input
                  {...propsError('correo', errores.correo)}
                  type="email"
                  autoComplete="email"
                  value={correo}
                  onChange={(e) => setCorreo(e.target.value)}
                  placeholder="tucorreo@botica.pe"
                  autoFocus
                />
              </Campo>

              <button type="submit" className="btn btn-primario btn-bloque" disabled={enviando}>
                {enviando ? 'Enviando...' : 'Enviar código'}
              </button>
            </form>
          </>
        ) : (
          <>
            <h1>Crea tu nueva contraseña</h1>
            <p className="texto-suave">
              Revisa la bandeja de <strong className="correo-recuperacion">{correo.trim()}</strong> (y la carpeta de spam).
              El código vence a los 15 minutos.
            </p>

            <form className="formulario" onSubmit={restablecer} noValidate>
              <Alerta tipo="info">{mensaje && <p>{mensaje}</p>}</Alerta>
              <Alerta tipo="error">{error && <p>{error}</p>}</Alerta>

              <Campo id="codigo" etiqueta="Código de verificación" error={errores.codigo}>
                <input
                  {...propsError('codigo', errores.codigo)}
                  className="input-codigo"
                  type="text"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  pattern="[0-9]*"
                  maxLength={LARGO_CODIGO}
                  value={codigo}
                  onChange={(e) => setCodigo(limpiarCodigo(e.target.value))}
                  placeholder="000000"
                  autoFocus
                />
              </Campo>
              <Campo
                id="password"
                etiqueta="Nueva contraseña"
                error={errores.password}
                ayuda={`Mínimo ${MIN_PASSWORD} caracteres.`}
              >
                <input
                  {...propsError('password', errores.password)}
                  type="password"
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </Campo>
              <Campo id="confirmacion" etiqueta="Repite la nueva contraseña" error={errores.confirmacion}>
                <input
                  {...propsError('confirmacion', errores.confirmacion)}
                  type="password"
                  autoComplete="new-password"
                  value={confirmacion}
                  onChange={(e) => setConfirmacion(e.target.value)}
                />
              </Campo>

              <button type="submit" className="btn btn-primario btn-bloque" disabled={enviando}>
                {enviando ? 'Guardando...' : 'Cambiar contraseña'}
              </button>
            </form>

            <div className="acciones-recuperacion">
              <button type="button" className="btn-enlace" onClick={reenviar} disabled={reenviando || enviando}>
                {reenviando ? 'Reenviando...' : 'Reenviar código'}
              </button>
              <button type="button" className="btn-enlace" onClick={usarOtroCorreo} disabled={enviando}>
                Usar otro correo
              </button>
            </div>

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
        )}

        <p className="acceso-pie">
          ¿Ya la recordaste? <Link to="/login">Volver a iniciar sesión</Link>
        </p>
      </SpotlightCard>
    </main>
  )
}
