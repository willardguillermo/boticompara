// H1 - Registro de la cuenta del dueño de botica (rol DUENO_BOTICA)
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import Campo from '../components/Campo.jsx'
import Marca from '../components/Marca.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { CORREO_VALIDO, propsError, telefonoInvalido } from '../utils/validacion.js'

const VACIO = { nombre: '', correo: '', telefono: '', password: '', confirmar: '' }

function validar(datos) {
  const errores = {}
  if (!datos.nombre.trim()) errores.nombre = 'Ingresa tu nombre completo.'
  if (!CORREO_VALIDO.test(datos.correo.trim())) errores.correo = 'Ingresa un correo válido.'
  if (telefonoInvalido(datos.telefono)) errores.telefono = 'Usa solo números (6 a 15 dígitos).'
  if (datos.password.length < 8) errores.password = 'La contraseña debe tener al menos 8 caracteres.'
  if (datos.confirmar !== datos.password) errores.confirmar = 'Las contraseñas no coinciden.'
  return errores
}

export default function RegistroPage() {
  const { registrarDueno } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()

  const [datos, setDatos] = useState(VACIO)
  const [errores, setErrores] = useState({})
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  const cambiar = (campo) => (e) => setDatos((d) => ({ ...d, [campo]: e.target.value }))

  async function enviar(e) {
    e.preventDefault()
    const nuevos = validar(datos)
    setErrores(nuevos)
    setError('')
    if (Object.keys(nuevos).length) return

    setEnviando(true)
    try {
      await registrarDueno(datos)
      toast('Cuenta creada. Ahora registra los datos de tu botica.')
      navigate('/panel/botica/registro', { replace: true })
    } catch (err) {
      if (err.status === 409) setErrores({ correo: err.mensaje })
      else setError(err.mensaje ?? 'No se pudo crear la cuenta.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main className="acceso acceso-ancho">
      <div className="tarjeta">
        <Marca a="/login" />
        <h1>Crea tu cuenta de botica</h1>
        <p className="texto-suave">
          Paso 1 de 2: tus datos como dueño. Luego registrarás tu botica para que sea revisada.
        </p>

        <form className="formulario" onSubmit={enviar} noValidate>
          <Alerta tipo="error">{error && <p>{error}</p>}</Alerta>

          <Campo id="nombre" etiqueta="Nombre completo" error={errores.nombre}>
            <input
              {...propsError('nombre', errores.nombre)}
              autoComplete="name"
              value={datos.nombre}
              onChange={cambiar('nombre')}
            />
          </Campo>
          <div className="fila-campos">
            <Campo id="correo" etiqueta="Correo" error={errores.correo}>
              <input
                {...propsError('correo', errores.correo)}
                type="email"
                autoComplete="email"
                value={datos.correo}
                onChange={cambiar('correo')}
              />
            </Campo>
            <Campo id="telefono" etiqueta="Teléfono" opcional error={errores.telefono}>
              <input
                {...propsError('telefono', errores.telefono)}
                type="tel"
                inputMode="numeric"
                autoComplete="tel"
                value={datos.telefono}
                onChange={cambiar('telefono')}
              />
            </Campo>
          </div>
          <div className="fila-campos">
            <Campo id="password" etiqueta="Contraseña" error={errores.password} ayuda="Mínimo 8 caracteres.">
              <input
                {...propsError('password', errores.password)}
                type="password"
                autoComplete="new-password"
                value={datos.password}
                onChange={cambiar('password')}
              />
            </Campo>
            <Campo id="confirmar" etiqueta="Repite la contraseña" error={errores.confirmar}>
              <input
                {...propsError('confirmar', errores.confirmar)}
                type="password"
                autoComplete="new-password"
                value={datos.confirmar}
                onChange={cambiar('confirmar')}
              />
            </Campo>
          </div>

          <button type="submit" className="btn btn-primario btn-bloque" disabled={enviando}>
            {enviando ? 'Creando cuenta...' : 'Crear cuenta'}
          </button>
        </form>

        <p className="acceso-pie">
          ¿Ya tienes cuenta? <Link to="/login">Inicia sesión</Link>
        </p>
      </div>
    </main>
  )
}
