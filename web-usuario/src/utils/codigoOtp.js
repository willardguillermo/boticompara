// H17, H20: lógica de las 6 casillas del código (CodigoOtp). El valor es un texto de hasta 6 caracteres
// donde un espacio es una casilla vacía, para poder borrar un dígito del medio sin mover los demás.

export const LARGO_OTP = 6
const VACIA = ' '

const soloDigitos = (texto) => String(texto ?? '').replace(/\D/g, '')

/** Las 6 casillas del valor: un dígito o '' si está vacía. */
export function casillas(valor) {
  const texto = String(valor ?? '')
  return Array.from({ length: LARGO_OTP }, (_, i) => (/\d/.test(texto[i] ?? '') ? texto[i] : ''))
}

/** Vuelve a armar el valor desde las casillas (sin espacios al final). */
export function unirCasillas(lista) {
  return lista.map((d) => d || VACIA).join('').trimEnd()
}

/** El código para enviar a la API: los 6 dígitos sin espacios, o '' si falta alguno. */
export function codigoCompleto(valor) {
  const texto = String(valor ?? '')
  return /^\d{6}$/.test(texto) ? texto : ''
}

/**
 * Escribe en la casilla `indice` lo que llegó (una tecla, un pegado o el autocompletado del celular).
 * - Sin dígitos: vacía la casilla.
 * - Un dígito: lo pone y el foco pasa a la siguiente.
 * - El código completo (6 o más dígitos), en cualquier casilla: lo reparte desde la primera.
 * - Varios dígitos sin llegar a 6: los reparte desde `indice`.
 * Devuelve { valor, foco } con la casilla que debe quedar enfocada.
 */
export function escribir(valor, indice, texto) {
  const lista = casillas(valor)
  const digitos = soloDigitos(texto)
  if (!digitos) {
    lista[indice] = ''
    return { valor: unirCasillas(lista), foco: indice }
  }
  const desde = digitos.length >= LARGO_OTP ? 0 : indice
  const aPoner = digitos.slice(0, LARGO_OTP - desde)
  for (let i = 0; i < aPoner.length; i++) lista[desde + i] = aPoner[i]
  const ultima = desde + aPoner.length - 1
  return { valor: unirCasillas(lista), foco: Math.min(ultima + 1, LARGO_OTP - 1) }
}

/**
 * Backspace en la casilla `indice`: si tiene un dígito lo borra y se queda ahí;
 * si ya estaba vacía, borra la anterior y el foco vuelve a ella.
 */
export function borrar(valor, indice) {
  const lista = casillas(valor)
  if (lista[indice]) {
    lista[indice] = ''
    return { valor: unirCasillas(lista), foco: indice }
  }
  const anterior = Math.max(indice - 1, 0)
  lista[anterior] = ''
  return { valor: unirCasillas(lista), foco: anterior }
}

/**
 * Qué escribió el usuario en una casilla que ya tenía `anterior` (teclados de celular que no avisan la tecla):
 * con 2 caracteres se toma el que quedó junto al cursor; con 1 o con un pegado largo, el texto tal cual.
 */
export function textoNuevo(texto, anterior, cursor) {
  const digitos = soloDigitos(texto)
  if (digitos.length === 2 && anterior) {
    const junto = texto[Math.max((cursor ?? texto.length) - 1, 0)]
    return /\d/.test(junto) ? junto : digitos.replace(anterior, '')
  }
  return digitos
}
