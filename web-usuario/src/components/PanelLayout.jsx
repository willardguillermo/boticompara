import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../context/AuthContext.jsx'
import { BoticaProvider, useBotica } from '../context/BoticaContext.jsx'
import Alerta from './Alerta.jsx'
import Cargando from './Cargando.jsx'
import Icono from './Icono.jsx'
import Marca from './Marca.jsx'

function Cabecera() {
  const { usuario, cerrarSesion } = useAuth()
  const { botica } = useBotica()
  const aprobada = botica?.estado === 'APROBADO'

  return (
    <header className="cabecera">
      <div className="cabecera-contenido">
        <Marca />
        {botica && (
          <nav className="navegacion" aria-label="Secciones del panel">
            <NavLink to="/panel/estado">
              <Icono nombre="tienda" /> Mi botica
            </NavLink>
            <NavLink to="/panel/catalogo">
              <Icono nombre={aprobada ? 'caja' : 'candado'} /> Catálogo
            </NavLink>
          </nav>
        )}
        <div className="usuario-sesion">
          <span className="nombre-usuario texto-suave">{usuario.nombre}</span>
          <button type="button" className="btn btn-secundario btn-icono" onClick={cerrarSesion}>
            <Icono nombre="salir" /> Salir
          </button>
        </div>
      </div>
    </header>
  )
}

function Contenido() {
  const { botica, error, recargar } = useBotica()

  if (error) {
    return (
      <div className="tarjeta formulario">
        <Alerta tipo="error">
          <p>{error}</p>
        </Alerta>
        <div>
          <button type="button" className="btn btn-secundario" onClick={recargar}>
            <Icono nombre="actualizar" /> Reintentar
          </button>
        </div>
      </div>
    )
  }
  if (botica === undefined) return <Cargando texto="Cargando tu botica..." />
  return <Outlet />
}

/** Estructura del panel protegido: cabecera + contenido de cada pantalla. */
export default function PanelLayout() {
  return (
    <BoticaProvider>
      <Cabecera />
      <main className="contenido">
        <Contenido />
      </main>
    </BoticaProvider>
  )
}
