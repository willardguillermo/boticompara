import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api } from '../services/index.js'

const BoticaContext = createContext(null)

/** GET /boticas/mia. Un 404 significa que el dueño aún no registró su botica. */
async function consultarBotica() {
  try {
    return { botica: await api.obtenerMiBotica(), error: '' }
  } catch (err) {
    if (err.status === 404) return { botica: null, error: '' }
    // Con 401 la sesión ya se cerró y se redirige al login
    return { botica: undefined, error: err.status === 401 ? '' : err.mensaje ?? 'No se pudo cargar tu botica.' }
  }
}

/**
 * Botica del dueño logueado.
 *   botica === undefined -> cargando
 *   botica === null      -> el dueño aún no registró su botica
 */
export function BoticaProvider({ children }) {
  const [botica, setBotica] = useState(undefined)
  const [error, setError] = useState('')

  const aplicar = useCallback((resultado) => {
    setBotica(resultado.botica)
    setError(resultado.error)
    return resultado.botica
  }, [])

  const recargar = useCallback(() => consultarBotica().then(aplicar), [aplicar])

  useEffect(() => {
    let activo = true
    consultarBotica().then((resultado) => activo && aplicar(resultado))
    return () => {
      activo = false
    }
  }, [aplicar])

  const valor = useMemo(() => ({ botica, error, recargar, setBotica }), [botica, error, recargar])
  return <BoticaContext.Provider value={valor}>{children}</BoticaContext.Provider>
}

// eslint-disable-next-line react/only-export-components
export function useBotica() {
  return useContext(BoticaContext)
}
