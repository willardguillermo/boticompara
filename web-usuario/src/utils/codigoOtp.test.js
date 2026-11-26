// Pruebas de las casillas del código (CodigoOtp): npm test
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { borrar, casillas, codigoCompleto, escribir, textoNuevo, unirCasillas } from './codigoOtp.js'

test('casillas y unirCasillas: un espacio es una casilla vacía y se conserva en el medio', () => {
  assert.deepEqual(casillas('12 4'), ['1', '2', '', '4', '', ''])
  assert.deepEqual(casillas(''), ['', '', '', '', '', ''])
  assert.equal(unirCasillas(['1', '2', '', '4', '', '']), '12 4')
  assert.equal(unirCasillas(['', '', '', '', '', '']), '')
})

test('escribir: un dígito llena la casilla y el foco pasa a la siguiente', () => {
  assert.deepEqual(escribir('', 0, '4'), { valor: '4', foco: 1 })
  assert.deepEqual(escribir('48392', 5, '0'), { valor: '483920', foco: 5 })
  assert.deepEqual(escribir('1 3', 1, '2'), { valor: '123', foco: 2 })
})

test('escribir: pegar el código completo en cualquier casilla lo reparte desde la primera', () => {
  for (const indice of [0, 2, 5]) {
    assert.deepEqual(escribir('9', indice, '483920'), { valor: '483920', foco: 5 })
  }
  // Con espacios, guiones o texto alrededor (como llega copiado de un correo)
  assert.deepEqual(escribir('', 3, 'Tu código: 483 920.'), { valor: '483920', foco: 5 })
  // Más de 6 dígitos: se toman los 6 primeros
  assert.deepEqual(escribir('', 0, '12345678'), { valor: '123456', foco: 5 })
})

test('escribir: varios dígitos sin llegar a 6 se reparten desde la casilla actual', () => {
  assert.deepEqual(escribir('1', 1, '234'), { valor: '1234', foco: 4 })
  assert.deepEqual(escribir('', 4, '789'), { valor: '    78', foco: 5 })
})

test('escribir: sin dígitos vacía la casilla y no avanza', () => {
  assert.deepEqual(escribir('123', 1, ''), { valor: '1 3', foco: 1 })
  assert.deepEqual(escribir('123', 1, 'a'), { valor: '1 3', foco: 1 })
})

test('borrar: borra la casilla actual o, si ya estaba vacía, la anterior', () => {
  assert.deepEqual(borrar('123', 2), { valor: '12', foco: 2 })
  assert.deepEqual(borrar('12', 2), { valor: '1', foco: 1 })
  assert.deepEqual(borrar('', 0), { valor: '', foco: 0 })
  assert.deepEqual(borrar('123456', 2), { valor: '12 456', foco: 2 })
})

test('codigoCompleto: solo 6 dígitos sin huecos', () => {
  assert.equal(codigoCompleto('483920'), '483920')
  assert.equal(codigoCompleto('48392'), '')
  assert.equal(codigoCompleto('48 920'), '')
  assert.equal(codigoCompleto(undefined), '')
})

test('textoNuevo: en una casilla llena se toma el dígito junto al cursor', () => {
  assert.equal(textoNuevo('37', '3', 2), '7')
  assert.equal(textoNuevo('73', '3', 1), '7')
  assert.equal(textoNuevo('5', '', 1), '5')
  assert.equal(textoNuevo('483920', '4', 6), '483920')
})
