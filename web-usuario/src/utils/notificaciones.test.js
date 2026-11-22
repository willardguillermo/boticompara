// Pruebas de las notificaciones del dueño (H103): npm test
import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  etiquetaCampana,
  iconoNotificacion,
  rutaPedido,
  subioContador,
  textoContador,
  tiempoRelativo,
  tituloPestana,
} from './notificaciones.js'

const AHORA = Date.parse('2026-11-22T15:30:00Z')
const hace = (ms) => tiempoRelativo(new Date(AHORA - ms).toISOString(), AHORA)
const MIN = 60 * 1000
const H = 60 * MIN
const D = 24 * H

test('tiempoRelativo: menos de un minuto o una fecha futura es "hace un momento"', () => {
  assert.equal(hace(0), 'hace un momento')
  assert.equal(hace(MIN - 1), 'hace un momento')
  assert.equal(hace(-5 * MIN), 'hace un momento')
})

test('tiempoRelativo: minutos y horas, con sus límites', () => {
  assert.equal(hace(MIN), 'hace 1 min')
  assert.equal(hace(5 * MIN + 59 * 1000), 'hace 5 min')
  assert.equal(hace(H - 1), 'hace 59 min')
  assert.equal(hace(H), 'hace 1 h')
  assert.equal(hace(D - 1), 'hace 23 h')
})

test('tiempoRelativo: días en singular y plural, y desde una semana la fecha de Lima', () => {
  assert.equal(hace(D), 'hace 1 día')
  assert.equal(hace(6 * D), 'hace 6 días')
  assert.equal(hace(7 * D - 1), 'hace 6 días')
  // 02:00 UTC del 12 es todavía el 11 en Lima
  assert.equal(tiempoRelativo('2026-11-12T02:00:00Z', AHORA), '11/11/2026')
})

test('tiempoRelativo acepta los microsegundos que manda la API', () => {
  assert.equal(tiempoRelativo('2026-11-22T15:25:00.123456Z', AHORA), 'hace 4 min')
})

test('textoContador: 0 no se muestra y más de 99 es "99+"', () => {
  assert.equal(textoContador(null), '')
  assert.equal(textoContador(0), '')
  assert.equal(textoContador(-1), '')
  assert.equal(textoContador(1), '1')
  assert.equal(textoContador(99), '99')
  assert.equal(textoContador(100), '99+')
})

test('etiquetaCampana dice el número real aunque se vea "99+"', () => {
  assert.equal(etiquetaCampana(null), 'Notificaciones')
  assert.equal(etiquetaCampana(0), 'Notificaciones, 0 sin leer')
  assert.equal(etiquetaCampana(150), 'Notificaciones, 150 sin leer')
})

test('tituloPestana pone el contador y vuelve al título de siempre con 0', () => {
  assert.equal(tituloPestana(3, 'Panel'), '(3) BotiCompara')
  assert.equal(tituloPestana(120, 'Panel'), '(99+) BotiCompara')
  assert.equal(tituloPestana(0, 'Panel'), 'Panel')
})

test('subioContador no avisa en la primera carga, ni si baja o queda igual', () => {
  assert.equal(subioContador(null, 5), false)
  assert.equal(subioContador(2, 2), false)
  assert.equal(subioContador(2, 1), false)
  assert.equal(subioContador(0, 1), true)
})

test('rutaPedido e iconoNotificacion', () => {
  assert.equal(rutaPedido({ pedidoId: 15 }), '/panel/pedidos?pedido=15')
  assert.equal(iconoNotificacion('NUEVA_RESERVA'), 'pedido')
  assert.equal(iconoNotificacion('OTRO_TIPO'), 'campana')
})
