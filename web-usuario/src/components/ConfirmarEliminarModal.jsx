// H10 - Eliminar producto (DELETE /productos/{id}, baja lógica)
// Regla nueva: con pedidos CONFIRMADA o LISTA el backend responde 409 y el producto no se da de baja
import { useState } from 'react'
import { Link } from 'react-router'
import { api } from '../services/index.js'
import Alerta from './Alerta.jsx'
import Modal from './Modal.jsx'

export default function ConfirmarEliminarModal({ producto, onCerrar, onEliminado }) {
  const [eliminando, setEliminando] = useState(false)
  const [error, setError] = useState('')
  const [bloqueado, setBloqueado] = useState('')

  async function eliminar() {
    setEliminando(true)
    setError('')
    try {
      await api.eliminarProducto(producto.id)
      onEliminado(producto)
    } catch (err) {
      if (err.status === 409) setBloqueado(err.mensaje ?? 'No puedes dar de baja este producto por ahora.')
      else setError(err.mensaje ?? 'No se pudo eliminar el producto.')
      setEliminando(false)
    }
  }

  if (bloqueado) {
    return (
      <Modal titulo="No se puede eliminar" onCerrar={onCerrar} pequeno>
        <div className="formulario">
          <p>
            <strong>{producto.nombreComercial}</strong> ({producto.presentacion}) tiene pedidos pendientes de
            entregar.
          </p>
          <Alerta tipo="error"><p>{bloqueado}</p></Alerta>
          <div className="acciones-form">
            <button type="button" className="btn btn-secundario" onClick={onCerrar}>
              Cerrar
            </button>
            <Link to="/panel/pedidos" className="btn btn-primario">
              Ver pedidos
            </Link>
          </div>
        </div>
      </Modal>
    )
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
