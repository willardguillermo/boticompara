// Pruebas del modo simulado: npm test
import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  eliminarCuentaEnDatos,
  MOTIVO_BOTICA_ELIMINADA,
  MOTIVO_PEDIDOS_BOTICA_ELIMINADA,
  soloRecientes,
  titularCoincide,
  vencerActivas,
} from './api.mock.js'

const DIA = 24 * 60 * 60 * 1000
const AHORA = Date.parse('2026-11-22T15:30:00Z')
const hace = (ms) => ({ creadoEn: new Date(AHORA - ms).toISOString() })

test('soloRecientes: una notificación de hace 6 días se ve y una de hace 8 no (H103)', () => {
  const de6 = hace(6 * DIA)
  const de8 = hace(8 * DIA)
  assert.deepEqual(soloRecientes([de6, de8], AHORA), [de6])
})

test('soloRecientes: a los 7 días justos ya no se ve, como en el contrato (H103)', () => {
  const casi = hace(7 * DIA - 1)
  const justo = hace(7 * DIA)
  assert.deepEqual(soloRecientes([casi, justo], AHORA), [casi])
  assert.deepEqual(soloRecientes([], AHORA), [])
})

test('titularCoincide: cada palabra de la cuenta está en el titular, sin tildes, mayúsculas ni orden (A7)', () => {
  assert.equal(titularCoincide('Rosa Quispe', 'QUISPE MAMANI ROSA'), true)
  assert.equal(titularCoincide('josé  peña', 'PEÑA RAMIREZ JOSE'), true)
  assert.equal(titularCoincide('Jorge Mendoza', 'QUISPE MAMANI ROSA'), false)
  assert.equal(titularCoincide('Ros Quispe', 'QUISPE MAMANI ROSA'), false)
  assert.equal(titularCoincide('   ', 'QUISPE MAMANI ROSA'), false)
})

// H19: datos propios para no depender de los de ejemplo
function datosDePrueba(estadoBotica = 'APROBADO') {
  const estados = ['PENDIENTE', 'CONFIRMADA', 'LISTA', 'ENTREGADA', 'RECHAZADA', 'CANCELADA', 'VENCIDA']
  return {
    usuarios: [
      { id: 1, nombre: 'Rosa', correo: 'rosa@prueba.pe', rol: 'DUENO_BOTICA' },
      { id: 4, nombre: 'Carlos', correo: 'carlos@prueba.pe', rol: 'COMPRADOR' },
    ],
    boticas: [{ id: 1, usuarioId: 1, estado: estadoBotica, motivoRechazo: estadoBotica === 'RECHAZADO' ? 'Otro' : null }],
    productos: [{ id: 10, boticaId: 1, activo: true }, { id: 20, boticaId: 2, activo: true }],
    reservas: [
      ...estados.map((estado, i) => ({ id: i + 1, productoId: 10, usuarioId: 4, estado, motivoRechazo: null })),
      { id: 50, productoId: 20, usuarioId: 4, estado: 'PENDIENTE', motivoRechazo: null },
      { id: 60, productoId: 20, usuarioId: 5, estado: 'PENDIENTE', motivoRechazo: null },
    ],
  }
}
const estadoDe = (datos, id) => datos.reservas.find((r) => r.id === id).estado

test('eliminarCuentaEnDatos: el dueño rechaza su botica y sus pedidos activos, sin tocar los terminados (H19, S4-02)', () => {
  const datos = datosDePrueba()
  const avisos = []
  eliminarCuentaEnDatos(datos, datos.usuarios[0], { avisar: (tipo, r) => avisos.push([tipo, r.id]), ahora: 'T' })

  assert.equal(datos.usuarios[0].nombre, 'Usuario eliminado')
  assert.deepEqual(datos.boticas[0], { id: 1, usuarioId: 1, estado: 'RECHAZADO', motivoRechazo: MOTIVO_BOTICA_ELIMINADA })
  assert.equal(datos.productos[0].activo, false)
  assert.equal(datos.productos[1].activo, true)
  for (const id of [1, 2, 3]) {
    assert.equal(estadoDe(datos, id), 'RECHAZADA')
    assert.equal(datos.reservas.find((r) => r.id === id).motivoRechazo, MOTIVO_PEDIDOS_BOTICA_ELIMINADA)
  }
  assert.deepEqual([4, 5, 6, 7].map((id) => estadoDe(datos, id)), ['ENTREGADA', 'RECHAZADA', 'CANCELADA', 'VENCIDA'])
  // Los pedidos de otra botica no cambian, y al dueño no le llega ningún aviso
  assert.equal(estadoDe(datos, 50), 'PENDIENTE')
  assert.deepEqual(avisos, [])
})

test('eliminarCuentaEnDatos: una botica ya rechazada conserva su motivo (H19)', () => {
  const datos = datosDePrueba('RECHAZADO')
  eliminarCuentaEnDatos(datos, datos.usuarios[0])
  assert.deepEqual([datos.boticas[0].estado, datos.boticas[0].motivoRechazo], ['RECHAZADO', 'Otro'])
})

test('eliminarCuentaEnDatos: el comprador cancela sus reservas activas y avisa a cada dueño (H19, H103)', () => {
  const datos = datosDePrueba()
  const avisos = []
  eliminarCuentaEnDatos(datos, datos.usuarios[1], { avisar: (tipo, r) => avisos.push([tipo, r.id]) })

  assert.deepEqual([1, 2, 3, 50].map((id) => estadoDe(datos, id)), ['CANCELADA', 'CANCELADA', 'CANCELADA', 'CANCELADA'])
  assert.deepEqual(avisos, [1, 2, 3, 50].map((id) => ['RESERVA_CANCELADA', id]))
  // La reserva de otro comprador y la botica no cambian
  assert.equal(estadoDe(datos, 60), 'PENDIENTE')
  assert.equal(datos.boticas[0].estado, 'APROBADO')
})

const reserva = (id, estado, venceEnMs) => ({ id, estado, venceEn: new Date(venceEnMs).toISOString() })

test('vencerActivas: solo las activas con venceEn pasado pasan a VENCIDA (H84, S4-01)', () => {
  const pendiente = reserva(1, 'PENDIENTE', AHORA - 1)
  const confirmada = reserva(2, 'CONFIRMADA', AHORA - DIA)
  const lista = reserva(3, 'LISTA', AHORA)
  const vigente = reserva(4, 'PENDIENTE', AHORA + 1)
  const entregada = reserva(5, 'ENTREGADA', AHORA - DIA)

  const vencidas = vencerActivas([pendiente, confirmada, lista, vigente, entregada], AHORA)

  assert.deepEqual(vencidas.map((r) => r.id), [1, 2, 3])
  assert.equal(pendiente.estado, 'VENCIDA')
  assert.equal(lista.actualizadoEn, new Date(AHORA).toISOString())
  assert.equal(vigente.estado, 'PENDIENTE')
  assert.equal(entregada.estado, 'ENTREGADA')
})

test('vencerActivas: una reserva ya VENCIDA no se vuelve a contar (H84)', () => {
  const ya = reserva(1, 'VENCIDA', AHORA - DIA)
  assert.deepEqual(vencerActivas([ya], AHORA), [])
})
