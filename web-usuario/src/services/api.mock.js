// Implementación simulada en memoria: mismo contrato que docs/API.md,
// con los usuarios, boticas y productos de prueba de database/seed.sql.
// Los datos se reinician al recargar la página.
import { validarRuc } from '../utils/ruc.js'
import { MAX_BYTES_CARGA, validarArchivoLicencia } from '../utils/validacion.js'
import { crearError } from './ApiError.js'
import { obtenerToken } from './sesion.js'

const PASSWORD_PRUEBA = 'Boti2026!'
// H17: textos iguales a los del backend (docs/API.md)
const CODIGO_RECUPERACION_PRUEBA = '123456'
const MENSAJE_RECUPERAR = 'Si el correo está registrado, te enviamos las instrucciones para restablecer tu contraseña'
const MENSAJE_RECUPERAR_429 = 'Hiciste demasiadas solicitudes desde esta conexión. Intenta más tarde'
const MENSAJE_CODIGO_INVALIDO = 'El código no es válido o venció. Pide uno nuevo'
const MAX_RECUPERAR_HORA = 5
let recuperaciones = []
const STOCK_MINIMO_DEFECTO = 5 // H12: igual que el default de la columna stock_minimo
// H2: únicos RUC que el modo simulado "encuentra" en SUNAT
const RUCS_SUNAT = {
  20601030013: {
    ruc: '20601030013', razonSocial: 'REXTIE S.A.C.', estado: 'ACTIVO', condicion: 'HABIDO',
    // Como la manda SUNAT: en mayúsculas y con un espacio al final
    direccion: 'CAL. LAS CAMELIAS NRO 256 INT. 701A URB. JARDIN ', distrito: 'SAN ISIDRO',
  },
  // A7: RUC de persona natural; su titular es Rosa Quispe (la cuenta rosa@ coincide, otra cuenta no)
  10461234564: {
    ruc: '10461234564', razonSocial: 'QUISPE MAMANI ROSA', estado: 'ACTIVO', condicion: 'HABIDO',
    direccion: 'JR. LOS PINOS NRO 120 ', distrito: 'LURIGANCHO',
  },
  // H7: Botica Nueva Era, con una dirección fiscal distinta a la de su local
  20712345676: {
    ruc: '20712345676', razonSocial: 'NUEVA ERA SALUD S.A.C.', estado: 'ACTIVO', condicion: 'HABIDO',
    direccion: 'AV. JAVIER PRADO ESTE NRO 4200 ', distrito: 'LA MOLINA',
  },
}

// H31: catálogo maestro con los mismos ID que la BD (database/seed_v3_catalogo.sql, TEST-001 a TEST-012)
// H72: condición de venta como en database/datos_prueba_receta.sql
const MEDICAMENTOS = [
  [2, 'Panadol', 'Paracetamol', 'Caja x 100 tabletas', '500 mg', 'SIN_RECETA'],
  [11, 'Paracetamol Genérico', 'Paracetamol', 'Tableta', '500 mg', 'SIN_RECETA'],
  [12, 'Advil', 'Ibuprofeno', 'Tableta', '400 mg', 'SIN_RECETA'],
  [23, 'Ibuprofeno Genérico', 'Ibuprofeno', 'Tableta', '400 mg', 'SIN_RECETA'],
  [24, 'Amoxil', 'Amoxicilina', 'Caja x 100 cápsulas', '500 mg', 'CON_RECETA'],
  [25, 'Amoxicilina Genérica', 'Amoxicilina', 'Cápsula', '500 mg', 'CON_RECETA'],
  [26, 'Cozaar', 'Losartán', 'Tableta', '50 mg', 'CON_RECETA'],
  [27, 'Losartán Genérico', 'Losartán', 'Tableta', '50 mg', 'CON_RECETA'],
  [28, 'Omeprazol Genérico', 'Omeprazol', 'Cápsula', '20 mg', 'CON_RECETA'],
  [29, 'Nexium', 'Esomeprazol', 'Caja x 14 tabletas', '40 mg', 'CON_RECETA'],
  [30, 'Aspirina Genérica', 'Ácido Acetilsalicílico', 'Tableta', '100 mg', 'SIN_RECETA'],
  [31, 'Loratadina Genérica', 'Loratadina', 'Tableta', '10 mg', 'SIN_RECETA'],
].map(([id, nombreComercial, principioActivo, presentacion, concentracion, condicionVenta]) => ({
  id, nombreComercial, principioActivo, presentacion, concentracion, condicionVenta, oculto: false,
}))

const CONDICIONES_VENTA = ['SIN_RECETA', 'CON_RECETA', 'RECETA_RETENIDA']

const db = {
  medicamentos: MEDICAMENTOS,
  usuarios: [
    { id: 1, nombre: 'Rosa Quispe', correo: 'rosa@boticompara.nissidev.dev', password: PASSWORD_PRUEBA, telefono: '987654321', direccion: null, rol: 'DUENO_BOTICA' },
    { id: 2, nombre: 'Jorge Mendoza', correo: 'jorge@boticompara.nissidev.dev', password: PASSWORD_PRUEBA, telefono: '976543210', direccion: null, rol: 'DUENO_BOTICA' },
    { id: 3, nombre: 'Lucía Torres', correo: 'lucia@boticompara.nissidev.dev', password: PASSWORD_PRUEBA, telefono: '965432109', direccion: null, rol: 'DUENO_BOTICA' },
    { id: 4, nombre: 'Carlos Ramírez', correo: 'carlos@boticompara.nissidev.dev', password: PASSWORD_PRUEBA, telefono: '954321098', direccion: 'Av. Lima 123, Chosica', rol: 'COMPRADOR' },
    // Compradores que solo existen en el modo simulado, para variar los pedidos (H77)
    { id: 5, nombre: 'Ana María Flores', correo: 'ana@ejemplo.test', password: PASSWORD_PRUEBA, telefono: null, direccion: null, rol: 'COMPRADOR' },
    // H19: cuenta eliminada (datos anonimizados)
    { id: 6, nombre: 'Usuario eliminado', correo: 'eliminado-6@boticompara.invalid', password: null, telefono: null, direccion: null, rol: 'COMPRADOR', eliminado: true },
    // H87: más compradores con reseña en Salud Total, para que la lista tenga 2 páginas y se vea "Ver más"
    ...['Miguel Ángel Paredes', 'Sofía Huamán', 'Luis Castillo', 'Patricia Rojas', 'Diego', 'Elena Vargas',
      'Jorge Luis Chávez', 'Carmen Salazar', 'Ricardo Ñique', 'Valeria Mendoza', 'Pedro Gómez'].map((nombre, i) => ({
      id: 7 + i, nombre, correo: `comprador${7 + i}@ejemplo.test`, password: PASSWORD_PRUEBA,
      telefono: null, direccion: null, rol: 'COMPRADOR',
    })),
  ],
  boticas: [
    { id: 1, usuarioId: 1, nombreComercial: 'Botica Salud Total', ruc: '20601234565', razonSocial: 'Salud Total Farma S.A.C.', direccion: 'Av. Lima Sur 450', distrito: 'Lurigancho-Chosica', telefono: '014567890', latitud: -11.942500, longitud: -76.699000, tieneLicencia: true, estado: 'APROBADO', motivoRechazo: null },
    { id: 2, usuarioId: 2, nombreComercial: 'FarmaVida', ruc: '20549871233', razonSocial: 'FarmaVida Perú E.I.R.L.', direccion: 'Jr. Trujillo 210', distrito: 'Lurigancho-Chosica', telefono: '014561234', latitud: -11.935100, longitud: -76.694300, tieneLicencia: true, estado: 'APROBADO', motivoRechazo: null },
    // Como en database/seed.sql: Nueva Era (lucia@) rechazada, para probar la corrección y el reenvío (H7)
    { id: 3, usuarioId: 3, nombreComercial: 'Botica Nueva Era', ruc: '20712345676', razonSocial: 'Nueva Era Salud S.A.C.', direccion: 'Av. Nicolás Ayllón 980', distrito: 'Ate', telefono: '013459876', latitud: null, longitud: null, tieneLicencia: false, estado: 'RECHAZADO',
      motivoRechazo: 'La dirección no coincide con la ficha RUC. Corrígela y vuelve a enviar.' },
  ],
  productos: [
    [1, 'Panadol', 'Paracetamol', 'Tableta 500 mg x 10', 8.5, 40],
    [1, 'Paracetamol Genérico', 'Paracetamol', 'Tableta 500 mg x 10', 2.5, 120],
    [1, 'Advil', 'Ibuprofeno', 'Tableta 400 mg x 10', 12.9, 25],
    [1, 'Ibuprofeno Genérico', 'Ibuprofeno', 'Tableta 400 mg x 10', 3.8, 80],
    [1, 'Amoxil', 'Amoxicilina', 'Cápsula 500 mg x 12', 24.0, 15],
    [1, 'Omeprazol Genérico', 'Omeprazol', 'Cápsula 20 mg x 14', 4.2, 60],
    [1, 'Losartán Genérico', 'Losartán', 'Tableta 50 mg x 30', 9.9, 30],
    [2, 'Panadol', 'Paracetamol', 'Tableta 500 mg x 10', 7.9, 35],
    [2, 'Paracetamol Genérico', 'Paracetamol', 'Tableta 500 mg x 10', 2.2, 90],
    [2, 'Amoxicilina Genérica', 'Amoxicilina', 'Cápsula 500 mg x 12', 8.5, 50],
    [2, 'Nexium', 'Esomeprazol', 'Tableta 40 mg x 14', 58.0, 10],
    [2, 'Omeprazol Genérico', 'Omeprazol', 'Cápsula 20 mg x 14', 3.9, 70],
    [2, 'Cozaar', 'Losartán', 'Tableta 50 mg x 30', 45.5, 12],
    [2, 'Ibuprofeno Genérico', 'Ibuprofeno', 'Tableta 400 mg x 10', 3.5, 0],
  ].map(([boticaId, nombreComercial, principioActivo, presentacion, precio, stock], i) => ({
    id: i + 1, boticaId, nombreComercial, principioActivo, presentacion, precio, stock,
    stockMinimo: STOCK_MINIMO_DEFECTO, activo: true,
    // Enlazado por nombre comercial, como el paso 5 de seed_v3_catalogo.sql
    medicamentoId: MEDICAMENTOS.find((m) => m.nombreComercial === nombreComercial)?.id ?? null,
  })),
}

