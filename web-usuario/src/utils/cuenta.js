// H16 y H19 - Mi cuenta: reglas sin React, para probarlas con node --test.
// Límites de PUT /usuarios/me (docs/API.md, sección 2; PerfilRequest del backend y columnas de usuario).

export const MAX_NOMBRE = 100
export const MAX_TELEFONO = 20
export const MAX_DIRECCION = 255

/** Palabra que hay que escribir para habilitar "Eliminar mi cuenta". */
export const PALABRA_ELIMINAR = 'ELIMINAR'

/** Campos editables del perfil como textos (null del servidor pasa a ''). */
export function formularioDesdePerfil(perfil) {
  return {
    nombre: perfil?.nombre ?? '',
    telefono: perfil?.telefono ?? '',
    direccion: perfil?.direccion ?? '',
  }
}

/** Errores por campo, con las mismas reglas que el backend. Vacío si todo está bien. */
export function validarPerfil({ nombre, telefono, direccion }) {
  const errores = {}
  const n = nombre.trim()
  if (!n) errores.nombre = 'Ingresa tu nombre.'
  else if (n.length > MAX_NOMBRE) errores.nombre = `El nombre admite máximo ${MAX_NOMBRE} caracteres.`
  if (telefono.trim().length > MAX_TELEFONO) {
    errores.telefono = `El teléfono admite máximo ${MAX_TELEFONO} caracteres.`
  }
  if (direccion.trim().length > MAX_DIRECCION) {
    errores.direccion = `La dirección admite máximo ${MAX_DIRECCION} caracteres.`
  }
  return errores
}

/** Cuerpo de PUT /usuarios/me: sin correo ni rol; teléfono y dirección vacíos van como null. */
export function cuerpoPerfil({ nombre, telefono, direccion }) {
  return {
    nombre: nombre.trim(),
    telefono: telefono.trim() || null,
    direccion: direccion.trim() || null,
  }
}

/** true si el formulario tiene algo distinto a lo guardado (ignora espacios de los extremos). */
export function hayCambios(perfil, formulario) {
  const guardado = cuerpoPerfil(formularioDesdePerfil(perfil))
  const nuevo = cuerpoPerfil(formulario)
  return Object.keys(nuevo).some((campo) => guardado[campo] !== nuevo[campo])
}

/** El botón de eliminar se habilita solo con ELIMINAR en mayúsculas (se toleran espacios alrededor). */
export function confirmacionValida(texto) {
  return String(texto ?? '').trim() === PALABRA_ELIMINAR
}

/** A7: RUC de persona natural (empieza con 10 o 15); su titular debe ser el nombre de la cuenta. */
export function esRucDePersona(ruc) {
  return /^(10|15)\d{9}$/.test(String(ruc ?? ''))
}

/**
 * A7: el 400 de POST /boticas o PUT /boticas/mia cuando la cuenta no está a nombre del titular.
 * Solo mira el principio, igual en el mensaje viejo ("... (según SUNAT: X)") y en el de S4-06 ("... del RUC").
 */
export const INICIO_ERROR_TITULAR = 'Este RUC pertenece a una persona'

export function esErrorDeTitular(mensaje) {
  return String(mensaje ?? '').trim().toLowerCase().startsWith(INICIO_ERROR_TITULAR.toLowerCase())
}

/** A7: mostrar en Mi cuenta el paso a paso de corregir el nombre y reenviar la botica. */
export function mostrarAyudaTitular(botica) {
  return botica?.estado === 'RECHAZADO' && esRucDePersona(botica.ruc)
}
