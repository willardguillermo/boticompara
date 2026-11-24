// Pruebas de Mi cuenta (H16, H19, A7): npm test
import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  confirmacionValida,
  cuerpoPerfil,
  esErrorDeTitular,
  esRucDePersona,
  formularioDesdePerfil,
  hayCambios,
  mostrarAyudaTitular,
  validarPerfil,
} from './cuenta.js'

const valido = { nombre: 'Rosa Quispe', telefono: '987654321', direccion: 'Jr. Los Pinos 120' }

test('validarPerfil: datos correctos no tienen errores; teléfono y dirección son opcionales (H16)', () => {
  assert.deepEqual(validarPerfil(valido), {})
  assert.deepEqual(validarPerfil({ nombre: 'Rosa', telefono: '', direccion: '' }), {})
})

test('validarPerfil: el nombre es obligatorio y admite máximo 100 caracteres (H16)', () => {
  assert.equal(validarPerfil({ ...valido, nombre: '   ' }).nombre, 'Ingresa tu nombre.')
  assert.deepEqual(validarPerfil({ ...valido, nombre: 'a'.repeat(100) }), {})
  assert.match(validarPerfil({ ...valido, nombre: 'a'.repeat(101) }).nombre, /100 caracteres/)
  // Los espacios de los extremos no cuentan, como en el backend (trim)
  assert.deepEqual(validarPerfil({ ...valido, nombre: `  ${'a'.repeat(100)}  ` }), {})
})

test('validarPerfil: teléfono hasta 20 caracteres y dirección hasta 255 (H16)', () => {
  assert.deepEqual(validarPerfil({ ...valido, telefono: '1'.repeat(20), direccion: 'd'.repeat(255) }), {})
  assert.match(validarPerfil({ ...valido, telefono: '1'.repeat(21) }).telefono, /20 caracteres/)
  assert.match(validarPerfil({ ...valido, direccion: 'd'.repeat(256) }).direccion, /255 caracteres/)
})

test('cuerpoPerfil: no envía correo ni rol y manda null en lo vacío (H16)', () => {
  assert.deepEqual(cuerpoPerfil({ nombre: '  Rosa Quispe ', telefono: '  ', direccion: '' }), {
    nombre: 'Rosa Quispe', telefono: null, direccion: null,
  })
})

test('hayCambios: compara con lo guardado ignorando espacios y null', () => {
  const perfil = { nombre: 'Rosa Quispe', telefono: null, direccion: null, correo: 'x@y.z', rol: 'DUENO_BOTICA' }
  const form = formularioDesdePerfil(perfil)
  assert.equal(hayCambios(perfil, form), false)
  assert.equal(hayCambios(perfil, { ...form, nombre: 'Rosa Quispe  ' }), false)
  assert.equal(hayCambios(perfil, { ...form, telefono: '987654321' }), true)
})

test('confirmacionValida: solo ELIMINAR en mayúsculas habilita el botón (H19)', () => {
  assert.equal(confirmacionValida('ELIMINAR'), true)
  assert.equal(confirmacionValida('  ELIMINAR '), true)
  for (const texto of ['', 'eliminar', 'Eliminar', 'ELIMINA', 'ELIMINAR CUENTA', 'ELIMlNAR', null, undefined]) {
    assert.equal(confirmacionValida(texto), false, `"${texto}" no debe habilitar el botón`)
  }
})

test('esRucDePersona: solo RUC 10 y 15 de 11 dígitos (A7)', () => {
  assert.equal(esRucDePersona('10461234564'), true)
  assert.equal(esRucDePersona('15123456789'), true)
  assert.equal(esRucDePersona('20601234565'), false)
  assert.equal(esRucDePersona('17123456789'), false)
  assert.equal(esRucDePersona('1046123456'), false)
  assert.equal(esRucDePersona(null), false)
})

test('esErrorDeTitular: reconoce el 400 de A7 con el mensaje viejo y con el de S4-06', () => {
  const viejo = 'Este RUC pertenece a una persona: la cuenta debe estar a nombre del titular (según SUNAT: QUISPE MAMANI ROSA)'
  const nuevo = 'Este RUC pertenece a una persona: la cuenta debe estar a nombre del titular del RUC'
  assert.equal(esErrorDeTitular(viejo), true)
  assert.equal(esErrorDeTitular(nuevo), true)
  assert.equal(esErrorDeTitular('Este RUC pertenece a una persona'), true)
  assert.equal(esErrorDeTitular('El RUC ya está registrado'), false)
  // Solo el principio: la frase en medio de otro mensaje no cuenta
  assert.equal(esErrorDeTitular('Aviso: este RUC pertenece a una persona'), false)
  assert.equal(esErrorDeTitular(undefined), false)
})

test('mostrarAyudaTitular: botica RECHAZADO con RUC de persona (A7)', () => {
  assert.equal(mostrarAyudaTitular({ estado: 'RECHAZADO', ruc: '10461234564' }), true)
  assert.equal(mostrarAyudaTitular({ estado: 'RECHAZADO', ruc: '20601234565' }), false)
  assert.equal(mostrarAyudaTitular({ estado: 'APROBADO', ruc: '10461234564' }), false)
  assert.equal(mostrarAyudaTitular(null), false)
})
