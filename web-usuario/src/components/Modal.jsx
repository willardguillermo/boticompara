import { useEffect, useId, useRef, useState } from 'react'

const ENFOCABLES =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

// Modales abiertos, del más antiguo al más nuevo: Escape y Tab solo actúan sobre el de arriba
const abiertos = []

/**
 * Ventana modal accesible: se cierra con Escape o haciendo clic fuera.
 * Al abrir pone el foco dentro (o respeta un autoFocus), lo mantiene dentro con Tab
 * y al cerrar lo devuelve al botón que la abrió.
 */
export default function Modal({ titulo, onCerrar, pequeno = false, children }) {
  const idTitulo = useId()
  const ventana = useRef(null)
  const alCerrar = useRef(onCerrar)
  // Se toma al crear el modal, antes de que un autoFocus de adentro mueva el foco
  const [anterior] = useState(() => document.activeElement)

  useEffect(() => {
    alCerrar.current = onCerrar
  }, [onCerrar])

  useEffect(() => {
    const nodo = ventana.current
    abiertos.push(nodo)
    if (!nodo.contains(document.activeElement)) nodo.focus()

    function alPresionar(e) {
      if (abiertos.at(-1) !== nodo) return
      if (e.key === 'Escape') {
        e.preventDefault()
        alCerrar.current()
      } else if (e.key === 'Tab') {
        const elementos = [...nodo.querySelectorAll(ENFOCABLES)]
        if (!elementos.length) return e.preventDefault()
        const primero = elementos[0]
        const ultimo = elementos.at(-1)
        if (e.shiftKey && (document.activeElement === primero || document.activeElement === nodo)) {
          e.preventDefault()
          ultimo.focus()
        } else if (!e.shiftKey && document.activeElement === ultimo) {
          e.preventDefault()
          primero.focus()
        }
      }
    }
    document.addEventListener('keydown', alPresionar)
    return () => {
      document.removeEventListener('keydown', alPresionar)
      abiertos.splice(abiertos.indexOf(nodo), 1)
      if (anterior instanceof HTMLElement && anterior.isConnected) anterior.focus()
    }
  }, [anterior])

  return (
    <div className="modal-fondo" onMouseDown={(e) => e.target === e.currentTarget && onCerrar()}>
      <div
        ref={ventana}
        className={`tarjeta modal ${pequeno ? 'modal-pequeno' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={idTitulo}
        tabIndex={-1}
      >
        <h2 id={idTitulo}>{titulo}</h2>
        {children}
      </div>
    </div>
  )
}
