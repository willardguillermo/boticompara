// H17, H20 - Código de 6 dígitos en 6 casillas (registro, verificación del login y recuperar contraseña).
// - Pegar el código completo en cualquier casilla lo reparte; el autocompletado del celular (one-time-code) también.
// - Flechas, Inicio, Fin, Backspace, Supr y Tab funcionan como en un campo normal.
// - Un solo rótulo para el grupo ("Código de 6 dígitos") y el error se anuncia con role="alert".
// - Animaciones sutiles (llenado, brillo en el foco, sacudida al fallar, check al validar);
//   con prefers-reduced-motion no hay ninguna (CSS y la sacudida, que se hace con JS).
import { useEffect, useId, useRef } from 'react'
import useMenosMovimiento from '../hooks/useMenosMovimiento.js'
import { borrar, casillas, codigoCompleto, escribir, LARGO_OTP, textoNuevo } from '../utils/codigoOtp.js'
import Icono from './Icono.jsx'
import './CodigoOtp.css'

const SACUDIDA = [
  { transform: 'translateX(0)' },
  { transform: 'translateX(-6px)' },
  { transform: 'translateX(5px)' },
  { transform: 'translateX(-3px)' },
  { transform: 'translateX(2px)' },
  { transform: 'translateX(0)' },
]

/**
 * valor: texto de hasta 6 caracteres (un espacio es una casilla vacía). onCambio(valor).
 * onCompleto(codigo): al quedar los 6 dígitos. error: mensaje bajo las casillas.
 * sacudir: número que cambia con cada código incorrecto (sacude y vuelve el foco a la primera casilla).
 * validado: muestra el check. autoFocus: enfoca la primera casilla al aparecer.
 */
export default function CodigoOtp({
  valor,
  onCambio,
  onCompleto,
  error,
  sacudir = 0,
  validado = false,
  deshabilitado = false,
  autoFocus = false,
  etiqueta = 'Código de 6 dígitos',
}) {
  const id = useId()
  const grupo = useRef(null)
  const entradas = useRef([])
  const menosMovimiento = useMenosMovimiento()
  const lista = casillas(valor)

  function enfocar(indice) {
    const entrada = entradas.current[Math.min(Math.max(indice, 0), LARGO_OTP - 1)]
    entrada?.focus()
    entrada?.select()
  }

  useEffect(() => {
    if (autoFocus) enfocar(0)
  }, [autoFocus])

  // Código incorrecto: sacudida suave (Web Animations, se puede repetir sin volver a montar) y foco al inicio
  useEffect(() => {
    if (!sacudir) return
    enfocar(0)
    if (!menosMovimiento) grupo.current?.animate?.(SACUDIDA, { duration: 380, easing: 'ease-out' })
  }, [sacudir, menosMovimiento])

  function aplicar({ valor: nuevo, foco }) {
    if (nuevo !== valor) onCambio(nuevo)
    enfocar(foco)
    const codigo = codigoCompleto(nuevo)
    if (codigo && codigo !== codigoCompleto(valor)) onCompleto?.(codigo)
  }

  function alTeclear(indice, e) {
    if (e.ctrlKey || e.metaKey || e.altKey) return
    const acciones = {
      Backspace: () => aplicar(borrar(valor, indice)),
      Delete: () => aplicar(escribir(valor, indice, '')),
      ArrowLeft: () => enfocar(indice - 1),
      ArrowRight: () => enfocar(indice + 1),
      Home: () => enfocar(0),
      End: () => enfocar(LARGO_OTP - 1),
    }
    if (acciones[e.key]) {
      e.preventDefault()
      acciones[e.key]()
    } else if (/^\d$/.test(e.key)) {
      // Se maneja aquí para que escribir el mismo dígito que ya estaba también avance
      e.preventDefault()
      aplicar(escribir(valor, indice, e.key))
    }
  }

  // Teclados de celular que no avisan la tecla y el autocompletado del código
  function alCambiar(indice, e) {
    aplicar(escribir(valor, indice, textoNuevo(e.target.value, lista[indice], e.target.selectionStart)))
  }

  function alPegar(indice, e) {
    e.preventDefault()
    aplicar(escribir(valor, indice, e.clipboardData.getData('text')))
  }

  const clases = ['otp', error && 'otp-con-error', validado && 'otp-validado'].filter(Boolean).join(' ')

  return (
    <div className={clases}>
      <span id={`${id}-etiqueta`} className="otp-etiqueta">
        {etiqueta}
      </span>
      <div
        ref={grupo}
        className="otp-casillas"
        role="group"
        aria-labelledby={`${id}-etiqueta`}
        aria-describedby={error ? `${id}-error` : undefined}
      >
        {lista.map((digito, i) => (
          <input
            // Las casillas son fijas: el índice es su identidad
            key={i}
            ref={(el) => {
              entradas.current[i] = el
            }}
            className="otp-casilla"
            data-llena={digito ? 'true' : 'false'}
            type="text"
            inputMode="numeric"
            pattern="[0-9]*"
            autoComplete="one-time-code"
            aria-label={`Dígito ${i + 1} de ${LARGO_OTP}`}
            aria-invalid={Boolean(error)}
            value={digito}
            disabled={deshabilitado}
            onChange={(e) => alCambiar(i, e)}
            onKeyDown={(e) => alTeclear(i, e)}
            onPaste={(e) => alPegar(i, e)}
            onFocus={(e) => e.target.select()}
          />
        ))}
        {validado && (
          <span className="otp-check" aria-hidden="true">
            <Icono nombre="check" />
          </span>
        )}
      </div>
      <span id={`${id}-error`} className="error-campo otp-error" role="alert">
        {error}
      </span>
      <span className="sr-only" role="status">
        {validado ? 'Código verificado' : ''}
      </span>
    </div>
  )
}