// ---------- Reservas de prueba (secciones 9 y 10) ----------
// Fechas relativas a cuando se abre la página, para que los plazos se vean reales.
const MINUTO = 60 * 1000
const HORA = 60 * MINUTO
const haceMs = (ms) => new Date(Date.now() - ms).toISOString()
const enMs = (ms) => new Date(Date.now() + ms).toISOString()
/** Cuánto falta para las 23:59:59 de hoy en Lima (UTC-5 todo el año). */
const hastaFinDelDiaLima = () => {
  const DIA = 24 * HORA
  return DIA - 1000 - ((Date.now() - 5 * HORA) % DIA)
}

/** [productoId, compradorId, cantidad, estado, creada hace, vence en (negativo = ya pasó), código, motivo] */
db.reservas = [
  // Botica Salud Total (rosa@): un pedido en cada uno de los 7 estados
  [2, 4, 2, 'PENDIENTE', 25 * MINUTO, 23 * HORA + 35 * MINUTO, 'BC-7K2QXM'],
  [5, 5, 3, 'PENDIENTE', 3 * HORA, 21 * HORA, 'BC-H4NP2T'],
  [3, 4, 1, 'CONFIRMADA', 6 * HORA, 18 * HORA, 'BC-R8WZ3D'],
  [7, 5, 2, 'LISTA', 20 * HORA, 40 * HORA, 'BC-M5VC9E'],
  [1, 4, 1, 'ENTREGADA', 3 * 24 * HORA, -2 * 24 * HORA, 'BC-Q2LX7A'],
  [6, 5, 2, 'RECHAZADA', 2 * 24 * HORA, -24 * HORA, 'BC-T6YB4K', 'No nos queda stock de esa presentación'],
  [4, 6, 4, 'CANCELADA', 4 * 24 * HORA, -3 * 24 * HORA, 'BC-W3FJ8N'],
  [2, 4, 1, 'VENCIDA', 5 * 24 * HORA, -4 * 24 * HORA, 'BC-Z9KD5P'],
  // FarmaVida (jorge@). El de Ibuprofeno (stock 0) no alcanza: "Confirmar" sale desactivado.
  [9, 4, 2, 'PENDIENTE', 50 * MINUTO, 23 * HORA + 10 * MINUTO, 'BC-C7HS2M'],
  [11, 5, 1, 'CONFIRMADA', 5 * HORA, 19 * HORA, 'BC-J4RT6V'],
  [14, 4, 2, 'PENDIENTE', 2 * HORA, 22 * HORA, 'BC-P3GU7R'],
  // Salud Total (rosa@): lista y vence hoy en hora de Lima, para "Por recoger hoy" del Inicio (H103)
  [1, 4, 1, 'LISTA', 30 * HORA, hastaFinDelDiaLima(), 'BC-K8DN4F'],
].map(([productoId, usuarioId, cantidad, estado, creada, vence, codigo, motivoRechazo = null], i) => {
  const producto = db.productos.find((p) => p.id === productoId)
  const creadoEn = haceMs(creada)
  return {
    id: i + 1, codigo, estado, productoId, usuarioId, cantidad, precioUnitario: producto.precio,
    motivoRechazo, venceEn: enMs(vence), creadoEn,
    actualizadoEn: estado === 'PENDIENTE' ? creadoEn : haceMs(Math.max(creada / 2, MINUTO)),
  }
})

// ---------- Reseñas de prueba (sección 11): Salud Total tiene 13 en 2 páginas (promedio 4.2), una de una cuenta
// eliminada y una oculta que no cuenta; FarmaVida, ninguna (para ver "Aún no tienes reseñas") ----------
/** [boticaId, usuarioId, estrellas, comentario, respuesta, creada hace, oculta] */
db.resenas = [
  [1, 4, 5, 'Me atendieron rápido y tenían todo.', '¡Gracias, Carlos! Te esperamos.', 3 * 24 * HORA],
  [1, 5, 4, 'Buen precio y el pedido estaba listo.', null, 6 * 24 * HORA],
  [1, 6, 4, null, null, 20 * 24 * HORA],
  [1, 7, 5, 'La señorita me explicó cómo tomar el antibiótico. Muy amable.', null, 2 * HORA],
  [1, 8, 3, 'Tuve que esperar un rato, pero encontré el medicamento que en otras boticas no había.', null, 1 * 24 * HORA],
  [1, 9, 5, 'Siempre compro aquí, precios justos.', null, 2 * 24 * HORA],
  [1, 10, 4, null, null, 4 * 24 * HORA],
  [1, 11, 5, 'Reservé por la app y al llegar ya estaba separado.', null, 8 * 24 * HORA],
  [1, 12, 2, 'El precio en la app no coincidía con el de la tienda.', null, 10 * 24 * HORA],
  [1, 13, 4, 'Buena atención.', null, 12 * 24 * HORA],
  [1, 14, 5, 'Local limpio y ordenado.', null, 15 * 24 * HORA],
  [1, 15, 4, 'Todo bien.', null, 25 * 24 * HORA],
  [1, 16, 5, 'Muy recomendable, atienden hasta tarde.', null, 30 * 24 * HORA],
  // Oculta por el administrador (H89): no aparece ni cuenta en el promedio
  [1, 17, 1, 'Comentario ofensivo de prueba.', null, 5 * 24 * HORA, true],
].map(([boticaId, usuarioId, estrellas, comentario, respuesta, creada, oculta = false], i) => ({
  id: i + 1, boticaId, usuarioId, estrellas, comentario, respuesta, oculta,
  respondidaEn: respuesta ? haceMs(creada / 2) : null, creadoEn: haceMs(creada), actualizadoEn: haceMs(creada),
}))

