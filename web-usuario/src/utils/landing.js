// Lógica de la página de presentación (landing, "/"): datos de ejemplo de la demo, cálculo del
// ahorro y qué sección está a la vista. Sin DOM, para probarla con node --test.

/**
 * Precios de ejemplo de la demo (los mismos de database/seed.sql). La página los marca siempre como
 * "precios de ejemplo de la demo": no son precios reales del mercado.
 * En Amoxicilina se comparan dos productos distintos con el mismo principio activo y la misma
 * presentación (marca contra genérico); en Paracetamol, el mismo producto en dos boticas.
 */
export const EJEMPLOS_DEMO = [
  {
    id: 'amoxicilina',
    nombre: 'Amoxicilina',
    presentacion: 'Cápsula 500 mg x 12',
    nota: 'Alternativa equivalente: mismo principio activo y presentación',
    caro: { botica: 'Botica Salud Total', producto: 'Amoxil', precio: 24 },
    barato: { botica: 'FarmaVida', producto: 'Amoxicilina genérica', precio: 8.5 },
  },
  {
    id: 'paracetamol',
    nombre: 'Paracetamol',
    presentacion: 'Tableta 500 mg x 10',
    nota: 'El mismo producto en dos boticas',
    caro: { botica: 'Botica Salud Total', producto: 'Paracetamol genérico', precio: 2.5 },
    barato: { botica: 'FarmaVida', producto: 'Paracetamol genérico', precio: 2.2 },
  },
]

const aCentimos = (precio) => Math.round(Number(precio) * 100)
const esPrecio = (precio) => typeof precio === 'number' && Number.isFinite(precio) && precio >= 0

/**
 * Cuánto se ahorra comprando en la botica más barata. Se calcula en céntimos para que
 * 24 - 8.5 dé 15.5 exacto y no 15.499999.
 * Devuelve { ahorro (soles), porcentaje (entero, sobre el precio mayor), masBarato ('a' | 'b' | null) }
 * o null si algún precio no es un número válido mayor o igual a cero.
 */
export function calcularAhorro(precioA, precioB) {
  if (!esPrecio(precioA) || !esPrecio(precioB)) return null
  const a = aCentimos(precioA)
  const b = aCentimos(precioB)
  const mayor = Math.max(a, b)
  const diferencia = Math.abs(a - b)
  return {
    ahorro: diferencia / 100,
    porcentaje: mayor === 0 ? 0 : Math.round((diferencia / mayor) * 100),
    masBarato: a === b ? null : a < b ? 'a' : 'b',
  }
}

/** Ahorro de un ejemplo de la demo (del caro al barato). */
export const ahorroDeEjemplo = (ejemplo) => calcularAhorro(ejemplo.caro.precio, ejemplo.barato.precio)

/** Ancho de la barra del precio menor respecto al mayor, en porcentaje entero (0 a 100). */
export function proporcionPrecio(precio, precioMayor) {
  if (!esPrecio(precio) || !esPrecio(precioMayor) || precioMayor === 0) return 0
  return Math.min(100, Math.round((precio / precioMayor) * 100))
}

/** El ejemplo que sigue en el carrusel del comparador, volviendo al primero al final. */
export function siguienteIndice(indice, total) {
  if (!Number.isInteger(total) || total <= 0) return 0
  return (((indice + 1) % total) + total) % total
}

/**
 * La sección activa de la barra de navegación.
 * `medidas`: [{ id, top }] en el orden de la página, con `top` = distancia del borde superior de la
 * sección al borde superior de la ventana (getBoundingClientRect().top).
 * Activa es la última sección cuyo borde ya pasó la línea `umbral` (en píxeles desde arriba).
 * Antes de la primera (en el hero) no hay ninguna activa: null.
 * Si la página llegó al final, se activa la última aunque sea corta y no alcance la línea.
 */
export function seccionActiva(medidas, umbral, alFinal = false) {
  if (!Array.isArray(medidas) || medidas.length === 0) return null
  if (alFinal) return medidas[medidas.length - 1].id
  let activa = null
  for (const { id, top } of medidas) {
    if (top <= umbral) activa = id
    else break
  }
  return activa
}
