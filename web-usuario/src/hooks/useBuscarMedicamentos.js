// H31 / D3 / D4 - Búsqueda en el catálogo maestro (GET /medicamentos) mientras se escribe.
// La usan el buscador de ID de la carga masiva y el selector del formulario de producto.
import { useEffect, useState } from 'react'
import { api } from '../services/index.js'

export const MIN_BUSQUEDA_MEDICAMENTO = 2

/**
 * Devuelve { buscando, medicamentos, error } para el texto escrito.
 * medicamentos es null mientras llega la respuesta del término actual.
 */
export function useBuscarMedicamentos(busqueda) {
  const termino = busqueda.trim()
  const buscando = termino.length >= MIN_BUSQUEDA_MEDICAMENTO
  // El resultado guarda su término: si el texto cambió, se ignora sin tener que limpiarlo
  const [resultado, setResultado] = useState({ termino: '', medicamentos: null, error: '' })

  // Busca en el servidor con una pequeña espera mientras se escribe, como el catálogo
  useEffect(() => {
    if (!buscando) return undefined
    let activo = true
    const espera = setTimeout(() => {
      api
        .buscarMedicamentos(termino)
        .then((medicamentos) => activo && setResultado({ termino, medicamentos, error: '' }))
        .catch((err) => {
          if (!activo || err.status === 401) return
          setResultado({
            termino,
            medicamentos: null,
            error: err.mensaje ?? 'No se pudo buscar en el catálogo de medicamentos.',
          })
        })
    }, 300)
    return () => {
      activo = false
      clearTimeout(espera)
    }
  }, [buscando, termino])

  const vigente = buscando && resultado.termino === termino
  return {
    termino,
    buscando,
    medicamentos: vigente ? resultado.medicamentos : null,
    error: vigente ? resultado.error : '',
  }
}

/** "Tableta 500 mg" o solo "Tableta" si no tiene concentración. */
export const presentacionMedicamento = (m) =>
  m.concentracion ? `${m.presentacion} ${m.concentracion}` : m.presentacion