const siguienteId = (lista) => Math.max(0, ...lista.map((x) => x.id)) + 1
const esperar = () => new Promise((r) => setTimeout(r, 250))
const copia = (x) => structuredClone(x)
const normalizar = (texto) =>
  String(texto ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

// ---------- Respuestas con la forma exacta de docs/API.md ----------
const usuarioCorto = ({ id, nombre, correo, rol }) => ({ id, nombre, correo, rol })
const perfil = ({ id, nombre, correo, telefono, direccion, rol }) => ({ id, nombre, correo, telefono, direccion, rol })
// eslint-disable-next-line no-unused-vars
const boticaDto = ({ usuarioId, ...botica }) => botica
const productoDto = ({ id, nombreComercial, principioActivo, presentacion, precio, stock, stockMinimo, medicamentoId }) =>
  ({ id, nombreComercial, principioActivo, presentacion, precio, stock, stockMinimo, medicamentoId: medicamentoId ?? null })

const medicamentoDto = ({ id, nombreComercial, principioActivo, presentacion, concentracion }) =>
  ({ id, nombreComercial, principioActivo, presentacion, concentracion: concentracion ?? null })

/** D4: igual que MedicamentoService.enlace del backend: { principioActivo } o { error }. */
function enlaceMedicamento(id) {
  const medicamento = db.medicamentos.find((m) => m.id === id)
  if (!medicamento) return { error: 'El medicamento no existe en el catálogo' }
  if (medicamento.oculto) return { error: 'El medicamento no está disponible en el catálogo' }
  return { principioActivo: medicamento.principioActivo }
}

/**
 * D3: medicamentoId de POST/PUT /productos. Devuelve el principio activo del medicamento
 * (regla D2) o lanza 400 si no existe o está oculto. Sin medicamentoId devuelve null.
 */
function enlaceDelCuerpo(datos) {
  const id = datos?.medicamentoId
  if (id === undefined || id === null) return null
  if (!Number.isSafeInteger(id) || id <= 0) throw crearError(400, 'El medicamentoId debe ser un número entero mayor a 0.')
  const enlace = enlaceMedicamento(id)
  if (enlace.error) throw crearError(400, enlace.error)
  return { medicamentoId: id, principioActivo: enlace.principioActivo }
}

/** Regla D2 en un PUT sin medicamentoId: el producto enlazado conserva el principio activo del medicamento. */
const principioDelEnlace = (producto) =>
  db.medicamentos.find((m) => m.id === producto.medicamentoId)?.principioActivo

// H3: igual que el backend, ambas coordenadas o ninguna, dentro de rango
function validarCoordenadas({ latitud, longitud }) {
  const hayLat = latitud !== undefined && latitud !== null
  const hayLng = longitud !== undefined && longitud !== null
  if (hayLat !== hayLng) throw crearError(400, 'Envía latitud y longitud juntas.')
  if (!hayLat) return
  if (!(Math.abs(latitud) <= 90) || !(Math.abs(longitud) <= 180)) {
    throw crearError(400, 'Las coordenadas están fuera de rango.')
  }
}

// H4: igual que el backend, revisa el contenido (firma del archivo), no solo la extensión
const FIRMAS_LICENCIA = [
  [0x25, 0x50, 0x44, 0x46], // %PDF
  [0x89, 0x50, 0x4e, 0x47], // PNG
  [0xff, 0xd8, 0xff], // JPEG
]

async function contenidoPermitido(archivo) {
  const bytes = new Uint8Array(await archivo.slice(0, 4).arrayBuffer())
  return FIRMAS_LICENCIA.some((firma) => firma.every((b, i) => bytes[i] === b))
}

// Mismas reglas en POST /boticas y PUT /boticas/mia
function validarDatosBotica(datos) {
  const obligatorios = ['nombreComercial', 'ruc', 'razonSocial', 'direccion', 'distrito']
  if (obligatorios.some((c) => !String(datos?.[c] ?? '').trim())) {
    throw crearError(400, 'Completa todos los datos obligatorios de la botica.')
  }
  if (!validarRuc(datos.ruc).valido) throw crearError(400, 'El RUC no es válido.')
  validarCoordenadas(datos)
}

const datosBotica = (datos) => ({
  nombreComercial: datos.nombreComercial.trim(), ruc: datos.ruc,
  razonSocial: datos.razonSocial.trim(), direccion: datos.direccion.trim(),
  distrito: datos.distrito.trim(), telefono: datos.telefono?.trim() || null,
  latitud: datos.latitud ?? null, longitud: datos.longitud ?? null,
})

// ---------- Autenticación simulada ----------
function usuarioActual() {
  const [prefijo, id] = (obtenerToken() ?? '').split('.')
  const usuario = prefijo === 'mock-token' && db.usuarios.find((u) => u.id === Number(id))
  if (!usuario) throw crearError(401, 'Tu sesión expiró. Inicia sesión nuevamente.')
  return usuario
}

function duenoActual() {
  const usuario = usuarioActual()
  if (usuario.rol !== 'DUENO_BOTICA') throw crearError(403, 'Esta opción es solo para dueños de botica.')
  return usuario
}

function boticaActual() {
  const botica = db.boticas.find((b) => b.usuarioId === duenoActual().id)
  if (!botica) throw crearError(404, 'Todavía no registraste tu botica.')
  return botica
}

function productoPropio(id) {
  const botica = boticaActual()
  const producto = db.productos.find((p) => p.id === Number(id) && p.activo && p.boticaId === botica.id)
  if (!producto) throw crearError(404, 'El producto no existe o no pertenece a tu botica.')
  return producto
}

// ---------- 10. Pedidos del dueño (mismas reglas que docs/API.md) ----------
const ESTADOS_RESERVA = ['PENDIENTE', 'CONFIRMADA', 'LISTA', 'ENTREGADA', 'RECHAZADA', 'CANCELADA', 'VENCIDA']
const ESTADOS_ACTIVOS = ['PENDIENTE', 'CONFIRMADA', 'LISTA']
// Únicos cambios de PATCH /pedidos/{id}/estado
const TRANSICIONES = { PENDIENTE: 'CONFIRMADA', CONFIRMADA: 'LISTA', LISTA: 'ENTREGADA' }
const PLAZO_RECOGER_MS = 48 * HORA

/** "Carlos Ramírez" -> "Carlos R."; una sola palabra sale tal cual; cuenta eliminada -> "Usuario eliminado". */
function nombreCorto(usuario) {
  if (!usuario || usuario.eliminado) return 'Usuario eliminado'
  const [nombre, apellido] = usuario.nombre.trim().split(/\s+/)
  return apellido ? `${nombre} ${apellido[0].toUpperCase()}.` : nombre
}

const redondear = (valor) => Math.round(valor * 100) / 100

function pedidoDto(reserva) {
  const producto = db.productos.find((p) => p.id === reserva.productoId)
  const { id, codigo, estado, productoId, cantidad, precioUnitario, motivoRechazo, venceEn, creadoEn, actualizadoEn } = reserva
  return {
    id, codigo, estado, productoId,
    nombreComercial: producto.nombreComercial, presentacion: producto.presentacion,
    cantidad, precioUnitario, total: redondear(cantidad * precioUnitario), stockActual: producto.stock,
    // Regla nueva: solo en PENDIENTE, stock menos las otras reservas activas (nunca negativo)
    disponible: estado === 'PENDIENTE' ? Math.max(producto.stock - apartadasPorOtras(reserva), 0) : null,
    compradorNombre: nombreCorto(db.usuarios.find((u) => u.id === reserva.usuarioId)),
    motivoRechazo, venceEn, creadoEn, actualizadoEn,
    // H72: la condición actual del medicamento enlazado; null si el producto no está enlazado
    condicionVenta: db.medicamentos.find((m) => m.id === producto.medicamentoId)?.condicionVenta ?? null,
  }
}

const ahoraIso = () => new Date().toISOString()
const yaVencio = (reserva) => new Date(reserva.venceEn).getTime() <= Date.now()

/** H84: imita el proceso programado, que pasa a VENCIDA las activas con venceEn pasado. */
function vencerReservas() {
  for (const r of db.reservas) {
    if (ESTADOS_ACTIVOS.includes(r.estado) && yaVencio(r)) Object.assign(r, { estado: 'VENCIDA', actualizadoEn: ahoraIso() })
  }
}

const reservasDeBotica = (botica) =>
  db.reservas.filter((r) => db.productos.find((p) => p.id === r.productoId)?.boticaId === botica.id)

function pedidoPropio(id) {
  const botica = boticaActual()
  if (!/^\d+$/.test(String(id))) throw crearError(400, 'El id del pedido debe ser numérico.')
  const reserva = reservasDeBotica(botica).find((r) => r.id === Number(id))
  if (!reserva) throw crearError(404, 'El pedido no existe o no pertenece a tu botica.')
  return reserva
}

function validarEstado(estado, obligatorio) {
  if (!obligatorio && (estado === undefined || estado === null || estado === '')) return
  if (!ESTADOS_RESERVA.includes(estado)) {
    throw crearError(400, `El estado debe ser uno de: ${ESTADOS_RESERVA.join(', ')}.`)
  }
}

/**
 * Regla nueva: unidades comprometidas de un producto (reservas CONFIRMADA y LISTA).
 * El dueño no puede bajar el stock por debajo de ellas.
 */
const unidadesComprometidas = (productoId) =>
  db.reservas
    .filter((r) => r.productoId === productoId && (r.estado === 'CONFIRMADA' || r.estado === 'LISTA'))
    .reduce((suma, r) => suma + r.cantidad, 0)

const mensajeComprometidas = (n) =>
  `Tienes ${n === 1 ? '1 unidad comprometida' : `${n} unidades comprometidas`} en pedidos confirmados o listos para recoger; el stock no puede ser menor`

const MENSAJE_BAJA_COMPROMETIDA =
  'No puedes dar de baja un producto con pedidos confirmados o listos para recoger. Atiéndelos o recházalos primero.'

/** Unidades apartadas por las otras reservas activas del mismo producto. */
const apartadasPorOtras = (reserva) =>
  db.reservas
    .filter((r) => r.productoId === reserva.productoId && r.id !== reserva.id && ESTADOS_ACTIVOS.includes(r.estado))
    .reduce((suma, r) => suma + r.cantidad, 0)

// ---------- Validación de productos (mismos mensajes que el backend) ----------
const ENTERO_MAXIMO = 2147483647
const texto = (valor) => String(valor ?? '').trim()

function validarTexto(valor, vacio, largo, max) {
  if (!valor) return vacio
  if (valor.length > max) return largo
  return ''
}

function validarPrecio(valor) {
  if (!valor) return 'El precio es obligatorio'
  const partes = /^[+-]?(\d*)(?:\.(\d*))?$/.exec(valor)
  if (!partes || !/\d/.test(valor)) return 'El precio debe ser un número (usa punto para los decimales)'
  if (Number(valor) <= 0) return 'El precio debe ser mayor a 0'
  if ((partes[2] ?? '').replace(/0+$/, '').length > 2) return 'El precio admite hasta 2 decimales'
  if (partes[1].replace(/^0+/, '').length > 8) return 'El precio es demasiado alto'
  return ''
}

function validarEntero(valor, nombre) {
  if (!valor) return `${nombre} es obligatorio`
  if (!/^[+-]?\d+$/.test(valor) || Math.abs(Number(valor)) > ENTERO_MAXIMO) return `${nombre} debe ser un número entero`
  if (Number(valor) < 0) return `${nombre} no puede ser negativo`
  return ''
}

/**
 * Reglas de POST/PUT /productos, que también usa cada fila de la carga masiva (H11).
 * Acepta números (JSON) o textos (celdas del CSV). Devuelve { error } o { producto }.
 * stockMinimo es opcional: si no viene queda en undefined.
 */
function revisarProducto(datos) {
  const nombreComercial = texto(datos?.nombreComercial)
  const principioActivo = texto(datos?.principioActivo)
  const presentacion = texto(datos?.presentacion)
  const precio = texto(datos?.precio)
  const stock = texto(datos?.stock)
  const stockMinimo = texto(datos?.stockMinimo)
  const error =
    validarTexto(nombreComercial, 'El nombre comercial es obligatorio', 'El nombre comercial admite máximo 150 caracteres', 150) ||
    validarTexto(principioActivo, 'El principio activo es obligatorio', 'El principio activo admite máximo 150 caracteres', 150) ||
    validarTexto(presentacion, 'La presentación es obligatoria', 'La presentación admite máximo 100 caracteres', 100) ||
    validarPrecio(precio) ||
    validarEntero(stock, 'El stock') ||
    (stockMinimo && validarEntero(stockMinimo, 'El stock mínimo'))
  if (error) return { error }
  return {
    producto: {
      nombreComercial, principioActivo, presentacion,
      precio: Math.round(Number(precio) * 100) / 100,
      stock: Number(stock),
      stockMinimo: stockMinimo ? Number(stockMinimo) : undefined,
    },
  }
}

function validarProducto(datos) {
  const { error, producto } = revisarProducto(datos)
  if (error) throw crearError(400, error)
  return producto
}

// ---------- H11: lectura del CSV, igual que CsvProductoParser del backend ----------
const MAX_FILAS_CSV = 1000
const COLUMNAS_OBLIGATORIAS = ['nombrecomercial', 'principioactivo', 'presentacion', 'precio', 'stock']
const claveProducto = (nombreComercial, presentacion) =>
  `${nombreComercial.trim().toLowerCase()}|${presentacion.trim().toLowerCase()}`

/** Lector mínimo: comas, comillas dobles, comillas escapadas ("") y saltos CRLF o LF. */
function leerCsv(contenido) {
  const registros = []
  let campos = []
  let campo = ''
  let entreComillas = false
  let linea = 1
  let lineaInicio = 1
  const textoCsv = `${contenido}\n`
  for (let i = 0; i < textoCsv.length; i++) {
    const c = textoCsv[i]
    if (entreComillas) {
      if (c === '"') {
        if (textoCsv[i + 1] === '"') {
          campo += '"'
          i++
        } else {
          entreComillas = false
        }
      } else {
        if (c === '\n') linea++
        campo += c
      }
    } else if (c === '"' && campo === '') {
      entreComillas = true
    } else if (c === ',') {
      campos.push(campo)
      campo = ''
    } else if (c === '\n' || c === '\r') {
      if (c === '\r' && textoCsv[i + 1] === '\n') i++
      campos.push(campo)
      campo = ''
      // Las líneas vacías (o solo con comas, como las que deja Excel) no cuentan como fila
      if (campos.some((s) => s.trim())) registros.push({ linea: lineaInicio, campos })
      campos = []
      linea++
      lineaInicio = linea
    } else {
      campo += c
    }
  }
  if (entreComillas) throw crearError(400, 'El archivo tiene comillas sin cerrar')
  return registros
}

/** Problemas del archivo completo lanzan 400; los de cada fila van en `errores`. */
async function leerFilasProductos(archivo) {
  if (!archivo) throw crearError(400, "Falta el archivo: envíalo en el campo 'archivo'")
  if (archivo.size > MAX_BYTES_CARGA) throw crearError(400, 'El archivo supera el máximo de 5 MB')
  if (archivo.size === 0) throw crearError(400, 'El archivo está vacío')
  // El backend lee .xlsx, pero aquí no hay una librería para abrirlo en el navegador
  if (archivo.name?.toLowerCase().endsWith('.xlsx')) {
    return {
      validas: [],
      errores: [{
        fila: 1,
        mensaje: 'En modo simulado solo se procesa CSV: el archivo .xlsx no se leyó. ' +
          'Prueba con la plantilla CSV o usa el backend real (VITE_USE_MOCK=false).',
      }],
    }
  }
  let contenido
  try {
    // TextDecoder quita el BOM que agrega Excel al guardar como "CSV UTF-8"
    contenido = new TextDecoder('utf-8', { fatal: true }).decode(await archivo.arrayBuffer())
  } catch {
    throw crearError(400, 'El archivo debe estar codificado en UTF-8. Guárdalo como "CSV UTF-8" desde Excel')
  }
  if (contenido.includes('\0')) throw crearError(400, 'El archivo no es un CSV válido')

  const registros = leerCsv(contenido)
  if (!registros.length) throw crearError(400, 'El archivo está vacío')
  const [cabecera, ...filas] = registros
  const indice = {}
  cabecera.campos.forEach((nombre, i) => {
    indice[nombre.trim().toLowerCase()] ??= i
  })
  if (!COLUMNAS_OBLIGATORIAS.every((c) => c in indice)) {
    throw crearError(400, 'Encabezado inválido. Se esperan las columnas nombreComercial,principioActivo,presentacion,precio,stock[,stockMinimo][,medicamentoId] separadas por coma.')
  }
  if (!filas.length) throw crearError(400, 'El archivo no tiene filas de productos')
  if (filas.length > MAX_FILAS_CSV) throw crearError(400, `El archivo supera el máximo de ${MAX_FILAS_CSV} filas`)

  const validas = []
  const errores = []
  const vistos = new Map()
  for (const { linea, campos } of filas) {
    if (campos.slice(cabecera.campos.length).some((s) => s.trim())) {
      errores.push({ fila: linea, mensaje: 'La fila tiene más columnas que el encabezado' })
      continue
    }
    const celda = (columna) => (indice[columna] === undefined ? '' : campos[indice[columna]] ?? '')
    const { error, producto } = revisarProducto({
      nombreComercial: celda('nombrecomercial'),
      principioActivo: celda('principioactivo'),
      presentacion: celda('presentacion'),
      precio: celda('precio'),
      stock: celda('stock'),
      stockMinimo: celda('stockminimo'),
    })
    // D4: medicamentoId es opcional; vacío o sin columna conserva el enlace actual
    const medicamentoId = celda('medicamentoid').trim()
    const errorMedicamento =
      medicamentoId && !(/^[+-]?\d+$/.test(medicamentoId) && Number(medicamentoId) > 0 && Number(medicamentoId) <= Number.MAX_SAFE_INTEGER)
        ? 'El medicamentoId debe ser un número entero mayor a 0.'
        : ''
    if (error || errorMedicamento) {
      errores.push({ fila: linea, mensaje: error || errorMedicamento })
      continue
    }
    const clave = claveProducto(producto.nombreComercial, producto.presentacion)
    if (vistos.has(clave)) {
      errores.push({ fila: linea, mensaje: `Producto duplicado en el archivo (ya aparece en la fila ${vistos.get(clave)})` })
      continue
    }
    vistos.set(clave, linea)
    validas.push({ ...producto, fila: linea, medicamentoId: medicamentoId ? Number(medicamentoId) : null })
  }
  return { validas, errores }
}

// ---------- 8. Asistente simulado (H68, POST /chat) ----------
// No hay modelo de IA: se reconoce la intención con palabras clave y se responde
// con los datos reales de la simulación, con la misma forma que docs/API.md.
// Para probar errores, escribe en el mensaje: error429, error503, error504 o error401.
// Si el mensaje contiene "respaldo", la respuesta llega con "respaldo": true.
// Al dueño le responde como el backend: precios como "S/ 8.50", listas de hasta 8 líneas con "- "
// (stock bajo, su catálogo y consejos de temporada: "¿qué conviene tener en invierno?") y, al comparar
// precios, "en tu botica: S/ X" frente a la opción más barata de las otras boticas.
// Si contiene una frase del filtro de crisis (por ejemplo "hacerme daño" o "me falta el aire"),
// llega el texto de ayuda con "ayuda": true y sin tarjetas.
const ESPERA_CHAT_MS = 2000
const MAX_TARJETAS_HERRAMIENTA = 5 // cada herramienta devuelve como máximo 5 resultados
const ROLES_HISTORIAL = new Set(['usuario', 'asistente'])

// Textos de docs/API.md, sección 8, "Crisis y números de ayuda" (H68)
const NUMEROS_AYUDA = 'Si es una emergencia, llama al SAMU 106. Si piensas en hacerte daño, llama a la Línea 113, opción 5.'
const TEXTO_AUTOLESION =
  'Lamento mucho que te sientas así y quiero que sepas que no estás solo. Llama al SAMU al 106 o a la Línea 113, opción 5, que es la de salud mental del Ministerio de Salud. Hay personas capacitadas dispuestas a escucharte y ayudarte en este momento.'
const TEXTO_EMERGENCIA =
  'Llama al SAMU al 106 de inmediato. BotiCompara solo compara precios de medicamentos y no puede atender una emergencia: ahora lo más importante es tu salud.'
// Frases del filtro de crisis, en minúsculas y sin tildes (se comparan con el mensaje normalizado)
const FRASES_AUTOLESION = [
  'hacerme dano', 'matarme', 'quiero matarme', 'suicid', 'quitarme la vida', 'no quiero vivir',
  'no quiero seguir viviendo', 'quiero morir', 'me quiero morir', 'acabar con mi vida',
  'cortarme las venas', 'hacerme cortes',
]
const FRASES_EMERGENCIA = [
  'no puedo respirar', 'no puedo respirar bien', 'me falta el aire', 'dolor de pecho', 'dolor en el pecho',
  'me duele el pecho', 'intoxicacion', 'envenenamiento', 'convulsion', 'desmayo',
]

const ERRORES_CHAT = {
  error429: [429, `Hiciste muchas preguntas seguidas. Espera un minuto y vuelve a intentar. ${NUMEROS_AYUDA}`],
  error503: [503, `El asistente no está disponible en este momento. Intenta más tarde. ${NUMEROS_AYUDA}`],
  error504: [504, `El asistente tardó demasiado en responder. Intenta de nuevo. ${NUMEROS_AYUDA}`],
  error401: [401, 'Tu sesión expiró. Inicia sesión nuevamente.'],
}

const TEXTO_FUERA_DE_ALCANCE =
  'No puedo recomendarte medicamentos ni dosis. Consulta con el químico farmacéutico de tu botica o con tu médico. Si es una emergencia, llama al SAMU al 106. Si ya sabes qué medicamento necesitas, te ayudo a encontrar dónde está más barato.'
const PIDE_CONSEJO = /\b(duele|dolor|fiebre|dosis|sintomas?|recetame|recomiendas?|que (me )?tomo|que puedo tomar)\b/
const PALABRAS_GENERICAS = new Set(['generico', 'generica'])
const PIDE_STOCK_BAJO = /\b(stock bajo|poco stock|reponer|por agotarse|se me acaba)\b/
// buscar_en_mi_catalogo: el dueño pregunta por lo que tiene en su propia botica
const PIDE_MI_CATALOGO = /\b(tengo|tenemos|mi catalogo|mis productos|mi botica)\b/
// Consejo de negocio del dueño (surtido, vitrina, temporadas): sin herramientas ni tarjetas
const PIDE_CONSEJO_NEGOCIO = /\b(consejos?|surtido|vitrina|temporadas?|invierno|verano|vender mas|que mas (puedo )?vender)\b/
// Respuesta del dueño (docs/API.md, sección 8): hasta 900 caracteres y listas de hasta 8 líneas con "- "
const MAX_LINEAS_LISTA = 8
const MAX_RESPUESTA_DUENO = 900
const CONSEJO_INVIERNO = [
  'Pañuelos desechables y bálsamo para labios',
  'Termómetros digitales',
  'Mascarillas y alcohol en gel',
  'Caramelos para la garganta (sin receta)',
  'Vaporizadores y humidificadores pequeños',
  'Cremas humectantes para manos y rostro',
  'Sueros de rehidratación oral',
  'Medias y guantes térmicos, si tienes espacio en vitrina',
]

/** Espera cancelable: si se aborta `signal`, rechaza con su motivo. */
function esperarCancelable(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(signal.reason)
    const id = setTimeout(resolve, ms)
    signal?.addEventListener('abort', () => {
      clearTimeout(id)
      reject(signal.reason)
    }, { once: true })
  })
}

