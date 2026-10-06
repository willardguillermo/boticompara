import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '../context/AuthContext.jsx'
import AvisoSoloMovil from './AvisoSoloMovil.jsx'

/** Sin sesión no se entra al panel: redirige al login y recuerda a dónde iba. */
export default function RutaProtegida() {
  const { usuario, cerrarSesion } = useAuth()
  const ubicacion = useLocation()

  if (!usuario) return <Navigate to="/login" replace state={{ desde: ubicacion.pathname }} />

  if (usuario.rol !== 'DUENO_BOTICA') {
    return (
      <main className="acceso">
        <div className="tarjeta">
          <AvisoSoloMovil nombre={usuario.nombre} onVolver={cerrarSesion} />
        </div>
      </main>
    )
  }
  return <Outlet />
}
