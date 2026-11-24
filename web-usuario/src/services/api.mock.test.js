// Pruebas del modo simulado: npm test
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { soloRecientes, titularCoincide } from './api.mock.js'

const DIA = 24 * 60 * 60 * 1000
const AHORA = Date.parse('2026-11-22T15:30:00Z')
const hace = (ms) => ({ creadoEn: new Date(AHORA - ms).toISOString() })

test('soloRecientes: una notificación de hace 6 días se ve y una de hace 8 no (H103)', () => {
  const de6 = hace(6 * DIA)
  const de8 = hace(8 * DIA)
  assert.deepEqual(soloRecientes([de6, de8], AHORA), [de6])
})

test('soloRecientes: a los 7 días justos ya no se ve, como en el contrato (H103)', () => {
  const casi = hace(7 * DIA - 1)
  const justo = hace(7 * DIA)
  assert.deepEqual(soloRecientes([casi, justo], AHORA), [casi])
  assert.deepEqual(soloRecientes([], AHORA), [])
})

test('titularCoincide: cada palabra de la cuenta está en el titular, sin tildes, mayúsculas ni orden (A7)', () => {
  assert.equal(titularCoincide('Rosa Quispe', 'QUISPE MAMANI ROSA'), true)
  assert.equal(titularCoincide('josé  peña', 'PEÑA RAMIREZ JOSE'), true)
  assert.equal(titularCoincide('Jorge Mendoza', 'QUISPE MAMANI ROSA'), false)
  assert.equal(titularCoincide('Ros Quispe', 'QUISPE MAMANI ROSA'), false)
  assert.equal(titularCoincide('   ', 'QUISPE MAMANI ROSA'), false)
})