/** 2.2 -> "2 soles con 20"; 58 -> "58 soles"; 1 -> "1 sol" (para leerse en voz alta) */
function precioHablado(valor) {
  const centimos = Math.round(Number(valor) * 100)
  const soles = Math.floor(centimos / 100)
  const resto = centimos % 100
  if (soles === 0) return `${resto} céntimos`
  const base = soles === 1 ? '1 sol' : `${soles} soles`
  return resto ? `${base} con ${String(resto).padStart(2, '0')}` : base
}

/** Para el dueño: 8.5 -> "S/ 8.50" (como TextoRespaldo.precioConSimbolo del backend) */
const precioConSimbolo = (valor) => `S/ ${Number(valor).toFixed(2)}`

/** El precio como lo escribe el backend según el rol: hablado para el comprador (H71), con símbolo para el dueño. */
const precioSegunRol = (valor, esDueno) => (esDueno ? precioConSimbolo(valor) : precioHablado(valor))

/** Frase corta y una lista de hasta 8 líneas que empiezan con "- " (respuesta del dueño). */
function textoLista(frase, lineas) {
  const texto = [frase, ...lineas.slice(0, MAX_LINEAS_LISTA).map((l) => `- ${l}`)].join('\n')
  return texto.length <= MAX_RESPUESTA_DUENO ? texto : `${texto.slice(0, MAX_RESPUESTA_DUENO - 1)}…`
}

