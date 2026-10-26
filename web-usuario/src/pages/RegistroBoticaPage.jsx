// H1 - Registro de la botica del dueño (queda PENDIENTE hasta que el admin la aprueba)
// H2 y H3: validación del RUC y ubicación en el mapa, en BoticaForm
import { useNavigate } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import BoticaForm from '../components/BoticaForm.jsx'
import RegistroPasos from '../components/RegistroPasos.jsx'
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
    <RegistroPasos paso={2}>
      <Alerta tipo="exito">
        <p>Tu cuenta fue creada. Si sales ahora, puedes iniciar sesión más tarde y continuar desde aquí.</p>
      </Alerta>
      <div className="pasos-encabezado">
        <h1>Datos de tu botica</h1>
        <p>Así te verán los compradores en los resultados de búsqueda.</p>
      </div>

      <BoticaForm
        textoBoton="Enviar solicitud"
        textoEnviando="Registrando botica..."
        errorGenerico="No se pudo registrar la botica."
        onGuardar={registrar}
      />
    </RegistroPasos>
  )
}
