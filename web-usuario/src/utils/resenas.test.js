// Pruebas de "Lo que dicen tus clientes" (H87): npm test
import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  etiquetaEstrellas, formatoFechaResena, hayMasPaginas, rellenoEstrellas, textoCantidad, textoPromedio, unirPaginas,
} from './resenas.js'

test('textoPromedio: con 1 decimal; sin reseñas (null) muestra una raya', () => {
  assert.equal(textoPromedio(4.3), '4.3')
  assert.equal(textoPromedio(5), '5.0')
  assert.equal(textoPromedio(null), '—')
})

test('textoCantidad: singular, plural y sin reseñas', () => {
  assert.equal(textoCantidad(1), '1 reseña')
  assert.equal(textoCantidad(12), '12 reseñas')
  assert.equal(textoCantidad(0), 'Sin reseñas')
})

test('rellenoEstrellas: la fracción exacta de cada estrella, entre 0 y 5', () => {
  assert.deepEqual(rellenoEstrellas(4.3), [100, 100, 100, 100, 30])
  assert.deepEqual(rellenoEstrellas(3), [100, 100, 100, 0, 0])
  assert.deepEqual(rellenoEstrellas(0.5), [50, 0, 0, 0, 0])
  assert.deepEqual(rellenoEstrellas(null), [0, 0, 0, 0, 0])
  assert.deepEqual(rellenoEstrellas(7), [100, 100, 100, 100, 100])
})

test('etiquetaEstrellas: texto para el lector de pantalla', () => {
  assert.equal(etiquetaEstrellas(4), '4 de 5 estrellas')
  assert.equal(etiquetaEstrellas(4.3), '4.3 de 5 estrellas')
})

test('formatoFechaResena: el día en hora de Lima, no en UTC', () => {
  assert.equal(formatoFechaResena('2026-11-13T18:20:00.123456Z'), '13 de noviembre de 2026')
  // 03:00 UTC del 14 son las 22:00 del 13 en Lima
  assert.equal(formatoFechaResena('2026-11-14T03:00:00Z'), '13 de noviembre de 2026')
  assert.equal(formatoFechaResena('2026-11-14T05:00:00Z'), '14 de noviembre de 2026')
})

test('hayMasPaginas: "Ver más" solo si queda otra página', () => {
  assert.equal(hayMasPaginas(0, 2), true)
  assert.equal(hayMasPaginas(1, 2), false)
  assert.equal(hayMasPaginas(0, 1), false)
  assert.equal(hayMasPaginas(0, 0), false)
})

test('unirPaginas: agrega la página siguiente sin repetir reseñas', () => {
  const primera = [{ id: 9 }, { id: 8 }]
  assert.deepEqual(unirPaginas(primera, [{ id: 7 }, { id: 6 }]).map((r) => r.id), [9, 8, 7, 6])
  // Llegó una reseña nueva: la 8 se corrió a la página siguiente
  assert.deepEqual(unirPaginas(primera, [{ id: 8 }, { id: 7 }]).map((r) => r.id), [9, 8, 7])
})
