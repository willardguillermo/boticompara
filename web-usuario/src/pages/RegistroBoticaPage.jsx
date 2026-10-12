// H1 - Registro de la botica del dueño (queda PENDIENTE hasta que el admin la aprueba)
// H2 y H3: validación del RUC y ubicación en el mapa, en BoticaForm
import { useNavigate } from 'react-router'
import BoticaForm from '../components/BoticaForm.jsx'
import { useBotica } from '../context/BoticaContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { api } from '../services/index.js'

export default function RegistroBoticaPage() {
  const { recargar } = useBotica()
  const toast = useToast()
  const navigate = useNavigate()

  async function registrar(datos) {
    await api.registrarBotica(datos)
    await recargar()
    toast('Botica registrada. Quedó pendiente de aprobación.')
    navigate('/panel/estado', { replace: true })
  }

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Registra tu botica</h1>
          <p className="texto-suave">
            Un administrador revisará tus datos. Cuando tu botica sea aprobada podrás publicar tu catálogo.
          </p>
        </div>
      </div>

      <BoticaForm
        textoBoton="Enviar solicitud"
        textoEnviando="Registrando botica..."
        errorGenerico="No se pudo registrar la botica."
        onGuardar={registrar}
      />
    </>
  )
}
