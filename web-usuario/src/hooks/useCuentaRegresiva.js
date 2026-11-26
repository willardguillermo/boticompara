// H20: cuenta regresiva para volver a pedir un código ("Reenviar código en 0:42").
import { useCallback, useEffect, useState } from 'react'
import { segundosRestantes } from '../utils/cuentaRegresiva.js'

/** { segundos, iniciar(segundos) }: segundos es lo que falta (0 cuando ya se puede). */
export default function useCuentaRegresiva() {
  const [hasta, setHasta] = useState(0)
  const [ahora, setAhora] = useState(() => Date.now())

  useEffect(() => {
    if (!hasta) return
    const reloj = setInterval(() => {
      const momento = Date.now()
      setAhora(momento)
      if (momento >= hasta) clearInterval(reloj)
    }, 250)
    return () => clearInterval(reloj)
  }, [hasta])

  const iniciar = useCallback((segundos) => {
    const momento = Date.now()
    setAhora(momento)
    setHasta(momento + segundos * 1000)
  }, [])

  return { segundos: segundosRestantes(hasta, ahora), iniciar }
}
