import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { ConBotica, InicioPanel, SinBotica } from './components/GuardiasBotica.jsx'
import PanelLayout from './components/PanelLayout.jsx'
import RutaProtegida from './components/RutaProtegida.jsx'
import RutaPublica from './components/RutaPublica.jsx'
import { AuthProvider } from './context/AuthContext.jsx'
import { ToastProvider } from './context/ToastContext.jsx'
import CatalogoPage from './pages/CatalogoPage.jsx'
import EstadoPage from './pages/EstadoPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import RegistroBoticaPage from './pages/RegistroBoticaPage.jsx'
import RegistroPage from './pages/RegistroPage.jsx'

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <Routes>
            <Route element={<RutaPublica />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/registro" element={<RegistroPage />} />
            </Route>
            <Route element={<RutaProtegida />}>
              <Route path="/panel" element={<PanelLayout />}>
                <Route index element={<InicioPanel />} />
                <Route
                  path="botica/registro"
                  element={<SinBotica><RegistroBoticaPage /></SinBotica>}
                />
                <Route path="estado" element={<ConBotica><EstadoPage /></ConBotica>} />
                <Route path="catalogo" element={<ConBotica><CatalogoPage /></ConBotica>} />
              </Route>
            </Route>
            <Route path="*" element={<Navigate to="/panel" replace />} />
          </Routes>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}
