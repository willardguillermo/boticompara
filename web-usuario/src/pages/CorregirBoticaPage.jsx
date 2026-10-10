// H7 - Corregir y reenviar la botica rechazada (PUT /boticas/mia).
// La ruta solo se muestra con la botica RECHAZADA (guardia SoloRechazada).
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import BoticaForm from '../components/BoticaForm.jsx'
import SeccionLicencia from '../components/SeccionLicencia.jsx'
import { useBotica } from '../context/BoticaContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { api } from '../services/index.js'

export default function CorregirBoticaPage() {
  const { botica, recargar } = useBotica()
  const toast = useToast()
  const navigate = useNavigate()

  // Se decide al entrar: si sube la licencia aquí, la sección sigue visible con "✓ Licencia cargada"
  const [pedirLicencia] = useState(!botica.tieneLicencia)
  // El formulario se precarga una sola vez; recargar la botica (p. ej. al subir la licencia) no lo borra
  const [inicial] = useState(() => ({
    nombreComercial: botica.nombreComercial ?? '',
    ruc: botica.ruc ?? '',
    razonSocial: botica.razonSocial ?? '',
    direccion: botica.direccion ?? '',
    distrito: botica.distrito ?? '',
    telefono: botica.telefono ?? '',
  }))
  const [ubicacionInicial] = useState(() =>
    botica.latitud != null && botica.longitud != null
      ? { latitud: Number(botica.latitud), longitud: Number(botica.longitud) }
      : null,
  )

  async function reenviar(datos) {
    try {
      await api.actualizarMiBotica(datos)
    } catch (err) {
      // 403: el estado cambió mientras corregía (por ejemplo, el admin ya la revisó)
      if (err.status !== 403) throw err
      toast(err.mensaje ?? 'Tu botica ya no está rechazada.', 'error')
      await recargar()
      navigate('/panel/estado', { replace: true })
      return
    }
    toast('Solicitud reenviada. Quedó pendiente de revisión.')
    await recargar()
    navigate('/panel/estado', { replace: true })
  }

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Corrige tu solicitud</h1>
          <p className="texto-suave">
            Revisa el motivo, corrige los datos y reenvía la solicitud. Volverá a quedar pendiente de revisión.
          </p>
        </div>
      </div>

      <div className="motivo">
        <Alerta tipo="error">
          <p>
            <strong>Motivo del rechazo:</strong> {botica.motivoRechazo || 'No se indicó un motivo.'}
          </p>
        </Alerta>
      </div>

      {pedirLicencia && (
        <div className="corregir-licencia">
          <SeccionLicencia tieneLicencia={botica.tieneLicencia} />
        </div>
      )}

      <BoticaForm
        inicial={inicial}
        ubicacionInicial={ubicacionInicial}
        textoBoton="Reenviar solicitud"
        textoEnviando="Reenviando solicitud..."
        errorGenerico="No se pudo reenviar la solicitud."
        onGuardar={reenviar}
      >
        <Link to="/panel/estado" className="btn btn-secundario">
          Cancelar
        </Link>
      </BoticaForm>
    </>
  )
}
