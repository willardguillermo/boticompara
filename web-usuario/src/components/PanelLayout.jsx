import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../context/AuthContext.jsx'
import { BoticaProvider, useBotica } from '../context/BoticaContext.jsx'
import { NotificacionesProvider } from '../context/NotificacionesContext.jsx'
import Alerta from './Alerta.jsx'
import AsistenteChat from './AsistenteChat.jsx'
import BotonTema from './BotonTema.jsx'
import Campana from './Campana.jsx'
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
            {aprobada && (
              <NavLink to="/panel/inicio">
                <Icono nombre="casa" /> Inicio
              </NavLink>
            )}
            <NavLink to="/panel/estado">
              <Icono nombre="tienda" /> Mi botica
            </NavLink>
            <NavLink to="/panel/catalogo">
              <Icono nombre={aprobada ? 'caja' : 'candado'} /> Catálogo
            </NavLink>
            <NavLink to="/panel/pedidos">
              <Icono nombre="pedido" /> Pedidos
            </NavLink>
          </nav>
        )}
        <div className="usuario-sesion">
          {/* H16: el nombre lleva a Mi cuenta; en celular solo se ve el ícono */}
          <NavLink to="/panel/cuenta" className="enlace-cuenta" aria-label={`Mi cuenta: ${usuario.nombre}`} title="Mi cuenta">
            <Icono nombre="usuario" /> <span className="nombre-usuario">{usuario.nombre}</span>
          </NavLink>
          <BotonTema />
          <Campana />
          {/* key: si cambia el usuario, el chat empieza de cero */}
          <AsistenteChat key={usuario.id} usuarioId={usuario.id} />
          <button type="button" className="btn btn-secundario btn-icono" onClick={cerrarSesion}>
            {/* En celular solo se ve el ícono; el texto queda para el lector de pantalla */}
            <Icono nombre="salir" /> <span className="texto-escritorio">Salir</span>
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
      <NotificacionesProvider>
        <Cabecera />
        <main className="contenido">
          <Contenido />
        </main>
      </NotificacionesProvider>
    </BoticaProvider>
  )
}
