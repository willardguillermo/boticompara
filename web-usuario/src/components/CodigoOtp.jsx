// H17, H20 - Código de 6 dígitos en 6 casillas (registro, verificación del login y recuperar contraseña).
// - Pegar el código completo en cualquier casilla lo reparte; el autocompletado del celular (one-time-code) también.
// - Flechas, Inicio, Fin, Backspace, Supr y Tab funcionan como en un campo normal.
// - Un solo rótulo para el grupo ("Código de 6 dígitos") y el error se anuncia con role="alert".
// - `fase` dibuja la verificación: las casillas vuelan al centro y se funden en una cruz de botica hecha de LEDs,
//   que se enciende como el letrero mientras se espera; luego se vuelve check (éxito) o falla en rojo y se rompe en
//   las 6 casillas (error). Solo se animan transform y opacity; con prefers-reduced-motion todo es un fundido.
import { useEffect, useId, useLayoutEffect, useRef } from 'react'
import useMenosMovimiento from '../hooks/useMenosMovimiento.js'
import { borrar, casillas, codigoCompleto, escribir, LARGO_OTP, textoNuevo } from '../utils/codigoOtp.js'
import { puntosCruz } from '../utils/cruzLed.js'
import './CodigoOtp.css'

const SACUDIDA = [
  { transform: 'translateX(0)' },
  { transform: 'translateX(-7px)' },
  { transform: 'translateX(6px)' },
  { transform: 'translateX(-4px)' },
  { transform: 'translateX(2px)' },
  { transform: 'translateX(0)' },
]

// 45 LEDs en una cuadrícula de 9 x 9 (10 unidades entre puntos)
const LEDS = puntosCruz(9, 3)
const PASO = 10
const RADIO = 3.3

/** Fases: 'escribiendo' | 'reunir' (vuela y se enciende) | 'fija' | 'exito' | 'falla' | 'volver'. */
const FASES_OCUPADAS = new Set(['reunir', 'fija', 'exito', 'falla', 'volver'])

/**
 * valor: texto de hasta 6 caracteres (un espacio es una casilla vacía). onCambio(valor).
 * onCompleto(codigo): al quedar los 6 dígitos. error: mensaje bajo las casillas.
 * sacudir: número que cambia con cada código incorrecto (sacude y vuelve el foco a la primera casilla).
 * fase: el momento de la verificación (ver arriba). autoFocus: enfoca la primera casilla al aparecer.
 */
export default function CodigoOtp({
  valor,
  onCambio,
  onCompleto,
  error,
  sacudir = 0,
  fase = 'escribiendo',
  deshabilitado = false,
  autoFocus = false,
  etiqueta = 'Código de 6 dígitos',
}) {
  const id = useId()
  const grupo = useRef(null)
  const celdas = useRef([])
  const entradas = useRef([])
  const menosMovimiento = useMenosMovimiento()
  const lista = casillas(valor)
  const ocupado = deshabilitado || FASES_OCUPADAS.has(fase)

  function enfocar(indice) {
    const entrada = entradas.current[Math.min(Math.max(indice, 0), LARGO_OTP - 1)]
    entrada?.focus()
    entrada?.select()
  }

  useEffect(() => {
    if (autoFocus) enfocar(0)
  }, [autoFocus])

  // Cuánto se mueve cada casilla para llegar al centro de la fila (offsetLeft no cambia con transform)
  useLayoutEffect(() => {
    const contenedor = grupo.current
    if (!contenedor || fase === 'escribiendo') return
    const centro = contenedor.clientWidth / 2
    for (const celda of celdas.current) {
      if (celda) celda.style.setProperty('--dx', `${centro - (celda.offsetLeft + celda.offsetWidth / 2)}px`)
    }
  }, [fase])

  // Código incorrecto: sacudida suave (Web Animations, se puede repetir sin volver a montar) y foco al inicio
  useEffect(() => {
    if (!sacudir) return
    enfocar(0)
    if (!menosMovimiento) grupo.current?.animate?.(SACUDIDA, { duration: 420, easing: 'ease-out' })
  }, [sacudir, menosMovimiento])

  function aplicar({ valor: nuevo, foco }) {
    // Mientras se verifica no se cambia el código (por ejemplo, un pegado que llegue tarde)
    if (ocupado) return
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

  const clases = ['otp', error && 'otp-con-error'].filter(Boolean).join(' ')

  return (
    <div className={clases} data-fase={fase}>
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
          <span
            // Las casillas son fijas: el índice es su identidad
            key={i}
            ref={(el) => {
              celdas.current[i] = el
            }}
            className="otp-celda"
            data-llena={digito ? 'true' : 'false'}
            style={{ '--i': i }}
          >
            <input
              ref={(el) => {
                entradas.current[i] = el
              }}
              className="otp-casilla"
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              autoComplete="one-time-code"
              aria-label={`Dígito ${i + 1} de ${LARGO_OTP}`}
              aria-invalid={Boolean(error)}
              value={digito}
              disabled={ocupado}
              onChange={(e) => alCambiar(i, e)}
              onKeyDown={(e) => alTeclear(i, e)}
              onPaste={(e) => alPegar(i, e)}
              onFocus={(e) => e.target.select()}
            />
          </span>
        ))}

        {/* La escena de la verificación: decorativa, el estado lo anuncia quien usa el componente */}
        <span className="otp-escena" aria-hidden="true">
          <span className="otp-halo" />
          <svg className="otp-cruz" viewBox={`0 0 ${PASO * 9} ${PASO * 9}`}>
            {LEDS.map(({ columna, fila, d }) => {
              const cx = PASO / 2 + columna * PASO
              const cy = PASO / 2 + fila * PASO
              return (
                <g key={`${columna}-${fila}`} style={{ '--d': d }}>
                  <circle className="led-base" cx={cx} cy={cy} r={RADIO} />
                  <circle className="led-luz" cx={cx} cy={cy} r={RADIO} />
                  <circle className="led-falla" cx={cx} cy={cy} r={RADIO} />
                </g>
              )
            })}
          </svg>
          <span className="otp-onda" />
          <span className="otp-onda otp-onda-2" />
          <svg className="otp-check" viewBox="0 0 52 52">
            <circle className="otp-check-fondo" cx="26" cy="26" r="24" />
            <path className="otp-check-trazo" d="M15 27.5l7.5 7.5L38 19" pathLength="1" />
          </svg>
        </span>
      </div>
      <span id={`${id}-error`} className="error-campo otp-error" role="alert">
        {error}
      </span>
    </div>
  )
}
