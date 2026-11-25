// H19 - Eliminar la cuenta (DELETE /usuarios/me). Las consecuencias son las de docs/API.md, sección 2.
// Esta web es solo para dueños (un COMPRADOR no puede iniciar sesión aquí), así que no se listan las del comprador.
import { useState } from 'react'
import { api } from '../services/index.js'
import { avisoBoticaAlEliminar, confirmacionValida, PALABRA_ELIMINAR } from '../utils/cuenta.js'
import Alerta from './Alerta.jsx'
import Campo from './Campo.jsx'
import Modal from './Modal.jsx'

export default function EliminarCuentaModal({ botica, onCerrar, onEliminada }) {
  const [texto, setTexto] = useState('')
  const [eliminando, setEliminando] = useState(false)
  const [error, setError] = useState('')
  const habilitado = confirmacionValida(texto) && !eliminando
  const avisoBotica = avisoBoticaAlEliminar(botica)

  // Mientras se elimina no se cierra (ni con Escape ni con clic fuera)
  const cerrar = () => {
    if (!eliminando) onCerrar()
  }

  async function eliminar(e) {
    e.preventDefault()
    if (!habilitado) return
    setEliminando(true)
    setError('')
    try {
      await api.eliminarCuenta()
      onEliminada()
    } catch (err) {
      setError(err.mensaje ?? 'No se pudo eliminar la cuenta. Inténtalo de nuevo.')
      setEliminando(false)
    }
  }

  return (
    <Modal titulo="¿Eliminar tu cuenta?" onCerrar={cerrar} pequeno>
      <form className="formulario" onSubmit={eliminar} noValidate>
        <p>Esta acción no se puede deshacer. Al eliminar tu cuenta:</p>
        <ul className="lista-consecuencias">
          <li>
            Tus datos se anonimizan: tu nombre pasa a «Usuario eliminado» y se quitan tu correo, teléfono y
            dirección.
          </li>
          <li>Tu contraseña deja de servir y ya no podrás entrar con esta cuenta.</li>
          {botica ? (
            <>
              <li>
                Todos los productos de <strong>{botica.nombreComercial}</strong> se dan de baja y dejan de aparecer
                en las búsquedas de los compradores.
              </li>
              {avisoBotica && (
                <li>
                  <strong>{botica.nombreComercial}</strong> {avisoBotica}
                </li>
              )}
              <li>
                Tus pedidos pendientes, confirmados o listos para recoger se rechazan, y cada comprador recibe un
                correo con el aviso.
              </li>
            </>
          ) : (
            <li>
              Si registraste una botica, sus productos dejan de aparecer en las búsquedas, deja de estar aprobada y
              sus pedidos activos se rechazan avisando a los compradores.
            </li>
          )}
          <li>Se borran tus favoritos y tus alertas de precio.</li>
        </ul>

        <Campo
          id="confirmar-eliminar"
          etiqueta={
            <>
              Escribe <strong>{PALABRA_ELIMINAR}</strong> para confirmar
            </>
          }
        >
          <input
            id="confirmar-eliminar"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            disabled={eliminando}
            // El foco entra directo aquí: es lo único que se puede hacer antes de confirmar
            autoFocus
          />
        </Campo>

        <Alerta tipo="error">{error && <p>{error}</p>}</Alerta>

        <div className="acciones-form">
          <button type="button" className="btn btn-secundario" onClick={cerrar} disabled={eliminando}>
            Cancelar
          </button>
          <button type="submit" className="btn btn-peligro" disabled={!habilitado}>
            {eliminando ? 'Eliminando...' : 'Eliminar mi cuenta'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
