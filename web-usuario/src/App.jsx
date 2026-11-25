import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { ConBotica, InicioPanel, SinBotica, SoloAprobada, SoloRechazada } from './components/GuardiasBotica.jsx'
import PanelLayout from './components/PanelLayout.jsx'
import RutaProtegida from './components/RutaProtegida.jsx'
import RutaPublica from './components/RutaPublica.jsx'
import { AuthProvider } from './context/AuthContext.jsx'
import { ToastProvider } from './context/ToastContext.jsx'
import CargaMasivaPage from './pages/CargaMasivaPage.jsx'
import CatalogoPage from './pages/CatalogoPage.jsx'
import CorregirBoticaPage from './pages/CorregirBoticaPage.jsx'
import CuentaPage from './pages/CuentaPage.jsx'
import EstadoPage from './pages/EstadoPage.jsx'
import InicioPage from './pages/InicioPage.jsx'
import LandingPage from './pages/LandingPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import PedidosPage from './pages/PedidosPage.jsx'
import RecuperarPasswordPage from './pages/RecuperarPasswordPage.jsx'
import RegistroBoticaPage from './pages/RegistroBoticaPage.jsx'
import RegistroPage from './pages/RegistroPage.jsx'

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <Routes>
            {/* Página de presentación: pública también con sesión (entonces ofrece "Ir a mi panel") */}
            <Route path="/" element={<LandingPage />} />
            <Route element={<RutaPublica />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/registro" element={<RegistroPage />} />
              <Route path="/recuperar" element={<RecuperarPasswordPage />} />
            </Route>
            <Route element={<RutaProtegida />}>
              <Route path="/panel" element={<PanelLayout />}>
                <Route index element={<InicioPanel />} />
                <Route
                  path="botica/registro"
                  element={<SinBotica><RegistroBoticaPage /></SinBotica>}
                />
                <Route
                  path="botica/corregir"
                  element={<ConBotica><SoloRechazada><CorregirBoticaPage /></SoloRechazada></ConBotica>}
                />
                <Route path="inicio" element={<ConBotica><SoloAprobada><InicioPage /></SoloAprobada></ConBotica>} />
                <Route path="estado" element={<ConBotica><EstadoPage /></ConBotica>} />
                <Route path="catalogo" element={<ConBotica><CatalogoPage /></ConBotica>} />
                <Route path="pedidos" element={<ConBotica><PedidosPage /></ConBotica>} />
                {/* H16, H19: sin ConBotica, para corregir el nombre aunque aún no tenga botica (A7) */}
                <Route path="cuenta" element={<CuentaPage />} />
                <Route
                  path="catalogo/carga-masiva"
                  element={<ConBotica><SoloAprobada><CargaMasivaPage /></SoloAprobada></ConBotica>}
                />
              </Route>
            </Route>
            <Route path="*" element={<Navigate to="/panel" replace />} />
          </Routes>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}
