// Pruebas de la lógica de la landing: npm test
import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  ahorroDeEjemplo,
  calcularAhorro,
  EJEMPLOS_DEMO,
  proporcionPrecio,
  seccionActiva,
  siguienteIndice,
} from './landing.js'

test('ahorro de los ejemplos de la demo, sin errores de coma flotante', () => {
  const [amoxicilina, paracetamol] = EJEMPLOS_DEMO
  assert.deepEqual(ahorroDeEjemplo(amoxicilina), { ahorro: 15.5, porcentaje: 65, masBarato: 'b' })
  assert.deepEqual(ahorroDeEjemplo(paracetamol), { ahorro: 0.3, porcentaje: 12, masBarato: 'b' })
})

test('los ejemplos usan los precios de la demo y la botica cara va primero', () => {
  const precios = EJEMPLOS_DEMO.map((e) => [e.caro.botica, e.caro.precio, e.barato.botica, e.barato.precio])
  assert.deepEqual(precios, [
    ['Botica Salud Total', 24, 'FarmaVida', 8.5],
    ['Botica Salud Total', 2.5, 'FarmaVida', 2.2],
  ])
  for (const e of EJEMPLOS_DEMO) assert.ok(e.caro.precio > e.barato.precio)
})

test('calcularAhorro no depende del orden', () => {
  assert.deepEqual(calcularAhorro(8.5, 24), { ahorro: 15.5, porcentaje: 65, masBarato: 'a' })
  assert.equal(calcularAhorro(0.1 + 0.2, 0.3).ahorro, 0)
})

test('calcularAhorro con precios iguales o en cero', () => {
  assert.deepEqual(calcularAhorro(5, 5), { ahorro: 0, porcentaje: 0, masBarato: null })
  assert.deepEqual(calcularAhorro(0, 0), { ahorro: 0, porcentaje: 0, masBarato: null })
  assert.deepEqual(calcularAhorro(0, 4), { ahorro: 4, porcentaje: 100, masBarato: 'a' })
})

test('calcularAhorro rechaza precios inválidos', () => {
  for (const malo of [-1, Number.NaN, Infinity, '8.50', null, undefined]) {
    assert.equal(calcularAhorro(malo, 2), null)
    assert.equal(calcularAhorro(2, malo), null)
  }
})

test('proporcionPrecio para las barras del comparador', () => {
  assert.equal(proporcionPrecio(8.5, 24), 35)
  assert.equal(proporcionPrecio(24, 24), 100)
  assert.equal(proporcionPrecio(30, 24), 100)
  assert.equal(proporcionPrecio(3, 0), 0)
  assert.equal(proporcionPrecio(-1, 24), 0)
})

test('siguienteIndice da la vuelta', () => {
  assert.equal(siguienteIndice(0, 2), 1)
  assert.equal(siguienteIndice(1, 2), 0)
  assert.equal(siguienteIndice(5, 1), 0)
  assert.equal(siguienteIndice(0, 0), 0)
})

test('seccionActiva: ninguna en el hero, luego la última que pasó la línea', () => {
  const medidas = (tops) => tops.map((top, i) => ({ id: `s${i}`, top }))
  assert.equal(seccionActiva(medidas([600, 1400, 2200]), 300), null)
  assert.equal(seccionActiva(medidas([300, 1100, 1900]), 300), 's0')
  assert.equal(seccionActiva(medidas([-900, 120, 900]), 300), 's1')
  assert.equal(seccionActiva(medidas([-2000, -1200, -300]), 300), 's2')
})

test('seccionActiva: al final de la página manda la última; sin secciones, null', () => {
  assert.equal(seccionActiva([{ id: 'a', top: -500 }, { id: 'b', top: 500 }], 300, true), 'b')
  assert.equal(seccionActiva([], 300), null)
  assert.equal(seccionActiva(undefined, 300), null)
})
