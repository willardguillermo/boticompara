import { useEffect, useId } from 'react'

/** Ventana modal accesible: se cierra con Escape o haciendo clic fuera. */
export default function Modal({ titulo, onCerrar, pequeno = false, children }) {
  const idTitulo = useId()

  useEffect(() => {
    const alPresionar = (e) => e.key === 'Escape' && onCerrar()
    document.addEventListener('keydown', alPresionar)
    return () => document.removeEventListener('keydown', alPresionar)
  }, [onCerrar])

  return (
    <div className="modal-fondo" onMouseDown={(e) => e.target === e.currentTarget && onCerrar()}>
      <div
        className={`tarjeta modal ${pequeno ? 'modal-pequeno' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={idTitulo}
      >
        <h2 id={idTitulo}>{titulo}</h2>
        {children}
      </div>
    </div>
  )
}
