// Pruebas de la espera para reenviar el código: npm test
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { formatoEspera, segundosDeEspera, segundosRestantes } from './cuentaRegresiva.js'

const AHORA = Date.parse('2026-11-26T15:00:00Z')

test('segundosDeEspera: usa el Retry-After en segundos', () => {
  assert.equal(segundosDeEspera('42'), 42)
  assert.equal(segundosDeEspera(' 3600 '), 3600)
  assert.equal(segundosDeEspera('0'), 1)
})

test('segundosDeEspera: acepta Retry-After como fecha HTTP', () => {
  assert.equal(segundosDeEspera('Thu, 26 Nov 2026 15:01:30 GMT', 60, AHORA), 90)
  assert.equal(segundosDeEspera('Thu, 26 Nov 2026 14:00:00 GMT', 60, AHORA), 1)
})

test('segundosDeEspera: sin header o ilegible, 60 s (o el valor por defecto)', () => {
  assert.equal(segundosDeEspera(null), 60)
  assert.equal(segundosDeEspera(undefined), 60)
  assert.equal(segundosDeEspera('pronto'), 60)
  assert.equal(segundosDeEspera('', 30), 30)
})

test('segundosRestantes: redondea hacia arriba y no baja de 0', () => {
  assert.equal(segundosRestantes(AHORA + 59_001, AHORA), 60)
  assert.equal(segundosRestantes(AHORA + 1_000, AHORA), 1)
  assert.equal(segundosRestantes(AHORA + 999, AHORA), 1)
  assert.equal(segundosRestantes(AHORA, AHORA), 0)
  assert.equal(segundosRestantes(AHORA - 5_000, AHORA), 0)
  assert.equal(segundosRestantes(null, AHORA), 0)
})

test('formatoEspera: minutos y segundos con dos cifras', () => {
  assert.equal(formatoEspera(60), '1:00')
  assert.equal(formatoEspera(75), '1:15')
  assert.equal(formatoEspera(5), '0:05')
  assert.equal(formatoEspera(0), '0:00')
  assert.equal(formatoEspera(-3), '0:00')
})
