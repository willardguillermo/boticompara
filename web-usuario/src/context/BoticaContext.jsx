import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api } from '../services/index.js'

const BoticaContext = createContext(null)

/**
 * Botica del dueño logueado (GET /boticas/mia).
 *   botica === undefined -> cargando
 *   botica === null      -> el dueño aún no registró su botica (404)
 */
export function BoticaProvider({ children }) {
  const [botica, setBotica] = useState(undefined)
  const [error, setError] = useState('')

  const recargar = useCallback(async () => {
    setError('')
    try {
      const datos = await api.obtenerMiBotica()
      setBotica(datos)
      return datos
    } catch (err) {
      if (err.status === 404) {
        setBotica(null)
        return null
      }
      if (err.status !== 401) setError(err.mensaje ?? 'No se pudo cargar tu botica.')
      throw err
    }
  }, [])

  useEffect(() => {
    recargar().catch(() => {})
  }, [recargar])

  const valor = useMemo(() => ({ botica, error, recargar, setBotica }), [botica, error, recargar])
  return <BoticaContext.Provider value={valor}>{children}</BoticaContext.Provider>
}

// eslint-disable-next-line react/only-export-components
export function useBotica() {
  return useContext(BoticaContext)
}
