import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import { api, EVENTO_SESION_EXPIRADA } from '../services/index.js'
import { borrarSesion, guardarSesion, obtenerToken, obtenerUsuario } from '../services/sesion.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const navigate = useNavigate()
  const [usuario, setUsuario] = useState(() => (obtenerToken() ? obtenerUsuario() : null))

  // Cualquier 401 de la API (token vencido o inválido) cierra la sesión
  useEffect(() => {
    const alExpirar = () => {
      setUsuario(null)
      navigate('/login', {
        replace: true,
        state: { aviso: 'Tu sesión expiró. Inicia sesión nuevamente.' },
      })
    }
    window.addEventListener(EVENTO_SESION_EXPIRADA, alExpirar)
    return () => window.removeEventListener(EVENTO_SESION_EXPIRADA, alExpirar)
  }, [navigate])

  /**
   * H18. Un COMPRADOR no puede usar esta web: no se guarda su sesión
   * y la pantalla le indica que use la app móvil.
   */
  const iniciarSesion = useCallback(async (correo, password) => {
    const { token, usuario: datos } = await api.login({ correo: correo.trim(), password })
    if (datos.rol !== 'DUENO_BOTICA') return { esComprador: true, usuario: datos }
    guardarSesion(token, datos)
    setUsuario(datos)
    return { esComprador: false, usuario: datos }
  }, [])

  /** H1. Crea la cuenta del dueño y entra directamente. */
  const registrarDueno = useCallback(
    async ({ nombre, correo, password, telefono }) => {
      await api.registro({
        nombre: nombre.trim(),
        correo: correo.trim().toLowerCase(),
        password,
        telefono: telefono.trim() || null,
        rol: 'DUENO_BOTICA',
      })
      return iniciarSesion(correo, password)
    },
    [iniciarSesion],
  )

  const cerrarSesion = useCallback(() => {
    borrarSesion()
    setUsuario(null)
    navigate('/login', { replace: true })
  }, [navigate])

  const valor = useMemo(
    () => ({ usuario, iniciarSesion, registrarDueno, cerrarSesion }),
    [usuario, iniciarSesion, registrarDueno, cerrarSesion],
  )
  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react/only-export-components
export function useAuth() {
  return useContext(AuthContext)
}
