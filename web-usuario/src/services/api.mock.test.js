// Pruebas del modo simulado: npm test
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { soloRecientes, titularCoincide, vencerActivas } from './api.mock.js'

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

const reserva = (id, estado, venceEnMs) => ({ id, estado, venceEn: new Date(venceEnMs).toISOString() })

test('vencerActivas: solo las activas con venceEn pasado pasan a VENCIDA (H84, S4-01)', () => {
  const pendiente = reserva(1, 'PENDIENTE', AHORA - 1)
  const confirmada = reserva(2, 'CONFIRMADA', AHORA - DIA)
  const lista = reserva(3, 'LISTA', AHORA)
  const vigente = reserva(4, 'PENDIENTE', AHORA + 1)
  const entregada = reserva(5, 'ENTREGADA', AHORA - DIA)

  const vencidas = vencerActivas([pendiente, confirmada, lista, vigente, entregada], AHORA)

  assert.deepEqual(vencidas.map((r) => r.id), [1, 2, 3])
  assert.equal(pendiente.estado, 'VENCIDA')
  assert.equal(lista.actualizadoEn, new Date(AHORA).toISOString())
  assert.equal(vigente.estado, 'PENDIENTE')
  assert.equal(entregada.estado, 'ENTREGADA')
})

test('vencerActivas: una reserva ya VENCIDA no se vuelve a contar (H84)', () => {
  const ya = reserva(1, 'VENCIDA', AHORA - DIA)
  assert.deepEqual(vencerActivas([ya], AHORA), [])
})

