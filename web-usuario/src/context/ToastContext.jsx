import { createContext, useCallback, useContext, useState } from 'react'
import Alerta from '../components/Alerta.jsx'

const ToastContext = createContext(null)

/** Mensajes breves de éxito o error que desaparecen solos. */
export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])

  const mostrar = useCallback((mensaje, tipo = 'exito') => {
    const id = crypto.randomUUID()
    setToasts((t) => [...t, { id, mensaje, tipo }])
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4000)
  }, [])

  return (
    <ToastContext.Provider value={mostrar}>
      {children}
      <div className="toasts" aria-live="polite">
        {toasts.map((t) => (
          <Alerta key={t.id} tipo={t.tipo}>
            <p>{t.mensaje}</p>
          </Alerta>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

// eslint-disable-next-line react/only-export-components
export function useToast() {
  return useContext(ToastContext)
}
