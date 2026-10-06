// Validación de RUC según docs/API.md (GET /boticas/validar-ruc/{ruc})
const PESOS = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2]
const PREFIJOS = ['10', '15', '17', '20']

/**
 * Valida formato y dígito verificador (módulo 11).
 * @returns {{ valido: boolean, completo: boolean, mensaje: string }}
 */
export function validarRuc(ruc) {
  const valor = String(ruc ?? '')
  if (valor === '') return { valido: false, completo: false, mensaje: 'Ingresa los 11 dígitos del RUC' }
  if (!/^\d+$/.test(valor)) return { valido: false, completo: false, mensaje: 'El RUC solo debe tener números' }
  if (valor.length >= 2 && !PREFIJOS.includes(valor.slice(0, 2))) {
    return { valido: false, completo: false, mensaje: 'El RUC debe empezar con 10, 15, 17 o 20' }
  }
  if (valor.length < 11) return { valido: false, completo: false, mensaje: `Faltan ${11 - valor.length} dígito(s)` }
  if (valor.length > 11) return { valido: false, completo: true, mensaje: 'El RUC debe tener 11 dígitos' }

  const suma = PESOS.reduce((total, peso, i) => total + peso * Number(valor[i]), 0)
  let digito = 11 - (suma % 11)
  if (digito === 10) digito = 0
  if (digito === 11) digito = 1

  return digito === Number(valor[10])
    ? { valido: true, completo: true, mensaje: 'RUC válido' }
    : { valido: false, completo: true, mensaje: 'El dígito verificador del RUC no es correcto' }
}