/** Distancia en línea recta (haversine), con 2 decimales, como GET /productos/buscar. */
function distanciaKm(lat, lng, botica) {
  if (lat == null || lng == null || botica.latitud == null || botica.longitud == null) return null
  const rad = (grados) => (grados * Math.PI) / 180
  const dLat = rad(botica.latitud - lat)
  const dLng = rad(botica.longitud - lng)
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(lat)) * Math.cos(rad(botica.latitud)) * Math.sin(dLng / 2) ** 2
  return Math.round(6371 * 2 * Math.asin(Math.sqrt(a)) * 100) / 100
}

/** Tarjeta de `productos`: stockMinimo solo en los productos propios del dueño. */
function tarjetaChat(p, { propio = false, lat, lng } = {}) {
  const botica = db.boticas.find((b) => b.id === p.boticaId)
  return {
    productoId: p.id,
    nombreComercial: p.nombreComercial,
    principioActivo: p.principioActivo,
    presentacion: p.presentacion,
    precio: p.precio,
    stock: p.stock,
    stockMinimo: propio ? p.stockMinimo : null,
    boticaId: botica.id,
    boticaNombre: botica.nombreComercial,
    boticaDistrito: botica.distrito,
    distanciaKm: distanciaKm(lat, lng, botica),
    // H72: condición de venta del medicamento enlazado; null si no está enlazado o no tiene el dato
    condicionVenta: db.medicamentos.find((m) => m.id === p.medicamentoId)?.condicionVenta ?? null,
  }
}

/** Lo que ve el comprador (v_producto_busqueda): activo, con stock, de botica aprobada, sin medicamento oculto. */
function visibleEnBusqueda(p) {
  const botica = db.boticas.find((b) => b.id === p.boticaId)
  const medicamento = db.medicamentos.find((m) => m.id === p.medicamentoId)
  return p.activo && p.stock > 0 && botica?.estado === 'APROBADO' && !medicamento?.oculto
}

/**
 * Medicamento que se menciona en el mensaje: la palabra que coincidió
 * ("panadol") y su principio activo ("Paracetamol"), o null.
 */
function medicamentoMencionado(mensaje) {
  const palabras = normalizar(mensaje).split(/[^a-z0-9]+/)
    .filter((p) => p.length >= 4 && !PALABRAS_GENERICAS.has(p))
  for (const palabra of palabras) {
    const producto = db.productos.find((p) =>
      [p.nombreComercial, p.principioActivo].some((nombre) => normalizar(nombre).split(/\s+/).includes(palabra)))
    if (producto) return { palabra, principio: producto.principioActivo }
  }
  return null
}

const principioMencionado = (mensaje) => medicamentoMencionado(mensaje)?.principio ?? null

/** Herramienta buscar_en_mi_catalogo del dueño (como GET /boticas/mia/productos?q=). */
function respuestaMiCatalogo(usuario, mensaje) {
  const botica = db.boticas.find((b) => b.usuarioId === usuario.id)
  if (!botica) {
    return { respuesta: 'Todavía no registraste tu botica, así que no tengo un catálogo que revisar. Cuando la registres, podré decirte a cuánto tienes cada producto.', productos: [] }
  }
  const mencionado = medicamentoMencionado(mensaje)
  if (!mencionado) {
    return { respuesta: `¿Qué producto quieres revisar en el catálogo de ${botica.nombreComercial}? Por ejemplo, pregúntame a cuánto tienes la amoxicilina.`, productos: [] }
  }
  const { palabra } = mencionado
  const propios = db.productos
    .filter((p) => p.boticaId === botica.id && p.activo)
    .filter((p) => normalizar(p.nombreComercial).includes(palabra) || normalizar(p.principioActivo).includes(palabra))
    .sort((a, b) => a.nombreComercial.localeCompare(b.nombreComercial, 'es'))
    .slice(0, MAX_TARJETAS_HERRAMIENTA)
  if (propios.length === 0) {
    return { respuesta: `No encontré ${palabra} en el catálogo de ${botica.nombreComercial}. Puedes agregarlo desde Catálogo.`, productos: [] }
  }
  const lineas = propios.map((p) =>
    `${p.nombreComercial}, ${p.presentacion}: ${precioConSimbolo(p.precio)}, ${p.stock === 0 ? 'agotado' : `${p.stock} ${p.stock === 1 ? 'unidad' : 'unidades'}`}`)
  return {
    respuesta: textoLista(`En tu catálogo tienes ${propios.length === 1 ? 'este producto' : 'estos productos'}:`, lineas),
    productos: propios.map((p) => tarjetaChat(p, { propio: true })),
  }
}

/** Herramienta buscar_precios (tipo principio): de la más barata a la más cara, o por cercanía. */
function buscarPreciosChat(principio, lat, lng) {
  const tarjetas = db.productos
    .filter((p) => visibleEnBusqueda(p) && normalizar(p.principioActivo) === normalizar(principio))
    .map((p) => tarjetaChat(p, { lat, lng }))
  const porPrecio = (a, b) => a.precio - b.precio
  const conDistancia = lat != null && lng != null
  tarjetas.sort(conDistancia
    ? (a, b) => (a.distanciaKm ?? Infinity) - (b.distanciaKm ?? Infinity) || porPrecio(a, b)
    : porPrecio)
  return tarjetas.slice(0, MAX_TARJETAS_HERRAMIENTA)
}

function textoPrecios(principio, tarjetas, usuario) {
  const esDueno = usuario.rol === 'DUENO_BOTICA'
  // tuBotica (buscar_precios del backend): los productos de la botica del dueño que pregunta
  const miBoticaId = esDueno ? db.boticas.find((b) => b.usuarioId === usuario.id)?.id : null
  const propias = tarjetas.filter((t) => t.boticaId === miBoticaId)
  const otras = tarjetas.filter((t) => t.boticaId !== miBoticaId)
  const precio = (valor) => precioSegunRol(valor, esDueno)

  if (esDueno && propias.length) {
    // Al dueño se le dice "en tu botica: S/ X" (nunca el nombre de su botica) y se compara con la más barata de las otras
    const mia = [...propias].sort((a, b) => a.precio - b.precio)[0]
    if (!otras.length) {
      return `Solo encontré ${principio.toLowerCase()} en tu botica: S/ ${mia.precio.toFixed(2)} (${mia.nombreComercial}). Ninguna otra botica aprobada lo tiene con stock.`
    }
    const rival = [...otras].sort((a, b) => a.precio - b.precio)[0]
    const diferencia = Math.abs(mia.precio - rival.precio)
    const comparacion = mia.precio < rival.precio
      ? `Estás ${precioConSimbolo(diferencia)} por debajo.`
      : mia.precio > rival.precio
        ? `Estás ${precioConSimbolo(diferencia)} por encima.`
        : 'Están al mismo precio.'
    return `${mia.nombreComercial} en tu botica: ${precioConSimbolo(mia.precio)}. La opción más barata de las otras boticas es ${rival.nombreComercial} en ${rival.boticaNombre}, a ${precioConSimbolo(rival.precio)}. ${comparacion}`
  }

  const masBarata = [...tarjetas].sort((a, b) => a.precio - b.precio)[0]
  const distancia = masBarata.distanciaKm != null ? `, a ${masBarata.distanciaKm.toFixed(1)} kilómetros de ti` : ''
  let texto = `Encontré ${tarjetas.length === 1 ? 'una opción' : `${tarjetas.length} opciones`} de ${principio.toLowerCase()} en boticas aprobadas. La más barata es ${masBarata.nombreComercial} en ${masBarata.boticaNombre}, ${masBarata.boticaDistrito}, a ${precio(masBarata.precio)}${distancia}.`
  const otraMarca = tarjetas.find((t) => t.nombreComercial !== masBarata.nombreComercial)
  if (otraMarca) {
    texto += ` Si prefieres ${otraMarca.nombreComercial}, está desde ${precio(otraMarca.precio)} en ${otraMarca.boticaNombre}.`
  }
  return texto
}

/** Texto de respaldo: lo arma "el servidor" solo con datos de las tarjetas. Precios según el rol. */
function textoRespaldo(tarjetas, usuario) {
  const masBarata = [...tarjetas].sort((a, b) => a.precio - b.precio)[0]
  const precio = precioSegunRol(masBarata.precio, usuario.rol === 'DUENO_BOTICA')
  return `Encontré ${tarjetas.length === 1 ? '1 opción' : `${tarjetas.length} opciones`}. La más barata es ${masBarata.nombreComercial} a ${precio} en ${masBarata.boticaNombre}, ${masBarata.boticaDistrito}.`
}

/** Herramienta ver_stock_bajo del dueño. */
function respuestaStockBajo(usuario) {
  const botica = db.boticas.find((b) => b.usuarioId === usuario.id)
  if (!botica) {
    return { respuesta: 'Todavía no registraste tu botica, así que no tengo un catálogo que revisar. Cuando la registres y la aprueben, podré decirte qué productos tienen stock bajo.', productos: [] }
  }
  const bajos = db.productos
    .filter((p) => p.boticaId === botica.id && p.activo && p.stock <= p.stockMinimo)
    .sort((a, b) => a.stock - b.stock)
    .slice(0, MAX_TARJETAS_HERRAMIENTA)
  if (bajos.length === 0) {
    return { respuesta: `No tienes productos con stock bajo en ${botica.nombreComercial}. Todos están por encima de su stock mínimo.`, productos: [] }
  }
  const lineas = bajos.map((p) =>
    `${p.nombreComercial}: ${p.stock === 0 ? 'agotado' : `${p.stock} ${p.stock === 1 ? 'unidad' : 'unidades'}`} (mínimo ${p.stockMinimo})`)
  const cuantos = bajos.length === 1 ? '1 producto' : `${bajos.length} productos`
  return {
    respuesta: textoLista(`Tienes ${cuantos} con stock bajo. Te conviene ${bajos.length === 1 ? 'reponerlo' : 'reponerlos'} pronto:`, lineas),
    productos: bajos.map((p) => tarjetaChat(p, { propio: true })),
  }
}

function textoAyuda(usuario) {
  const nombre = usuario.nombre.split(' ')[0]
  return usuario.rol === 'DUENO_BOTICA'
    ? `Hola, ${nombre}. Soy el asistente de BotiCompara. Puedo revisar tu stock, comparar tus precios con la competencia, ver alternativas y darte consejos para tu negocio: surtido, vitrina y temporadas. Por ejemplo, pregúntame qué productos tengo con stock bajo o qué conviene tener en invierno.`
    : `Hola, ${nombre}. Soy el asistente de BotiCompara. Puedo ayudarte a encontrar dónde está más barato un medicamento en las boticas aprobadas. Por ejemplo, pregúntame dónde está más barato el paracetamol.`
}

