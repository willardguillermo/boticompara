// Pruebas de la conversación guardada del asistente (H69): npm test
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { armarHistorial, MAX_HISTORIAL_CHAT } from './chat.js'
import {
  borrarConversacion,
  CLAVE_CHAT,
  conversacionVencida,
  guardarConversacion,
  leerConversacion,
  mensajesParaGuardar,
} from './chatGuardado.js'

const MINUTO = 60_000
const CIERRE = 1_000_000

/** sessionStorage de mentira */
function almacen() {
  const datos = new Map()
  return {
    getItem: (k) => (datos.has(k) ? datos.get(k) : null),
    setItem: (k, v) => datos.set(k, String(v)),
    removeItem: (k) => datos.delete(k),
    datos,
  }
}

const conversacion = [
  { id: 1, rol: 'usuario', texto: '¿A cuánto tengo el panadol?', estado: 'ok' },
  { id: 2, rol: 'asistente', texto: 'En tu botica: S/ 8.50.', estado: 'ok', productos: [], respaldo: false, ayuda: false },
]

test('vence a los 10 minutos de cerrado, no a los 9', () => {
  const s = almacen()
  guardarConversacion({ usuarioId: 7, mensajes: conversacion, cerradaEn: CIERRE }, s)
  assert.equal(leerConversacion(7, CIERRE + 9 * MINUTO, s).mensajes.length, 2)
  assert.equal(leerConversacion(7, CIERRE + 10 * MINUTO + 1, s), null)
  assert.equal(s.datos.has(CLAVE_CHAT), false, 'lo vencido se borra')
})

test('mientras el panel está abierto no vence', () => {
  assert.equal(conversacionVencida(null, CIERRE + 60 * MINUTO), false)
  const s = almacen()
  guardarConversacion({ usuarioId: 7, mensajes: conversacion, cerradaEn: null }, s)
  assert.notEqual(leerConversacion(7, CIERRE + 60 * MINUTO, s), null)
})

test('otro usuario no ve la conversación y se borra', () => {
  const s = almacen()
  guardarConversacion({ usuarioId: 7, mensajes: conversacion, cerradaEn: CIERRE }, s)
  assert.equal(leerConversacion(8, CIERRE, s), null)
  assert.equal(s.datos.has(CLAVE_CHAT), false)
  assert.equal(leerConversacion(7, CIERRE, s), null)
})

test('borrarConversacion la quita (cerrar sesión)', () => {
  const s = almacen()
  guardarConversacion({ usuarioId: 7, mensajes: conversacion, cerradaEn: CIERRE }, s)
  borrarConversacion(s)
  assert.equal(leerConversacion(7, CIERRE, s), null)
})

test('no se guardan los errores con números de ayuda, lo pendiente ni la respuesta de crisis', () => {
  const mensajes = [
    ...conversacion,
    { id: 3, rol: 'usuario', texto: 'hola', estado: 'fallido', error: 'Hiciste muchas preguntas seguidas. ... SAMU 106 ...' },
    { id: 4, rol: 'usuario', texto: 'quiero morir', estado: 'ok' },
    { id: 5, rol: 'asistente', texto: 'Llama al SAMU al 106...', estado: 'ok', productos: [], ayuda: true },
    { id: 6, rol: 'usuario', texto: '¿y el ibuprofeno?', estado: 'enviando' },
  ]
  const guardados = mensajesParaGuardar(mensajes)
  assert.deepEqual(
    guardados.map((m) => m.texto),
    ['¿A cuánto tengo el panadol?', 'En tu botica: S/ 8.50.'],
  )
  assert.ok(guardados.every((m) => m.error === undefined))
})

test('si sessionStorage falla, no se rompe', () => {
  const roto = {
    getItem: () => { throw new Error('bloqueado') },
    setItem: () => { throw new Error('bloqueado') },
    removeItem: () => { throw new Error('bloqueado') },
  }
  assert.doesNotThrow(() => guardarConversacion({ usuarioId: 7, mensajes: conversacion, cerradaEn: CIERRE }, roto))
  assert.equal(leerConversacion(7, CIERRE, roto), null)
  assert.equal(leerConversacion(7, CIERRE, null), null)
})

test('lo guardado dañado se descarta', () => {
  const s = almacen()
  s.setItem(CLAVE_CHAT, '{no es json')
  assert.equal(leerConversacion(7, CIERRE, s), null)
  assert.equal(s.datos.has(CLAVE_CHAT), false)
})

test('una conversación larga restaurada envía como máximo 10 mensajes de historial', () => {
  const larga = Array.from({ length: 30 }, (_, i) => ({
    rol: i % 2 ? 'asistente' : 'usuario',
    texto: `m${i}`,
    estado: 'ok',
    productos: [],
  }))
  const s = almacen()
  guardarConversacion({ usuarioId: 7, mensajes: larga, cerradaEn: CIERRE }, s)
  const historial = armarHistorial(leerConversacion(7, CIERRE, s).mensajes)
  assert.equal(historial.length, MAX_HISTORIAL_CHAT)
  assert.equal(historial.at(-1).texto, 'm29')
  assert.deepEqual(Object.keys(historial[0]).sort(), ['rol', 'texto'])
})
