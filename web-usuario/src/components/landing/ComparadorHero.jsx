import { useEffect, useState } from 'react'
import useMenosMovimiento from '../../hooks/useMenosMovimiento.js'
import { formatoSoles } from '../../utils/formato.js'
import { ahorroDeEjemplo, EJEMPLOS_DEMO, proporcionPrecio, siguienteIndice } from '../../utils/landing.js'
import Icono from '../Icono.jsx'
import Counter from '../reactbits/Counter.jsx'

// Animación protagonista del hero: el mismo medicamento en dos boticas, con los pines en un mini
// mapa y el ahorro. Rota entre los ejemplos de la demo cada pocos segundos; se detiene al pasar el
// puntero o el foco, con el botón de pausa (WCAG 2.2.2), al elegir un ejemplo y con movimiento
// reducido.
const INTERVALO_MS = 6000

function MapaDemo({ ejemplo }) {
  return (
    <svg className="lp-mapa" viewBox="0 0 340 170" role="img" aria-label={`Mapa ilustrativo: tu ubicación, ${ejemplo.caro.botica} y ${ejemplo.barato.botica}`}>
      <rect className="lp-mapa-fondo" x="0" y="0" width="340" height="170" rx="14" />
      <path className="lp-mapa-parque" d="M206 104h70a8 8 0 0 1 8 8v40a8 8 0 0 1-8 8h-70a8 8 0 0 1-8-8v-40a8 8 0 0 1 8-8z" />
      <path className="lp-mapa-rio" d="M-10 30C40 18 70 44 112 34s66-30 110-16 78 2 128-8" />
      <g className="lp-mapa-calles">
        <path d="M0 74H340M0 132H340M64 0V170M168 0V170M268 0V96" />
      </g>
      <g className="lp-mapa-cuadras">
        <path d="M0 74H340M0 132H340M64 0V170M168 0V170M268 0V96" />
      </g>
      {/* Ruta de tu ubicación a la botica más barata, por las calles */}
      <path key={`ruta-${ejemplo.id}`} className="lp-mapa-ruta" d="M64 132H168V74" pathLength="1" />
      <g className="lp-mapa-tu" transform="translate(64 132)">
        <circle className="lp-mapa-tu-onda" r="16" />
        <circle className="lp-mapa-tu-punto" r="7" />
      </g>
      <text className="lp-mapa-etiqueta" x="46" y="160">Tú</text>

      <g key={`caro-${ejemplo.id}`} className="lp-pin lp-pin-caro" transform="translate(268 96)">
        <g className="lp-pin-cuerpo">
          <path d="M0 0c-9-11-14-17-14-25a14 14 0 0 1 28 0c0 8-5 14-14 25z" />
          <circle cy="-25" r="5" />
        </g>
        <g className="lp-pin-precio" transform="translate(-34 -70)">
          <rect width="68" height="24" rx="12" />
          <text x="34" y="16.5">{formatoSoles(ejemplo.caro.precio)}</text>
        </g>
      </g>
      <g key={`barato-${ejemplo.id}`} className="lp-pin lp-pin-barato" transform="translate(168 74)">
        <g className="lp-pin-cuerpo">
          <path d="M0 0c-9-11-14-17-14-25a14 14 0 0 1 28 0c0 8-5 14-14 25z" />
          <circle cy="-25" r="5" />
        </g>
        <g className="lp-pin-precio" transform="translate(-34 -70)">
          <rect width="68" height="24" rx="12" />
          <text x="34" y="16.5">{formatoSoles(ejemplo.barato.precio)}</text>
        </g>
      </g>
    </svg>
  )
}

function FilaPrecio({ dato, mayor, barato }) {
  return (
    <li className={`lp-fila${barato ? ' lp-fila-barata' : ''}`}>
      <div className="lp-fila-datos">
        <span className="lp-fila-botica">
          <Icono nombre="pin" /> {dato.botica}
        </span>
        <span className="lp-fila-producto">{dato.producto}</span>
        <span className="lp-barra" aria-hidden="true">
          {/* La prop style de React se aplica con el CSSOM, que la CSP permite */}
          <span style={{ '--lp-proporcion': `${proporcionPrecio(dato.precio, mayor)}%` }} />
        </span>
      </div>
      <div className="lp-fila-precio">
        {barato && <span className="lp-chip-barato">Más barato</span>}
        <strong>{formatoSoles(dato.precio)}</strong>
      </div>
    </li>
  )
}

export default function ComparadorHero() {
  const menosMovimiento = useMenosMovimiento()
  const [indice, setIndice] = useState(0)
  const [pausado, setPausado] = useState(false)
  const [encima, setEncima] = useState(false)
  const ejemplo = EJEMPLOS_DEMO[indice]
  const { ahorro, porcentaje } = ahorroDeEjemplo(ejemplo)
  const rota = !menosMovimiento && !pausado && !encima

  useEffect(() => {
    if (!rota) return undefined
    const temporizador = setInterval(() => setIndice((i) => siguienteIndice(i, EJEMPLOS_DEMO.length)), INTERVALO_MS)
    return () => clearInterval(temporizador)
  }, [rota])

  const elegir = (i) => {
    setIndice(i)
    setPausado(true)
  }

  return (
    <div
      className="lp-comparador"
      onMouseEnter={() => setEncima(true)}
      onMouseLeave={() => setEncima(false)}
      onFocus={() => setEncima(true)}
      onBlur={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget)) setEncima(false)
      }}
    >
      <div className="lp-comparador-barra">
        <div className="lp-busqueda" aria-hidden="true">
          <Icono nombre="buscar" />
          <span key={ejemplo.id} className="lp-busqueda-texto">
            {ejemplo.nombre.toLowerCase()}
          </span>
        </div>
        {!menosMovimiento && (
          <button
            type="button"
            className="lp-boton-pausa"
            onClick={() => setPausado((p) => !p)}
            aria-label={pausado ? 'Reanudar los ejemplos' : 'Pausar los ejemplos'}
            title={pausado ? 'Reanudar' : 'Pausar'}
          >
            <Icono nombre={pausado ? 'play' : 'pausa'} />
          </button>
        )}
      </div>

      <div className="lp-ejemplos" role="group" aria-label="Elige un ejemplo">
        {EJEMPLOS_DEMO.map((e, i) => (
          <button key={e.id} type="button" aria-pressed={i === indice} onClick={() => elegir(i)}>
            {e.nombre}
          </button>
        ))}
      </div>

      <MapaDemo ejemplo={ejemplo} />

      <ul key={ejemplo.id} className="lp-filas" aria-label={`${ejemplo.nombre}, ${ejemplo.presentacion}`}>
        <FilaPrecio dato={ejemplo.caro} mayor={ejemplo.caro.precio} />
        <FilaPrecio dato={ejemplo.barato} mayor={ejemplo.caro.precio} barato />
      </ul>

      <div className="lp-ahorro">
        <span className="lp-ahorro-etiqueta">Ahorras</span>
        <span className="lp-ahorro-monto">
          <Counter value={ahorro} decimals={2} prefix="S/ " locale="es-PE" fontSize="inherit" duration={0.9} stagger={0.05} />
        </span>
        <span className="lp-ahorro-porcentaje">{porcentaje} % menos</span>
      </div>

      <p className="lp-comparador-nota">
        {ejemplo.nota} · {ejemplo.presentacion}. <strong>Precios de ejemplo de la demo.</strong>
      </p>
    </div>
  )
}
