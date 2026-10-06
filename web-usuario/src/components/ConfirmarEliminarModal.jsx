// H10 - Eliminar producto (DELETE /productos/{id}, baja lógica)
import { useState } from 'react'
import { api } from '../services/index.js'
import Alerta from './Alerta.jsx'
import Modal from './Modal.jsx'

export default function ConfirmarEliminarModal({ producto, onCerrar, onEliminado }) {
  const [eliminando, setEliminando] = useState(false)
  const [error, setError] = useState('')

  async function eliminar() {
    setEliminando(true)
    setError('')
    try {
      await api.eliminarProducto(producto.id)
      onEliminado(producto)
    } catch (err) {
      setError(err.mensaje ?? 'No se pudo eliminar el producto.')
      setEliminando(false)
    }
  }

  return (
    <Modal titulo="¿Eliminar producto?" onCerrar={onCerrar} pequeno>
      <div className="formulario">
        <p>
          <strong>{producto.nombreComercial}</strong> ({producto.presentacion}) dejará de aparecer en tu
          catálogo y en las búsquedas de los compradores.
        </p>
        <Alerta tipo="error">{error && <p>{error}</p>}</Alerta>
        <div className="acciones-form">
          <button type="button" className="btn btn-secundario" onClick={onCerrar} disabled={eliminando}>
            Cancelar
          </button>
          <button type="button" className="btn btn-peligro" onClick={eliminar} disabled={eliminando}>
            {eliminando ? 'Eliminando...' : 'Sí, eliminar'}
          </button>
        </div>
      </div>
    </Modal>
  )
}
