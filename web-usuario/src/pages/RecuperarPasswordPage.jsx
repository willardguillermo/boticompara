// H17 - Recuperar la contraseña del dueño de botica (docs/API.md, /auth/recuperar y /auth/restablecer)
// El código y la contraseña solo viven en el estado del componente: nunca en localStorage ni en la URL.
// H20: el código se escribe en la tarjeta de vidrio (VerificacionCodigo), con la contraseña nueva dentro: al
//      cambiarla se ve la misma cruz LED que en el registro (correcto → check; código incorrecto → falla en rojo).
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import Campo from '../components/Campo.jsx'
import Marca from '../components/Marca.jsx'
import SpotlightCard from '../components/reactbits/SpotlightCard.jsx'
import VerificacionCodigo from '../components/VerificacionCodigo.jsx'
import useCuentaRegresiva from '../hooks/useCuentaRegresiva.js'
import { MODO_SIMULADO, api } from '../services/index.js'
import { LARGO_CODIGO, MIN_PASSWORD, validarCorreoRecuperacion, validarRestablecer } from '../utils/recuperacion.js'
import { ESPERA_POR_DEFECTO, segundosDeEspera } from '../utils/cuentaRegresiva.js'
import { propsError } from '../utils/validacion.js'

const AVISO_PASSWORD_ACTUALIZADA = 'Contraseña actualizada, ingresa con tu nueva contraseña.'

export default function RecuperarPasswordPage() {
  const navigate = useNavigate()

  const [paso, setPaso] = useState('correo')
  const [correo, setCorreo] = useState('')
  const [password, setPassword] = useState('')
  const [confirmacion, setConfirmacion] = useState('')
  const [errores, setErrores] = useState({})
  const [error, setError] = useState('')
  // Siempre el mensaje que manda el servidor, para no revelar si el correo existe
  const [mensaje, setMensaje] = useState('')
  const [enviando, setEnviando] = useState(false)
  const espera = useCuentaRegresiva()

  async function pedirCodigo() {
    try {
      const respuesta = await api.recuperarPassword(correo)
      setMensaje(respuesta?.mensaje ?? '')
      espera.iniciar(ESPERA_POR_DEFECTO)
    } catch (err) {
      if (err.status === 429) espera.iniciar(segundosDeEspera(err.retryAfter))
      throw err
    }
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

  function usarOtroCorreo() {
    setPaso('correo')
    setPassword('')
    setConfirmacion('')
    setErrores({})
    setError('')
    setMensaje('')
  }

  /** La contraseña nueva se revisa antes de mandar el código (el código lo revisa la tarjeta). */
  function validarPassword() {
    const { codigo: _sinCodigo, ...nuevos } = validarRestablecer({ codigo: '000000', password, confirmacion })
    setErrores(nuevos)
    return !Object.keys(nuevos).length
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

              <button type="submit" className="btn btn-primario btn-bloque" disabled={enviando || espera.segundos > 0}>
                {enviando ? 'Enviando...' : 'Enviar código'}
              </button>
            </form>
          </>
        ) : (
          <>
            <Alerta tipo="info">{mensaje && <p>{mensaje}</p>}</Alerta>
            <VerificacionCodigo
              Titulo="h1"
              titulo="Crea tu nueva contraseña"
              tituloExito="Contraseña actualizada"
              anuncioExito="Contraseña actualizada"
              subtitulo={
                <>
                  Revisa la bandeja de <strong className="correo-recuperacion">{correo.trim()}</strong> (y la carpeta de
                  spam). El código vence a los 15 minutos.
                </>
              }
              subtituloExito="Ya puedes entrar con tu nueva contraseña."
              // El código se manda junto con la contraseña: no se verifica solo al completar las casillas
              autoEnviar={false}
              validarAntes={validarPassword}
              onVerificar={(codigo) => api.restablecerPassword({ correo, token: codigo, password })}
              onVerificado={() =>
                navigate('/login', { replace: true, state: { exito: AVISO_PASSWORD_ACTUALIZADA, correo: correo.trim() } })
              }
              // El 400 del código (inválido, vencido o usado) hace fallar la cruz; uno de la contraseña va arriba
              esErrorDeCodigo={(err) => err?.status === 400 && /c[oó]digo/i.test(err.mensaje ?? '')}
              onReenviar={pedirCodigo}
              onCambiarCorreo={usarOtroCorreo}
              textoCambiar="Usar otro correo"
              textoBoton="Cambiar contraseña"
              textoExito="Contraseña actualizada"
              espera={espera}
            >
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
            </VerificacionCodigo>

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
