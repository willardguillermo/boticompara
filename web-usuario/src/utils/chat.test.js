// Pruebas de las reglas del chat que no dependen del navegador.
// Se ejecutan con el ejecutor de pruebas de Node, sin dependencias: npm test
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { MENSAJE_INESPERADO } from '../services/ApiError.js'
import { armarHistorial, MENSAJE_GENERICO, mensajeDeError, unirPrecios } from './chat.js'

const NBSP = '\u00A0'

test('unirPrecios une "S/" y el monto con un espacio de no separación', () => {
  assert.equal(unirPrecios('En tu botica: S/ 2.50.'), `En tu botica: S/${NBSP}2.50.`)
})

test('unirPrecios cambia todos los precios del texto, también en listas', () => {
  const texto = 'Tienes:\n- Panadol: S/ 8.50\n- Advil: S/ 12.90'
  assert.equal(unirPrecios(texto), `Tienes:\n- Panadol: S/${NBSP}8.50\n- Advil: S/${NBSP}12.90`)
})

test('unirPrecios no toca otros espacios ni "S/" sin número', () => {
  assert.equal(unirPrecios('Precio en S/ por unidad'), 'Precio en S/ por unidad')
  assert.equal(unirPrecios('2 soles con 50'), '2 soles con 50')
  assert.equal(unirPrecios(''), '')
})

test('el historial que se envía conserva el texto original, sin el espacio de no separación', () => {
  const mensajes = [
    { rol: 'usuario', texto: '¿A cuánto tengo el panadol?', estado: 'ok' },
    { rol: 'asistente', texto: 'En tu botica: S/ 8.50.', estado: 'ok', productos: [] },
  ]
  const historial = armarHistorial(mensajes)
  assert.equal(historial[1].texto, 'En tu botica: S/ 8.50.')
  assert.ok(!historial[1].texto.includes(NBSP))
})

test('mensajeDeError muestra el mensaje del servidor en el 403 del chat', () => {
  const botica = 'El asistente se activa cuando tu botica sea aprobada'
  const correo = 'Verifica tu correo para usar el asistente'
  assert.equal(mensajeDeError({ status: 403, mensaje: botica }), botica)
  assert.equal(mensajeDeError({ status: 403, mensaje: correo }), correo)
})

test('mensajeDeError usa el genérico en un 403 sin mensaje del servidor', () => {
  assert.equal(mensajeDeError({ status: 403, mensaje: MENSAJE_INESPERADO }), MENSAJE_GENERICO)
  assert.equal(mensajeDeError({ status: 403 }), MENSAJE_GENERICO)
})

test('mensajeDeError sigue mostrando el mensaje del 429 y no el de otros códigos', () => {
  assert.equal(mensajeDeError({ status: 429, mensaje: 'Espera un minuto' }), 'Espera un minuto')
  assert.equal(mensajeDeError({ status: 400, mensaje: 'Escribe tu pregunta' }), MENSAJE_GENERICO)
})
