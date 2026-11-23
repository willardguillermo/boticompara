// Pruebas de las reglas del chat que no dependen del navegador.
// Se ejecutan con el ejecutor de pruebas de Node, sin dependencias: npm test
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { armarHistorial, unirPrecios } from './chat.js'

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
