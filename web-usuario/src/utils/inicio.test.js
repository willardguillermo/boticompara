// Pruebas del Inicio del dueño (H103): npm test
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { esHoyEnLima, porRecogerHoy, primerNombre, saludo } from './inicio.js'

// Lima es UTC-5 todo el año
const lima = (hora) => new Date(`2026-11-22T${hora}-05:00`)

test('saludo según la hora de Lima, con sus límites', () => {
  assert.equal(saludo(lima('04:59:59')), 'Buenas noches')
  assert.equal(saludo(lima('05:00:00')), 'Buenos días')
  assert.equal(saludo(lima('11:59:59')), 'Buenos días')
  assert.equal(saludo(lima('12:00:00')), 'Buenas tardes')
  assert.equal(saludo(lima('18:59:59')), 'Buenas tardes')
  assert.equal(saludo(lima('19:00:00')), 'Buenas noches')
  assert.equal(saludo(lima('00:00:00')), 'Buenas noches')
})

test('primerNombre', () => {
  assert.equal(primerNombre('Rosa Quispe'), 'Rosa')
  assert.equal(primerNombre('  Ana   María Flores '), 'Ana')
  assert.equal(primerNombre(null), '')
})

test('esHoyEnLima usa el día de Lima, no el de UTC', () => {
  const ahora = lima('20:00:00') // en UTC ya es el 23
  assert.equal(esHoyEnLima('2026-11-23T04:59:59Z', ahora), true) // 23:59:59 del 22 en Lima
  assert.equal(esHoyEnLima('2026-11-23T05:00:00Z', ahora), false) // 00:00 del 23 en Lima
  assert.equal(esHoyEnLima('2026-11-22T04:59:59Z', ahora), false) // 23:59:59 del 21 en Lima
})

test('porRecogerHoy cuenta solo los que vencen hoy', () => {
  const pedidos = [{ venceEn: '2026-11-22T23:00:00Z' }, { venceEn: '2026-11-24T12:00:00Z' }]
  assert.equal(porRecogerHoy(pedidos, lima('09:00:00')), 1)
  assert.equal(porRecogerHoy([], lima('09:00:00')), 0)
})
