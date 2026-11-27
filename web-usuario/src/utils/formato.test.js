// Pruebas de los formatos de texto: npm test
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { conPuntoFinal } from './formato.js'

test('conPuntoFinal: agrega el punto si el mensaje del servidor no lo trae', () => {
  assert.equal(conPuntoFinal('El almacenamiento de archivos no está configurado'), 'El almacenamiento de archivos no está configurado.')
  assert.equal(conPuntoFinal('  Sin conexión  '), 'Sin conexión.')
})

test('conPuntoFinal: no duplica el cierre de una frase que ya lo tiene', () => {
  assert.equal(conPuntoFinal('No se pudo subir la licencia.'), 'No se pudo subir la licencia.')
  assert.equal(conPuntoFinal('¡Listo!'), '¡Listo!')
  assert.equal(conPuntoFinal('¿Reintentar?'), '¿Reintentar?')
  assert.equal(conPuntoFinal('Espera…'), 'Espera…')
  assert.equal(conPuntoFinal('Termina en punto. '), 'Termina en punto.')
})

test('conPuntoFinal: un mensaje vacío sigue vacío', () => {
  assert.equal(conPuntoFinal(''), '')
  assert.equal(conPuntoFinal('   '), '')
  assert.equal(conPuntoFinal(null), '')
  assert.equal(conPuntoFinal(undefined), '')
})
