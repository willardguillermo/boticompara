import { useEffect } from 'react'

/**
 * Animación de entrada al hacer scroll: cada `.revelar` dentro de `raizRef` recibe `.revelado`
 * cuando entra en la ventana (una sola vez).
 * Solo se esconden de antemano si la raíz tiene `.lp-animar`, que se pone aquí: sin JavaScript,
 * sin IntersectionObserver o con movimiento reducido el contenido se ve desde el inicio.
 */
export default function useRevelar(raizRef, activo) {
  useEffect(() => {
    const raiz = raizRef.current
    if (!raiz || !activo || typeof IntersectionObserver === 'undefined') return undefined
    const elementos = [...raiz.querySelectorAll('.revelar')]
    const observador = new IntersectionObserver(
      (entradas) => {
        for (const entrada of entradas) {
          if (!entrada.isIntersecting) continue
          entrada.target.classList.add('revelado')
          observador.unobserve(entrada.target)
        }
      },
      { rootMargin: '0px 0px -8% 0px', threshold: 0.12 },
    )
    for (const el of elementos) observador.observe(el)
    raiz.classList.add('lp-animar')
    return () => {
      observador.disconnect()
      raiz.classList.remove('lp-animar')
      for (const el of elementos) el.classList.remove('revelado')
    }
  }, [raizRef, activo])
}
