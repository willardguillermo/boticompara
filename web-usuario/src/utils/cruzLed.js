// H20: la cruz de botica hecha de LEDs que aparece al verificar el código (CodigoOtp).
// Misma forma que el ícono del logo (un "+" cuyos brazos miden un tercio del ancho), en una cuadrícula de puntos.

/**
 * Puntos de una cruz de `lado` x `lado` con brazos de `brazo` puntos de ancho.
 * Cada punto trae su columna, su fila y `d`: la distancia al centro (en anillos), para que la onda de luz
 * salga del centro y llegue a las puntas de los brazos.
 */
export function puntosCruz(lado = 9, brazo = 3) {
  const centro = (lado - 1) / 2
  const desde = (lado - brazo) / 2
  const enBrazo = (v) => v >= desde && v < desde + brazo
  const puntos = []
  for (let fila = 0; fila < lado; fila++) {
    for (let columna = 0; columna < lado; columna++) {
      if (!enBrazo(fila) && !enBrazo(columna)) continue
      puntos.push({ columna, fila, d: Math.max(Math.abs(fila - centro), Math.abs(columna - centro)) })
    }
  }
  return puntos
}