/** Mismas validaciones que el backend (docs/API.md, POST /chat). */
function validarPedidoChat({ mensaje, historial, lat, lng }) {
  const limpio = texto(mensaje)
  if (!limpio) throw crearError(400, 'Escribe tu pregunta')
  if (limpio.length > 500) throw crearError(400, 'La pregunta no puede pasar de 500 caracteres')
  if (historial != null) {
    const valido = Array.isArray(historial) && historial.every((m) =>
      ROLES_HISTORIAL.has(m?.rol) && texto(m?.texto).length > 0 && String(m.texto).length <= 1000)
    if (!valido) throw crearError(400, 'El historial no es válido')
  }
  validarCoordenadas({ latitud: lat, longitud: lng })
  return limpio
}

// ---------- 12. Notificaciones del dueño (H103, mismas reglas que docs/API.md) ----------
const MAX_NOTIFICACIONES = 50
const PLAZO_NOTIFICACIONES_MS = 7 * 24 * HORA
const ESPERA_NUEVA_RESERVA_MS = 60 * 1000
const TITULOS_NOTIFICACION = {
  NUEVA_RESERVA: 'Nueva reserva',
  RESERVA_CANCELADA: 'Reserva cancelada',
  RESERVA_VENCIDA: 'Reserva vencida',
}
db.notificaciones = []

/** Texto ya escrito al crearla y sin datos del comprador; como máximo una por reserva y tipo. */
function crearNotificacion(tipo, reserva, { creadoEn = ahoraIso(), leida = false } = {}) {
  if (db.notificaciones.some((n) => n.pedidoId === reserva.id && n.tipo === tipo)) return
  const producto = db.productos.find((p) => p.id === reserva.productoId)
  const condicion = db.medicamentos.find((m) => m.id === producto.medicamentoId)?.condicionVenta
  const partes = [`${producto.nombreComercial} x ${reserva.cantidad}`, precioConSimbolo(reserva.cantidad * reserva.precioUnitario)]
  if (tipo === 'NUEVA_RESERVA' && condicion === 'CON_RECETA') partes.push('Requiere receta médica')
  db.notificaciones.push({
    id: siguienteId(db.notificaciones),
    usuarioId: db.boticas.find((b) => b.id === producto.boticaId).usuarioId,
    tipo, titulo: `${TITULOS_NOTIFICACION[tipo]} ${reserva.codigo}`, mensaje: partes.join(' · '),
    pedidoId: reserva.id, leida, creadoEn,
  })
}

// Tres de ejemplo para rosa@: una cancelada ya leída y dos reservas nuevas sin leer
for (const [tipo, codigo, leida] of [
  ['RESERVA_CANCELADA', 'BC-W3FJ8N', true],
  ['NUEVA_RESERVA', 'BC-H4NP2T', false],
  ['NUEVA_RESERVA', 'BC-7K2QXM', false],
]) {
  const reserva = db.reservas.find((r) => r.codigo === codigo)
  crearNotificacion(tipo, reserva, { leida, creadoEn: tipo === 'NUEVA_RESERVA' ? reserva.creadoEn : reserva.actualizadoEn })
}

const LETRAS_CODIGO = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
function codigoReserva() {
  const bytes = crypto.getRandomValues(new Uint8Array(6))
  return `BC-${Array.from(bytes, (b) => LETRAS_CODIGO[b % LETRAS_CODIGO.length]).join('')}`
}

/** Imita POST /reservas desde la app del comprador: la reserva y la notificación al dueño. */
function simularReserva(productoId, cantidad = 1) {
  const producto = db.productos.find((p) => p.id === Number(productoId) && p.activo)
  if (!producto) return null
  const creadoEn = ahoraIso()
  const reserva = {
    id: siguienteId(db.reservas), codigo: codigoReserva(), estado: 'PENDIENTE', productoId: producto.id,
    usuarioId: 5, cantidad, precioUnitario: producto.precio, motivoRechazo: null,
    venceEn: enMs(24 * HORA), creadoEn, actualizadoEn: creadoEn,
  }
  db.reservas.push(reserva)
  crearNotificacion('NUEVA_RESERVA', reserva)
  return reserva
}

// A los 60 s de iniciar sesión (o de recargar la página con sesión) a rosa@ le llega una reserva nueva
let temporizadorReserva
function programarReservaNueva(usuarioId) {
  clearTimeout(temporizadorReserva)
  if (usuarioId === 1) temporizadorReserva = setTimeout(() => simularReserva(1, 2), ESPERA_NUEVA_RESERVA_MS)
}
programarReservaNueva(Number((obtenerToken() ?? '').split('.')[1]))

const notificacionDto = ({ id, tipo, titulo, mensaje, pedidoId, leida, creadoEn }) =>
  ({ id, tipo, titulo, mensaje, pedidoId, leida, creadoEn })

const misNotificaciones = () => {
  const dueno = duenoActual()
  return db.notificaciones.filter((n) => n.usuarioId === dueno.id)
}

/** Deja las de los últimos 7 días; a los 7 días justos ya no (docs/API.md). Se exporta para probarla. */
export const soloRecientes = (notificaciones, ahora = Date.now()) =>
  notificaciones.filter((n) => new Date(n.creadoEn).getTime() > ahora - PLAZO_NOTIFICACIONES_MS)

/** Lo que ven la campana (lista y contador) y la actividad reciente. */
const misNotificacionesRecientes = () => soloRecientes(misNotificaciones())

/** A5: el RUC solo choca con una botica APROBADO (las PENDIENTE y RECHAZADO pueden repetirlo). */
function exigirRucLibre(ruc) {
  if (db.boticas.some((b) => b.ruc === ruc && b.estado === 'APROBADO')) {
    throw crearError(409, 'El RUC ya está registrado')
  }
}

const palabras = (texto) =>
  (texto ?? '').normalize('NFD').replace(/\p{M}/gu, '').toUpperCase().split(/[^\p{L}\p{N}]+/u).filter(Boolean)

/** A7: cada palabra del nombre de la cuenta está en el del titular, sin tildes, mayúsculas ni orden. */
export function titularCoincide(nombreCuenta, titular) {
  const delTitular = new Set(palabras(titular))
  const deLaCuenta = palabras(nombreCuenta)
  return deLaCuenta.length > 0 && deLaCuenta.every((p) => delTitular.has(p))
}

/** A7: un RUC 10 o 15 (persona) con titular conocido en SUNAT debe estar a nombre de la cuenta. */
function exigirTitular(dueno, ruc) {
  const titular = RUCS_SUNAT[ruc]?.razonSocial
  if (!/^(10|15)/.test(ruc) || !titular) return
  if (!titularCoincide(dueno.nombre, titular)) {
    throw crearError(400, `Este RUC pertenece a una persona: la cuenta debe estar a nombre del titular (según SUNAT: ${titular})`)
  }
}

