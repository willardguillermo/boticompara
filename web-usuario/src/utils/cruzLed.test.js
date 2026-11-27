// Pruebas de la cruz LED: npm test
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { puntosCruz } from './cruzLed.js'

test('puntosCruz: 9x9 con brazos de 3 da 45 puntos, como el "+" del logo', () => {
  const puntos = puntosCruz()
  assert.equal(puntos.length, 45)
  // Las esquinas no son parte de la cruz; el centro y las puntas de los brazos sí
  const hay = (c, f) => puntos.some((p) => p.columna === c && p.fila === f)
  assert.equal(hay(0, 0), false)
  assert.equal(hay(8, 8), false)
  assert.equal(hay(4, 4), true)
  assert.equal(hay(4, 0), true)
  assert.equal(hay(0, 4), true)
})

test('puntosCruz: la onda va del centro (d = 0) a las puntas de los brazos (d = 4)', () => {
  const puntos = puntosCruz()
  assert.deepEqual(puntos.find((p) => p.columna === 4 && p.fila === 4).d, 0)
  assert.equal(puntos.find((p) => p.columna === 4 && p.fila === 0).d, 4)
  assert.equal(Math.max(...puntos.map((p) => p.d)), 4)
  // Cada anillo tiene puntos, así que la onda no se salta ninguno
  assert.deepEqual([...new Set(puntos.map((p) => p.d))].sort(), [0, 1, 2, 3, 4])
})

test('puntosCruz: es simétrica', () => {
  const clave = (p) => `${p.columna},${p.fila}`
  const conjunto = new Set(puntosCruz().map(clave))
  for (const p of puntosCruz()) {
    assert.ok(conjunto.has(`${8 - p.columna},${p.fila}`))
    assert.ok(conjunto.has(`${p.fila},${p.columna}`))
  }
  assert.equal(puntosCruz(5, 1).length, 9)
})
