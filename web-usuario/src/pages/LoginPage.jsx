// H18 - Inicio de sesión del dueño de botica
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import AvisoSoloMovil from '../components/AvisoSoloMovil.jsx'
import Campo from '../components/Campo.jsx'
import Marca from '../components/Marca.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { MODO_SIMULADO } from '../services/index.js'
import { propsError } from '../utils/validacion.js'

export default function LoginPage() {
  const { iniciarSesion } = useAuth()
  const navigate = useNavigate()
  const { state } = useLocation()

  const [correo, setCorreo] = useState('')
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
      <div className="tarjeta">
        <Marca a="/login" />
        {comprador ? (
          <AvisoSoloMovil nombre={comprador.nombre} onVolver={() => setComprador(null)} />
        ) : (
          <>
            <h1>Panel de botica</h1>
            <p className="texto-suave">Ingresa para gestionar tu botica y tu catálogo.</p>

            <form className="formulario" onSubmit={enviar} noValidate>
              {state?.aviso && (
                <Alerta tipo="aviso">
                  <p>{state.aviso}</p>
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

              <button type="submit" className="btn btn-primario btn-bloque" disabled={enviando}>
                {enviando ? 'Ingresando...' : 'Ingresar'}
              </button>
            </form>

            <p className="acceso-pie">
              ¿Tienes una botica y aún no tienes cuenta? <Link to="/registro">Regístrate</Link>
            </p>

            {MODO_SIMULADO && (
              <div className="datos-prueba">
                <Alerta tipo="info">
                  <p>
                    <strong>Modo simulado.</strong> Prueba con <code>rosa@boticasalud.pe</code>{' '}
                    (aprobada) o <code>lucia@boticanueva.pe</code> (pendiente), contraseña{' '}
                    <code>Boti2026!</code>
                  </p>
                </Alerta>
              </div>
            )}
          </>
        )}
      </div>
    </main>
  )
}
