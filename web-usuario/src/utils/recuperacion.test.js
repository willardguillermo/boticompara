// Pruebas de la recuperación de contraseña (H17): npm test
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { codigoValido, limpiarCodigo, validarCorreoRecuperacion, validarRestablecer } from './recuperacion.js'

const validos = { codigo: '123456', password: 'NuevaClave1', confirmacion: 'NuevaClave1' }

test('codigoValido: solo 6 dígitos exactos', () => {
  assert.equal(codigoValido('123456'), true)
  assert.equal(codigoValido('000000'), true)
  assert.equal(codigoValido('12345'), false)
  assert.equal(codigoValido('1234567'), false)
  assert.equal(codigoValido('12a456'), false)
  assert.equal(codigoValido(' 123456'), false)
  assert.equal(codigoValido(''), false)
  assert.equal(codigoValido(undefined), false)
})

test('limpiarCodigo: quita lo que no es número y corta en 6', () => {
  assert.equal(limpiarCodigo('12 34-56'), '123456')
  assert.equal(limpiarCodigo('abc'), '')
  assert.equal(limpiarCodigo('12345678'), '123456')
  assert.equal(limpiarCodigo(null), '')
})

test('validarRestablecer: sin errores cuando todo es válido', () => {
  assert.deepEqual(validarRestablecer(validos), {})
})

test('validarRestablecer: el código debe tener 6 dígitos', () => {
  assert.ok(validarRestablecer({ ...validos, codigo: '' }).codigo)
  assert.ok(validarRestablecer({ ...validos, codigo: '12345' }).codigo)
  assert.ok(validarRestablecer({ ...validos, codigo: '12345a' }).codigo)
})

test('validarRestablecer: la contraseña debe tener al menos 8 caracteres', () => {
  const corta = validarRestablecer({ ...validos, password: '1234567', confirmacion: '1234567' })
  assert.ok(corta.password)
  assert.equal(corta.confirmacion, undefined)
  assert.deepEqual(validarRestablecer({ ...validos, password: '12345678', confirmacion: '12345678' }), {})
  assert.ok(validarRestablecer({ ...validos, password: '' }).password)
})

test('validarRestablecer: las contraseñas deben coincidir', () => {
  assert.equal(validarRestablecer({ ...validos, confirmacion: 'NuevaClave2' }).confirmacion, 'Las contraseñas no coinciden.')
  assert.ok(validarRestablecer({ ...validos, confirmacion: '' }).confirmacion)
})

test('validarCorreoRecuperacion: exige un correo con formato válido', () => {
  assert.equal(validarCorreoRecuperacion('rosa@boticompara.nissidev.dev'), '')
  assert.equal(validarCorreoRecuperacion('  rosa@boticompara.nissidev.dev '), '')
  assert.ok(validarCorreoRecuperacion(''))
  assert.ok(validarCorreoRecuperacion('rosa@'))
})
