// Pruebas de la máquina de pasos del registro (H1, H20): npm test
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { cuerpoRegistro, ESTADO_INICIAL, numeroDePaso, pasoTrasError, puedeVolverA, registroReducer } from './registro.js'

const pasar = (estado, ...acciones) => acciones.reduce(registroReducer, estado)
const CORREO = 'nueva@botica.pe'
const enBotica = pasar(
  ESTADO_INICIAL,
  { tipo: 'codigoEnviado', correo: CORREO },
  { tipo: 'verificado', token: 'tok-1' },
  { tipo: 'cuentaLista' },
)

test('el camino completo: correo, código, cuenta, botica y licencia', () => {
  const estado = pasar(enBotica, { tipo: 'boticaLista' })
  assert.equal(estado.paso, 'licencia')
  assert.equal(estado.token, 'tok-1')
  assert.equal(estado.correo, CORREO)
  assert.deepEqual(['correo', 'codigo', 'cuenta', 'botica', 'licencia'].map(numeroDePaso), [1, 2, 3, 4, 5])
})

test('sin token no se puede pasar de la cuenta ni de la botica', () => {
  const sinToken = { ...ESTADO_INICIAL, paso: 'cuenta' }
  assert.equal(registroReducer(sinToken, { tipo: 'cuentaLista' }).paso, 'cuenta')
  assert.equal(registroReducer({ ...sinToken, paso: 'botica' }, { tipo: 'boticaLista' }).paso, 'botica')
  // "verificado" solo vale en el paso del código y con un token
  assert.equal(registroReducer(ESTADO_INICIAL, { tipo: 'verificado', token: 't' }).paso, 'correo')
  const enCodigo = registroReducer(ESTADO_INICIAL, { tipo: 'codigoEnviado', correo: CORREO })
  assert.equal(registroReducer(enCodigo, { tipo: 'verificado', token: '' }).paso, 'codigo')
})

test('cambiar el correo o pedir otro código olvida el token', () => {
  assert.equal(registroReducer(enBotica, { tipo: 'cambiarCorreo' }).token, null)
  assert.equal(registroReducer(enBotica, { tipo: 'cambiarCorreo' }).paso, 'correo')
  const otro = registroReducer(enBotica, { tipo: 'codigoEnviado', correo: 'otro@botica.pe' })
  assert.deepEqual([otro.paso, otro.token, otro.correo], ['codigo', null, 'otro@botica.pe'])
})

test('volver: solo a la cuenta o la botica ya hechas, nunca al correo ni al código', () => {
  const enLicencia = registroReducer(enBotica, { tipo: 'boticaLista' })
  assert.equal(puedeVolverA(enLicencia, 'cuenta'), true)
  assert.equal(puedeVolverA(enLicencia, 'botica'), true)
  assert.equal(puedeVolverA(enLicencia, 'codigo'), false)
  assert.equal(puedeVolverA(enLicencia, 'correo'), false)
  assert.equal(puedeVolverA(enBotica, 'licencia'), false)
  const vuelto = registroReducer(enLicencia, { tipo: 'volver', paso: 'cuenta' })
  assert.deepEqual([vuelto.paso, vuelto.token], ['cuenta', 'tok-1'])
  assert.equal(registroReducer(enLicencia, { tipo: 'volver', paso: 'codigo' }).paso, 'licencia')
})

test('un error del RUC vuelve a la botica conservando el token; uno del correo lo olvida', () => {
  const enLicencia = registroReducer(enBotica, { tipo: 'boticaLista' })
  const porRuc = registroReducer(enLicencia, { tipo: 'error', paso: 'botica', aviso: 'El RUC ya está registrado' })
  assert.deepEqual([porRuc.paso, porRuc.token, porRuc.aviso], ['botica', 'tok-1', 'El RUC ya está registrado'])
  const porCorreo = registroReducer(enLicencia, { tipo: 'error', paso: 'correo', aviso: 'venció' })
  assert.deepEqual([porCorreo.paso, porCorreo.token, porCorreo.correo], ['correo', null, CORREO])
})

test('pasoTrasError: a qué paso lleva cada error de POST /auth/registro', () => {
  const error = (status, mensaje) => ({ status, mensaje })
  assert.equal(pasoTrasError(error(400, 'Tu verificación del correo venció. Vuelve a pedir un código')), 'correo')
  assert.equal(pasoTrasError(error(409, 'El correo ya está registrado')), 'correo')
  assert.equal(pasoTrasError(error(409, 'El RUC ya está registrado')), 'botica')
  assert.equal(pasoTrasError(error(400, 'El RUC no es válido')), 'botica')
  assert.equal(
    pasoTrasError(error(400, 'Este RUC pertenece a una persona: la cuenta debe estar a nombre del titular del RUC')),
    'botica',
  )
  assert.equal(pasoTrasError(error(400, 'Las coordenadas están fuera de rango.')), 'botica')
  assert.equal(pasoTrasError(error(400, 'La contraseña debe tener al menos 8 caracteres')), null)
  assert.equal(pasoTrasError(error(503, 'No disponible')), null)
  assert.equal(pasoTrasError(error(0, 'Sin conexión')), null)
})

test('cuerpoRegistro: arma el cuerpo del dueño con el token, el correo verificado y la botica', () => {
  const botica = { nombreComercial: 'Botica Sol', ruc: '20601030013' }
  assert.deepEqual(
    cuerpoRegistro({
      token: 'tok-1',
      correo: ' Nueva@Botica.pe ',
      cuenta: { nombre: ' Ana Ruiz ', password: 'Clave2026!', telefono: ' ' },
      botica,
    }),
    {
      tokenVerificacion: 'tok-1', nombre: 'Ana Ruiz', correo: 'nueva@botica.pe', password: 'Clave2026!',
      telefono: null, rol: 'DUENO_BOTICA', botica,
    },
  )
})
