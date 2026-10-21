// Implementación simulada en memoria: mismo contrato que docs/API.md,
// con los usuarios, boticas y productos de prueba de database/seed.sql.
// Los datos se reinician al recargar la página.
import { validarRuc } from '../utils/ruc.js'
import { MAX_BYTES_CSV, validarArchivoLicencia } from '../utils/validacion.js'
import { crearError } from './ApiError.js'
import { obtenerToken } from './sesion.js'

const PASSWORD_PRUEBA = 'Boti2026!'
const STOCK_MINIMO_DEFECTO = 5 // H12: igual que el default de la columna stock_minimo
// H2: único RUC que el modo simulado "encuentra" en SUNAT
const RUC_SUNAT = { ruc: '20601030013', razonSocial: 'REXTIE S.A.C.', estado: 'ACTIVO', condicion: 'HABIDO' }

const db = {
  usuarios: [
    { id: 1, nombre: 'Rosa Quispe', correo: 'rosa@boticasalud.pe', password: PASSWORD_PRUEBA, telefono: '987654321', direccion: null, rol: 'DUENO_BOTICA' },
    { id: 2, nombre: 'Jorge Mendoza', correo: 'jorge@farmavida.pe', password: PASSWORD_PRUEBA, telefono: '976543210', direccion: null, rol: 'DUENO_BOTICA' },
    { id: 3, nombre: 'Lucía Torres', correo: 'lucia@boticanueva.pe', password: PASSWORD_PRUEBA, telefono: '965432109', direccion: null, rol: 'DUENO_BOTICA' },
    { id: 4, nombre: 'Carlos Ramírez', correo: 'carlos@correo.com', password: PASSWORD_PRUEBA, telefono: '954321098', direccion: 'Av. Lima 123, Chosica', rol: 'COMPRADOR' },
  ],
  boticas: [
    { id: 1, usuarioId: 1, nombreComercial: 'Botica Salud Total', ruc: '20601234565', razonSocial: 'Salud Total Farma S.A.C.', direccion: 'Av. Lima Sur 450', distrito: 'Lurigancho-Chosica', telefono: '014567890', latitud: -11.942500, longitud: -76.699000, tieneLicencia: true, estado: 'APROBADO', motivoRechazo: null },
    { id: 2, usuarioId: 2, nombreComercial: 'FarmaVida', ruc: '20549871233', razonSocial: 'FarmaVida Perú E.I.R.L.', direccion: 'Jr. Trujillo 210', distrito: 'Lurigancho-Chosica', telefono: '014561234', latitud: -11.935100, longitud: -76.694300, tieneLicencia: true, estado: 'APROBADO', motivoRechazo: null },
    { id: 3, usuarioId: 3, nombreComercial: 'Botica Nueva Era', ruc: '20712345676', razonSocial: 'Nueva Era Salud S.A.C.', direccion: 'Av. Nicolás Ayllón 980', distrito: 'Ate', telefono: '013459876', latitud: null, longitud: null, tieneLicencia: false, estado: 'PENDIENTE', motivoRechazo: null },
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
const productoDto = ({ id, nombreComercial, principioActivo, presentacion, precio, stock, stockMinimo }) =>
  ({ id, nombreComercial, principioActivo, presentacion, precio, stock, stockMinimo })

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
  if (archivo.size > MAX_BYTES_CSV) throw crearError(400, 'El archivo supera el máximo de 5 MB')
  if (archivo.size === 0) throw crearError(400, 'El archivo está vacío')
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
    throw crearError(400, 'Encabezado inválido. Se esperan las columnas nombreComercial,principioActivo,presentacion,precio,stock[,stockMinimo] separadas por coma')
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
    if (error) {
      errores.push({ fila: linea, mensaje: error })
      continue
    }
    const clave = claveProducto(producto.nombreComercial, producto.presentacion)
    if (vistos.has(clave)) {
      errores.push({ fila: linea, mensaje: `Producto duplicado en el archivo (ya aparece en la fila ${vistos.get(clave)})` })
      continue
    }
    vistos.set(clave, linea)
    validas.push(producto)
  }
  return { validas, errores }
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
  // H2: simula la consulta a SUNAT solo para RUC_SUNAT; los demás RUC válidos salen como LOCAL
  async validarRuc(ruc) {
    await esperar()
    const { valido, mensaje } = validarRuc(ruc)
    if (valido && ruc === RUC_SUNAT.ruc) return { ...RUC_SUNAT, valido, mensaje, fuente: 'SUNAT' }
    return {
      ruc, valido,
      mensaje: valido ? 'RUC válido (no se pudo consultar SUNAT)' : mensaje,
      razonSocial: null, estado: null, condicion: null, fuente: 'LOCAL',
    }
  },

  async registrarBotica(datos) {
    await esperar()
    const dueno = duenoActual()
    validarDatosBotica(datos)
    if (db.boticas.some((b) => b.usuarioId === dueno.id)) throw crearError(409, 'Ya registraste una botica con esta cuenta.')
    if (db.boticas.some((b) => b.ruc === datos.ruc)) throw crearError(409, 'El RUC ya está registrado.')

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
    if (db.boticas.some((b) => b.ruc === datos.ruc && b.id !== botica.id)) {
      throw crearError(409, 'El RUC ya está registrado por otra botica.')
    }
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
    const producto = { id: siguienteId(db.productos), boticaId: botica.id, ...campos, stockMinimo, activo: true }
    db.productos.push(producto)
    return productoDto(producto)
  },

  async actualizarProducto(id, datos) {
    await esperar()
    const producto = productoPropio(id)
    // Sin stockMinimo se conserva el valor actual
    const { stockMinimo = producto.stockMinimo, ...campos } = validarProducto(datos)
    Object.assign(producto, campos, { stockMinimo })
    return productoDto(producto)
  },

  async eliminarProducto(id) {
    await esperar()
    productoPropio(id).activo = false // baja lógica
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
    for (const { stockMinimo, ...fila } of validas) {
      const existente = existentes.get(claveProducto(fila.nombreComercial, fila.presentacion))
      if (existente) {
        Object.assign(existente, { precio: fila.precio, stock: fila.stock })
        if (stockMinimo !== undefined) existente.stockMinimo = stockMinimo
        actualizados++
      } else {
        db.productos.push({
          id: siguienteId(db.productos), boticaId: botica.id, ...fila,
          stockMinimo: stockMinimo ?? STOCK_MINIMO_DEFECTO, activo: true,
        })
        creados++
      }
    }
    return { creados, actualizados, errores }
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
