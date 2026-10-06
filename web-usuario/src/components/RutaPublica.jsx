import { Navigate, Outlet } from 'react-router'
import { useAuth } from '../context/AuthContext.jsx'

/** Login y registro: si ya hay sesión de dueño, se va directo al panel. */
export default function RutaPublica() {
  const { usuario } = useAuth()
  if (usuario?.rol === 'DUENO_BOTICA') return <Navigate to="/panel" replace />
  return <Outlet />
}