export const apiMock = {
  // ---------- 1. Autenticación ----------
  async login({ correo, password }) {
    await esperar()
    const usuario = db.usuarios.find((u) => u.correo === String(correo ?? '').trim().toLowerCase())
    if (!usuario || usuario.password !== password) throw crearError(401, 'Correo o contraseña incorrectos.')
    programarReservaNueva(usuario.id)
    return {
      token: `mock-token.${usuario.id}.${crypto.randomUUID()}`,
      tipo: 'Bearer',
      usuario: usuarioCorto(usuario),
    }
  },

  async registro({ nombre, correo, password, telefono, rol }) {
    await esperar()
    const correoNormal = String(correo ?? '').trim().toLowerCase()
    if (!String(nombre ?? '').trim()) throw crearError(400, 'El nombre es obligatorio.')
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correoNormal)) throw crearError(400, 'El correo no tiene un formato válido.')
    if (String(password ?? '').length < 8) throw crearError(400, 'La contraseña debe tener al menos 8 caracteres.')
    if (!['COMPRADOR', 'DUENO_BOTICA'].includes(rol)) throw crearError(400, 'El rol no es válido.')
    if (db.usuarios.some((u) => u.correo === correoNormal)) throw crearError(409, 'El correo ya está registrado.')

    const usuario = {
      id: siguienteId(db.usuarios), nombre: nombre.trim(), correo: correoNormal, password,
      telefono: telefono?.trim() || null, direccion: null, rol,
    }
    db.usuarios.push(usuario)
    return usuarioCorto(usuario)
  },

  // H17: siempre el mismo mensaje, exista o no el correo. Imita el límite de 5 solicitudes por hora por IP.
  async recuperarPassword(correo) {
    await esperar()
    if (!String(correo ?? '').trim()) throw crearError(400, 'El correo es obligatorio')
    const ahora = Date.now()
    recuperaciones = recuperaciones.filter((t) => ahora - t < HORA)
    if (recuperaciones.length >= MAX_RECUPERAR_HORA) throw crearError(429, MENSAJE_RECUPERAR_429)
    recuperaciones.push(ahora)
    return { mensaje: MENSAJE_RECUPERAR }
  },

  // H17: en el simulador el código válido es siempre 123456
  async restablecerPassword({ correo, token, password }) {
    await esperar()
    if (String(password ?? '').length < 8) throw crearError(400, 'La contraseña debe tener al menos 8 caracteres')
    const usuario = db.usuarios.find((u) => u.correo === String(correo ?? '').trim().toLowerCase())
    if (!usuario || token !== CODIGO_RECUPERACION_PRUEBA) throw crearError(400, MENSAJE_CODIGO_INVALIDO)
    usuario.password = password
    return { mensaje: 'Contraseña actualizada' }
  },

  // ---------- 2. Perfil ----------
  async obtenerPerfil() {
    await esperar()
    return perfil(usuarioActual())
  },

  // ---------- 3. Botica ----------
  // H2: simula la consulta a SUNAT solo para RUCS_SUNAT; los demás RUC válidos salen como LOCAL
  async validarRuc(ruc) {
    await esperar()
    const { valido, mensaje } = validarRuc(ruc)
    if (valido && RUCS_SUNAT[ruc]) return { ...RUCS_SUNAT[ruc], valido, mensaje, fuente: 'SUNAT' }
    return {
      ruc, valido,
      mensaje: valido ? 'RUC válido (no se pudo consultar SUNAT)' : mensaje,
      razonSocial: null, estado: null, condicion: null, fuente: 'LOCAL', direccion: null, distrito: null,
    }
  },

  async registrarBotica(datos) {
    await esperar()
    const dueno = duenoActual()
    validarDatosBotica(datos)
    if (db.boticas.some((b) => b.usuarioId === dueno.id)) throw crearError(409, 'Ya registraste una botica con esta cuenta.')
    exigirRucLibre(datos.ruc)
    exigirTitular(dueno, datos.ruc)

    const botica = {
      id: siguienteId(db.boticas), usuarioId: dueno.id, ...datosBotica(datos),
      tieneLicencia: false, estado: 'PENDIENTE', motivoRechazo: null,
    }
    db.boticas.push(botica)
    const { id, nombreComercial, ruc, estado } = botica
    return { id, nombreComercial, ruc, estado }
  },

  async obtenerMiBotica() {
    await esperar()
    return copia(boticaDto(boticaActual()))
  },

  // H7: solo una botica RECHAZADA se corrige; vuelve a PENDIENTE y conserva la licencia
  async actualizarMiBotica(datos) {
    await esperar()
    const botica = boticaActual()
    if (botica.estado !== 'RECHAZADO') {
      throw crearError(403, 'Solo puedes corregir tu botica cuando la solicitud fue rechazada.')
    }
    validarDatosBotica(datos)
    exigirRucLibre(datos.ruc)
    exigirTitular(duenoActual(), datos.ruc)
    Object.assign(botica, datosBotica(datos), { estado: 'PENDIENTE', motivoRechazo: null })
    return copia(boticaDto(botica))
  },

  async subirLicencia(archivo) {
    await esperar()
    const botica = boticaActual()
    const error = validarArchivoLicencia(archivo)
    if (error) throw crearError(400, error)
    if (!(await contenidoPermitido(archivo))) {
      throw crearError(400, 'El contenido del archivo no corresponde a un PDF, JPG o PNG.')
    }
    botica.tieneLicencia = true
    return { tieneLicencia: true, mensaje: 'Licencia cargada correctamente' }
  },

  // ---------- 4. Catálogo ----------
  // H12: con stockBajo solo los que tienen stock <= stockMinimo; se combina con q
  async listarMisProductos(q = '', { stockBajo = false } = {}) {
    await esperar()
    const botica = boticaActual()
    const busqueda = normalizar(q.trim())
    return db.productos
      .filter((p) => p.boticaId === botica.id && p.activo)
      .filter((p) => !busqueda || normalizar(p.nombreComercial).includes(busqueda) || normalizar(p.principioActivo).includes(busqueda))
      .filter((p) => !stockBajo || p.stock <= p.stockMinimo)
      .sort((a, b) => a.nombreComercial.localeCompare(b.nombreComercial, 'es'))
      .map(productoDto)
  },

  async crearProducto(datos) {
    await esperar()
    const botica = boticaActual()
    if (botica.estado !== 'APROBADO') {
      throw crearError(403, 'Tu botica debe estar aprobada para agregar productos.')
    }
    const { stockMinimo = STOCK_MINIMO_DEFECTO, ...campos } = validarProducto(datos)
    // Regla D2: enlazado, el principio activo es el del medicamento y se ignora el texto enviado
    const enlace = enlaceDelCuerpo(datos) ?? { medicamentoId: null }
    const producto = {
      id: siguienteId(db.productos), boticaId: botica.id, ...campos, stockMinimo, activo: true, ...enlace,
    }
    db.productos.push(producto)
    return productoDto(producto)
  },

  async actualizarProducto(id, datos) {
    await esperar()
    const producto = productoPropio(id)
    // Sin stockMinimo se conserva el valor actual
    const { stockMinimo = producto.stockMinimo, ...campos } = validarProducto(datos)
    // Regla nueva: el stock no baja de las unidades de pedidos CONFIRMADA y LISTA
    const comprometidas = unidadesComprometidas(producto.id)
    if (campos.stock < comprometidas) throw crearError(409, mensajeComprometidas(comprometidas))
    // Sin medicamentoId se conserva el enlace actual (enviar null no lo borra); con enlace, regla D2
    const enlace = enlaceDelCuerpo(datos)
    Object.assign(producto, campos, { stockMinimo }, enlace ?? {})
    if (!enlace && producto.medicamentoId) {
      producto.principioActivo = principioDelEnlace(producto) ?? producto.principioActivo
    }
    return productoDto(producto)
  },

  // D3: quita el enlace con el catálogo maestro; conserva el texto del principio activo
  async quitarEnlaceMedicamento(id) {
    await esperar()
    productoPropio(id).medicamentoId = null
    return null
  },

  async eliminarProducto(id) {
    await esperar()
    const producto = productoPropio(id)
    // Regla nueva: no se da de baja con reservas CONFIRMADA o LISTA
    if (unidadesComprometidas(producto.id) > 0) throw crearError(409, MENSAJE_BAJA_COMPROMETIDA)
    producto.activo = false // baja lógica
    return null
  },

  // H11: crea los nuevos y actualiza precio y stock (y stockMinimo si viene) de los que ya existen
  async cargarProductosCsv(archivo) {
    await esperar()
    const botica = db.boticas.find((b) => b.usuarioId === duenoActual().id)
    if (!botica) throw crearError(403, 'Primero debes registrar tu botica')
    if (botica.estado !== 'APROBADO') {
      throw crearError(403, `Tu botica aún no está aprobada (estado: ${botica.estado})`)
    }
    const { validas, errores } = await leerFilasProductos(archivo)

    const existentes = new Map()
    for (const p of db.productos.filter((p) => p.boticaId === botica.id && p.activo)) {
      const clave = claveProducto(p.nombreComercial, p.presentacion)
      if (!existentes.has(clave)) existentes.set(clave, p)
    }
    let creados = 0
    let actualizados = 0
    for (const { stockMinimo, fila: numeroFila, medicamentoId, ...fila } of validas) {
      // D4: un medicamento inexistente u oculto deja la fila como error y las demás siguen
      const enlace = medicamentoId === null ? null : enlaceMedicamento(medicamentoId)
      if (enlace?.error) {
        errores.push({ fila: numeroFila, mensaje: enlace.error })
        continue
      }
      let producto = existentes.get(claveProducto(fila.nombreComercial, fila.presentacion))
      if (producto) {
        // Regla nueva, igual que al editar: la fila queda como error y las demás siguen
        const comprometidas = unidadesComprometidas(producto.id)
        if (fila.stock < comprometidas) {
          errores.push({ fila: numeroFila, mensaje: mensajeComprometidas(comprometidas) })
          continue
        }
        Object.assign(producto, { precio: fila.precio, stock: fila.stock })
        if (stockMinimo !== undefined) producto.stockMinimo = stockMinimo
        actualizados++
      } else {
        producto = {
          id: siguienteId(db.productos), boticaId: botica.id, ...fila,
          stockMinimo: stockMinimo ?? STOCK_MINIMO_DEFECTO, activo: true, medicamentoId: null,
        }
        db.productos.push(producto)
        creados++
      }
      // Regla D2: enlazado, el principio activo es el del medicamento y se ignora el texto de la fila
      if (enlace) Object.assign(producto, { medicamentoId, principioActivo: enlace.principioActivo })
    }
    errores.sort((a, b) => a.fila - b.fila)
    return { creados, actualizados, errores }
  },

  // ---------- 10. Pedidos del dueño ----------
  // H77, H81: del más reciente al más antiguo, máximo 100; estado opcional
  async listarPedidos(estado) {
    await esperar()
    const botica = boticaActual()
    validarEstado(estado, false)
    vencerReservas()
    return reservasDeBotica(botica)
      .filter((r) => !estado || r.estado === estado)
      .sort((a, b) => b.creadoEn.localeCompare(a.creadoEn))
      .slice(0, 100)
      .map(pedidoDto)
  },

  // H78
  async obtenerPedido(id) {
    await esperar()
    vencerReservas()
    return pedidoDto(pedidoPropio(id))
  },

  // H79: PENDIENTE -> CONFIRMADA -> LISTA -> ENTREGADA, sin saltos ni vuelta atrás
  async cambiarEstadoPedido(id, estado) {
    await esperar()
    validarEstado(estado, true)
    const reserva = pedidoPropio(id)
    if (TRANSICIONES[reserva.estado] !== estado) {
      throw crearError(409, `No se puede pasar de ${reserva.estado} a ${estado}`)
    }
    if (yaVencio(reserva)) throw crearError(409, 'La reserva ya venció')
    const producto = db.productos.find((p) => p.id === reserva.productoId)

    if (estado === 'CONFIRMADA') {
      const disponible = Math.max(producto.stock - apartadasPorOtras(reserva), 0)
      if (disponible < reserva.cantidad) {
        throw crearError(409, `No hay stock suficiente para aceptar este pedido: disponible ${disponible}, pedido ${reserva.cantidad}. Recházalo con un motivo.`)
      }
    } else if (estado === 'LISTA') {
      reserva.venceEn = enMs(PLAZO_RECOGER_MS)
    } else if (estado === 'ENTREGADA') {
      // Respaldo: con las unidades comprometidas ya no debería pasar (ningún dato de prueba lo dispara)
      if (producto.stock < reserva.cantidad) throw crearError(409, `Stock insuficiente: quedan ${producto.stock} unidades`)
      producto.stock -= reserva.cantidad
    }
    Object.assign(reserva, { estado, actualizadoEn: ahoraIso() })
    return pedidoDto(reserva)
  },

  // H80: solo desde PENDIENTE o CONFIRMADA, con motivo de 10 a 300 caracteres
  async rechazarPedido(id, motivo) {
    await esperar()
    const motivoLimpio = String(motivo ?? '').trim()
    if (motivoLimpio.length < 10 || motivoLimpio.length > 300) {
      throw crearError(400, 'El motivo debe tener entre 10 y 300 caracteres.')
    }
    const reserva = pedidoPropio(id)
    if (!['PENDIENTE', 'CONFIRMADA'].includes(reserva.estado)) {
      throw crearError(409, `No se puede rechazar un pedido ${reserva.estado}`)
    }
    if (yaVencio(reserva)) throw crearError(409, 'La reserva ya venció')
    Object.assign(reserva, { estado: 'RECHAZADA', motivoRechazo: motivoLimpio, actualizadoEn: ahoraIso() })
    return pedidoDto(reserva)
  },

  // H31: catálogo maestro (hasta 20, sin ocultos, por nombre comercial)
  async buscarMedicamentos(q) {
    await esperar()
    duenoActual()
    const termino = texto(q).toLowerCase()
    if (termino.length < 2) throw crearError(400, 'La búsqueda requiere mínimo 2 caracteres')
    return db.medicamentos
      .filter((m) => !m.oculto)
      .filter((m) => `${m.nombreComercial}\n${m.principioActivo}`.toLowerCase().includes(termino))
      .sort((a, b) => a.nombreComercial.localeCompare(b.nombreComercial, 'es'))
      .slice(0, 20)
      .map(medicamentoDto)
  },

  // ---------- 11. Reseñas ----------
  // H87: público, páginas de 10, sin las ocultas
  async listarResenas(boticaId, page = 0) {
    await esperar()
    if (!/^\d+$/.test(String(boticaId)) || !/^\d+$/.test(String(page))) {
      throw crearError(400, 'El id de la botica y la página deben ser números enteros desde 0.')
    }
    const botica = db.boticas.find((b) => b.id === Number(boticaId) && b.estado === 'APROBADO')
    if (!botica) throw crearError(404, 'La botica no existe o no está aprobada.')
    const visibles = db.resenas
      .filter((r) => r.boticaId === botica.id && !r.oculta)
      .sort((a, b) => b.creadoEn.localeCompare(a.creadoEn))
    const suma = visibles.reduce((total, r) => total + r.estrellas, 0)
    return {
      boticaId: botica.id,
      promedio: visibles.length ? Math.round((suma / visibles.length) * 10) / 10 : null,
      total: visibles.length,
      page: Number(page),
      totalPages: Math.ceil(visibles.length / 10),
      resenas: visibles
        .slice(page * 10, page * 10 + 10)
        .map(({ id, usuarioId, estrellas, comentario, respuesta, respondidaEn, creadoEn, actualizadoEn }) => ({
          id, autor: nombreCorto(db.usuarios.find((u) => u.id === usuarioId)),
          estrellas, comentario, respuesta, respondidaEn, creadoEn, actualizadoEn,
        })),
      miResena: null,
    }
  },

  // ---------- 12. Notificaciones del dueño (H103) ----------
  // Últimos 7 días, de la más reciente a la más antigua, máximo 50. Sin botica: lista vacía, no 404
  async listarNotificaciones({ soloNoLeidas = false } = {}) {
    await esperar()
    const propias = misNotificacionesRecientes()
    if (typeof soloNoLeidas !== 'boolean') throw crearError(400, 'soloNoLeidas debe ser true o false.')
    return propias
      .filter((n) => !soloNoLeidas || !n.leida)
      .sort((a, b) => b.creadoEn.localeCompare(a.creadoEn) || b.id - a.id)
      .slice(0, MAX_NOTIFICACIONES)
      .map(notificacionDto)
  },

  // Cuenta las no leídas de los últimos 7 días, sin el límite de 50
  async contarNotificacionesNoLeidas() {
    await esperar()
    return { total: misNotificacionesRecientes().filter((n) => !n.leida).length }
  },

  // 204 también si ya estaba leída; 404 si no existe o es de otro usuario
  async marcarNotificacionLeida(id) {
    await esperar()
    const propias = misNotificaciones()
    if (!/^\d+$/.test(String(id))) throw crearError(400, 'El id de la notificación debe ser numérico.')
    const notificacion = propias.find((n) => n.id === Number(id))
    if (!notificacion) throw crearError(404, 'La notificación no existe')
    notificacion.leida = true
    return null
  },

  async marcarNotificacionesLeidas() {
    await esperar()
    for (const n of misNotificaciones()) n.leida = true
    return null
  },

  // ---------- 8. Asistente (H68) ----------
  async enviarChat(pedido, { signal } = {}) {
    const usuario = usuarioActual()
    const mensaje = validarPedidoChat(pedido)
    await esperarCancelable(ESPERA_CHAT_MS, signal)

    const consulta = normalizar(mensaje)
    const simulado = Object.keys(ERRORES_CHAT).find((clave) => consulta.includes(clave))
    if (simulado) throw crearError(...ERRORES_CHAT[simulado])

    // respaldo y ayuda valen false salvo que la respuesta diga lo contrario
    return { respaldo: false, ayuda: false, ...responderChat(usuario, mensaje, pedido) }
  },
}

