// H18 - Inicio de sesión del dueño de botica
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import AvisoSoloMovil from '../components/AvisoSoloMovil.jsx'
import Campo from '../components/Campo.jsx'
import Marca from '../components/Marca.jsx'
import SpotlightCard from '../components/reactbits/SpotlightCard.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { MODO_SIMULADO } from '../services/index.js'
import { propsError } from '../utils/validacion.js'

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
      const resultado = await iniciarSesion(correo, password)
      if (resultado.esComprador) {
        setComprador(resultado.usuario)
        setPassword('')
      } else {
        navigate(state?.desde ?? '/panel', { replace: true })
      }
    } catch (err) {
      setError(err.mensaje ?? 'No se pudo iniciar sesión.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main className="acceso">
      {/* Luz suave que sigue al mouse (Spotlight Card); el borde lo pone .tarjeta */}
      <SpotlightCard className="tarjeta" intensity={0.5} spotlightSize={280} style={{ '--spotlight-card-border': 'transparent' }}>
        <Marca a="/login" degradado />
        {comprador ? (
          <AvisoSoloMovil nombre={comprador.nombre} onVolver={() => setComprador(null)} />
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
              {aviso && (
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
                    (aprobada) o <code>lucia@boticompara.nissidev.dev</code> (rechazada), contraseña{' '}
                    <code>Boti2026!</code>
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
