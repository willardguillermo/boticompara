// Implementación simulada en memoria: mismo contrato que docs/API.md,
// con los usuarios, boticas y productos de prueba de database/seed.sql.
// Los datos se reinician al recargar la página.
import { validarRuc } from '../utils/ruc.js'
import { crearError } from './ApiError.js'
import { obtenerToken } from './sesion.js'

const PASSWORD_PRUEBA = 'Boti2026!'

const db = {
  usuarios: [
    { id: 1, nombre: 'Rosa Quispe', correo: 'rosa@boticasalud.pe', password: PASSWORD_PRUEBA, telefono: '987654321', direccion: null, rol: 'DUENO_BOTICA' },
    { id: 2, nombre: 'Jorge Mendoza', correo: 'jorge@farmavida.pe', password: PASSWORD_PRUEBA, telefono: '976543210', direccion: null, rol: 'DUENO_BOTICA' },
    { id: 3, nombre: 'Lucía Torres', correo: 'lucia@boticanueva.pe', password: PASSWORD_PRUEBA, telefono: '965432109', direccion: null, rol: 'DUENO_BOTICA' },
    { id: 4, nombre: 'Carlos Ramírez', correo: 'carlos@correo.com', password: PASSWORD_PRUEBA, telefono: '954321098', direccion: 'Av. Lima 123, Chosica', rol: 'COMPRADOR' },
  ],
  boticas: [
    { id: 1, usuarioId: 1, nombreComercial: 'Botica Salud Total', ruc: '20601234565', razonSocial: 'Salud Total Farma S.A.C.', direccion: 'Av. Lima Sur 450', distrito: 'Lurigancho-Chosica', telefono: '014567890', estado: 'APROBADO', motivoRechazo: null },
    { id: 2, usuarioId: 2, nombreComercial: 'FarmaVida', ruc: '20549871233', razonSocial: 'FarmaVida Perú E.I.R.L.', direccion: 'Jr. Trujillo 210', distrito: 'Lurigancho-Chosica', telefono: '014561234', estado: 'APROBADO', motivoRechazo: null },
    { id: 3, usuarioId: 3, nombreComercial: 'Botica Nueva Era', ruc: '20712345676', razonSocial: 'Nueva Era Salud S.A.C.', direccion: 'Av. Nicolás Ayllón 980', distrito: 'Ate', telefono: '013459876', estado: 'PENDIENTE', motivoRechazo: null },
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
    id: i + 1, boticaId, nombreComercial, principioActivo, presentacion, precio, stock, activo: true,
  })),
}

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
const productoDto = ({ id, nombreComercial, principioActivo, presentacion, precio, stock }) =>
  ({ id, nombreComercial, principioActivo, presentacion, precio, stock })

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

function validarProducto(datos) {
  const textos = ['nombreComercial', 'principioActivo', 'presentacion']
  if (textos.some((c) => !String(datos?.[c] ?? '').trim())) {
    throw crearError(400, 'Nombre comercial, principio activo y presentación son obligatorios.')
  }
  const precio = Number(datos.precio)
  if (!Number.isFinite(precio) || precio <= 0) throw crearError(400, 'El precio debe ser mayor a 0.')
  const stock = Number(datos.stock)
  if (!Number.isInteger(stock) || stock < 0) throw crearError(400, 'El stock debe ser un número entero mayor o igual a 0.')
  return {
    nombreComercial: datos.nombreComercial.trim(),
    principioActivo: datos.principioActivo.trim(),
    presentacion: datos.presentacion.trim(),
    precio: Math.round(precio * 100) / 100,
    stock,
  }
}

export const apiMock = {
  // ---------- 1. Autenticación ----------
  async login({ correo, password }) {
    await esperar()
    const usuario = db.usuarios.find((u) => u.correo === String(correo ?? '').trim().toLowerCase())
    if (!usuario || usuario.password !== password) throw crearError(401, 'Correo o contraseña incorrectos.')
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

  // ---------- 2. Perfil ----------
  async obtenerPerfil() {
    await esperar()
    return perfil(usuarioActual())
  },

  // ---------- 3. Botica ----------
  async validarRuc(ruc) {
    await esperar()
    const { valido, mensaje } = validarRuc(ruc)
    return { ruc, valido, mensaje }
  },

  async registrarBotica(datos) {
    await esperar()
    const dueno = duenoActual()
    const obligatorios = ['nombreComercial', 'ruc', 'razonSocial', 'direccion', 'distrito']
    if (obligatorios.some((c) => !String(datos?.[c] ?? '').trim())) {
      throw crearError(400, 'Completa todos los datos obligatorios de la botica.')
    }
    if (!validarRuc(datos.ruc).valido) throw crearError(400, 'El RUC no es válido.')
    if (db.boticas.some((b) => b.usuarioId === dueno.id)) throw crearError(409, 'Ya registraste una botica con esta cuenta.')
    if (db.boticas.some((b) => b.ruc === datos.ruc)) throw crearError(409, 'El RUC ya está registrado.')

    const botica = {
      id: siguienteId(db.boticas), usuarioId: dueno.id,
      nombreComercial: datos.nombreComercial.trim(), ruc: datos.ruc,
      razonSocial: datos.razonSocial.trim(), direccion: datos.direccion.trim(),
      distrito: datos.distrito.trim(), telefono: datos.telefono?.trim() || null,
      estado: 'PENDIENTE', motivoRechazo: null,
    }
    db.boticas.push(botica)
    const { id, nombreComercial, ruc, estado } = botica
    return { id, nombreComercial, ruc, estado }
  },

  async obtenerMiBotica() {
    await esperar()
    return copia(boticaDto(boticaActual()))
  },

  // ---------- 4. Catálogo ----------
  async listarMisProductos(q = '') {
    await esperar()
    const botica = boticaActual()
    const texto = normalizar(q.trim())
    return db.productos
      .filter((p) => p.boticaId === botica.id && p.activo)
      .filter((p) => !texto || normalizar(p.nombreComercial).includes(texto) || normalizar(p.principioActivo).includes(texto))
      .sort((a, b) => a.nombreComercial.localeCompare(b.nombreComercial, 'es'))
      .map(productoDto)
  },

  async crearProducto(datos) {
    await esperar()
    const botica = boticaActual()
    if (botica.estado !== 'APROBADO') {
      throw crearError(403, 'Tu botica debe estar aprobada para agregar productos.')
    }
    const producto = { id: siguienteId(db.productos), boticaId: botica.id, ...validarProducto(datos), activo: true }
    db.productos.push(producto)
    return productoDto(producto)
  },

  async actualizarProducto(id, datos) {
    await esperar()
    const producto = productoPropio(id)
    Object.assign(producto, validarProducto(datos))
    return productoDto(producto)
  },

  async eliminarProducto(id) {
    await esperar()
    productoPropio(id).activo = false // baja lógica
    return null
  },
}

// Ayuda para pruebas en modo simulado: desde la consola del navegador se
// puede imitar lo que haría el administrador en Django.
//   boticomparaMock.aprobar(3)
//   boticomparaMock.rechazar(3, 'La dirección no coincide con la ficha RUC')
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
  }
}