/** Elige la respuesta simulada según la intención del mensaje. */
function responderChat(usuario, mensaje, { lat, lng }) {
  const consulta = normalizar(mensaje)
  const esDueno = usuario.rol === 'DUENO_BOTICA'

  // Filtro de crisis: va antes que todo lo demás, sin tarjetas. Si hay frases de los dos grupos, gana autolesión
  if (FRASES_AUTOLESION.some((f) => consulta.includes(f))) return { respuesta: TEXTO_AUTOLESION, productos: [], ayuda: true }
  if (FRASES_EMERGENCIA.some((f) => consulta.includes(f))) return { respuesta: TEXTO_EMERGENCIA, productos: [], ayuda: true }
  if (consulta.includes('respaldo')) {
    // Como si la llamada final a Gemini fallara después de que las herramientas encontraron productos
    const mencionado = principioMencionado(mensaje)
    const encontrados = mencionado ? buscarPreciosChat(mencionado, lat, lng) : []
    const productos = encontrados.length ? encontrados : buscarPreciosChat('Paracetamol', lat, lng)
    return { respuesta: textoRespaldo(productos, usuario), productos, respaldo: true }
  }
  if (PIDE_CONSEJO.test(consulta)) return { respuesta: TEXTO_FUERA_DE_ALCANCE, productos: [] }
  if (esDueno && PIDE_STOCK_BAJO.test(consulta)) return respuestaStockBajo(usuario)
  if (esDueno && PIDE_CONSEJO_NEGOCIO.test(consulta)) {
    // Consejo de surtido: tipos de producto, sin marcas, precios ni stock (instrucciones del dueño)
    return { respuesta: textoLista('En invierno se piden más estos tipos de producto. Revisa si los tienes y ponlos a la vista:', CONSEJO_INVIERNO), productos: [] }
  }
  if (esDueno && PIDE_MI_CATALOGO.test(consulta)) return respuestaMiCatalogo(usuario, mensaje)

  const principio = principioMencionado(mensaje)
  if (principio) {
    const productos = buscarPreciosChat(principio, lat, lng)
    const respuesta = productos.length
      ? textoPrecios(principio, productos, usuario)
      : `No encontré ${principio.toLowerCase()} con stock en las boticas aprobadas. Prueba con otro nombre o con el principio activo.`
    return { respuesta, productos }
  }
  return { respuesta: textoAyuda(usuario), productos: [] }
}

// Ayuda para pruebas en modo simulado: desde la consola del navegador se
// puede imitar lo que haría el administrador en Django.
//   boticomparaMock.aprobar(3)
//   boticomparaMock.rechazar(3, 'La dirección no coincide con la ficha RUC')
//   boticomparaMock.ocultarMedicamento(12)
//   boticomparaMock.pedidos()                      (H77: pedidos de todas las boticas)
//   boticomparaMock.vencerPedido(1)                (H79: venceEn pasa a ayer, para el 409 "La reserva ya venció")
//   boticomparaMock.cancelarPedido(1)              (H79: el comprador lo cancela desde el móvil)
//   boticomparaMock.nuevaReserva(1)                (H103: un comprador reserva el producto 1 y llega la notificación)
//   boticomparaMock.condicionVenta(24, 'RECETA_RETENIDA')  (H72: cambia la condición de un medicamento; null = sin información)
export function registrarAyudaConsola() {
  const cambiarEstado = (id, estado, motivoRechazo = null) => {
    const botica = db.boticas.find((b) => b.id === Number(id))
    if (!botica) return `No existe la botica ${id}`
    Object.assign(botica, { estado, motivoRechazo })
    return `${botica.nombreComercial}: ${estado}`
  }
  window.boticomparaMock = {
    boticas: () => db.boticas.map(({ id, nombreComercial, estado }) => ({ id, nombreComercial, estado })),
    aprobar: (id) => cambiarEstado(id, 'APROBADO'),
    rechazar: (id, motivo = 'Datos incompletos') => cambiarEstado(id, 'RECHAZADO', motivo),
    pendiente: (id) => cambiarEstado(id, 'PENDIENTE'),
    // D3: imita que el administrador oculte o vuelva a mostrar un medicamento del catálogo maestro
    ocultarMedicamento: (id, oculto = true) => {
      const medicamento = db.medicamentos.find((m) => m.id === Number(id))
      if (!medicamento) return `No existe el medicamento ${id}`
      medicamento.oculto = oculto
      return `${medicamento.nombreComercial}: ${oculto ? 'oculto' : 'visible'}`
    },
    // H72: imita que se cargue en la BD la condición de venta de un medicamento
    condicionVenta: (id, condicion = null) => {
      const medicamento = db.medicamentos.find((m) => m.id === Number(id))
      if (!medicamento) return `No existe el medicamento ${id}`
      if (condicion !== null && !CONDICIONES_VENTA.includes(condicion)) {
        return `La condición debe ser una de: ${CONDICIONES_VENTA.join(', ')} o null`
      }
      medicamento.condicionVenta = condicion
      return `${medicamento.nombreComercial}: ${condicion ?? 'sin información'}`
    },
    pedidos: () => db.reservas.map((r) => ({ ...pedidoDto(r), boticaId: db.productos.find((p) => p.id === r.productoId).boticaId })),
    // No cambia el estado: el proceso de H84 lo marca VENCIDA en la siguiente consulta
    vencerPedido: (id) => {
      const reserva = db.reservas.find((r) => r.id === Number(id))
      if (!reserva) return `No existe el pedido ${id}`
      reserva.venceEn = haceMs(24 * HORA)
      return `${reserva.codigo}: venceEn en el pasado (sigue ${reserva.estado} hasta la próxima consulta)`
    },
    cancelarPedido: (id) => {
      const reserva = db.reservas.find((r) => r.id === Number(id))
      if (!reserva) return `No existe el pedido ${id}`
      if (!ESTADOS_ACTIVOS.includes(reserva.estado)) return `No se puede cancelar una reserva ${reserva.estado}`
      Object.assign(reserva, { estado: 'CANCELADA', actualizadoEn: ahoraIso() })
      crearNotificacion('RESERVA_CANCELADA', reserva)
      return `${reserva.codigo}: CANCELADA`
    },
    // H103: imita una reserva nueva desde la app, con su notificación al dueño
    nuevaReserva: (productoId = 1, cantidad = 1) =>
      simularReserva(productoId, cantidad)?.codigo ?? `No existe el producto ${productoId}`,
  }
}
