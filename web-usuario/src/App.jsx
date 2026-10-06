import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import RutaProtegida from './components/RutaProtegida.jsx'
import RutaPublica from './components/RutaPublica.jsx'
import { AuthProvider } from './context/AuthContext.jsx'
import { ToastProvider } from './context/ToastContext.jsx'
import LoginPage from './pages/LoginPage.jsx'
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
              <Route path="/panel/*" element={<main className="contenido"><h1>Panel</h1></main>} />
            </Route>
            <Route path="*" element={<Navigate to="/panel" replace />} />
          </Routes>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}
