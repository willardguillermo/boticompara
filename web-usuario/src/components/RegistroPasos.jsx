// H1 - Armazón del registro por pasos: lista de pasos a la izquierda y tarjeta del paso actual
import { useEffect, useRef } from 'react'
import Icono from './Icono.jsx'

const PASOS = [
  { titulo: 'Tu cuenta', descripcion: 'Datos de acceso', hecho: 'Cuenta creada' },
  { titulo: 'Tu botica', descripcion: 'Nombre comercial y RUC' },
  { titulo: 'Ubicación', descripcion: 'Dirección y mapa' },
  { titulo: 'Licencia', descripcion: 'Documento de funcionamiento' },
  { titulo: 'Revisión', descripcion: 'Confirma y envía' },
]

/**
 * paso: el paso actual (1 a 5), o 6 para la pantalla final, con los 5 pasos hechos.
 * children: el contenido del paso. pie: los botones del pie (opcional).
 * onIrAPaso(numero): si se pasa, los pasos hechos se pueden clicar para volver a ellos,
 * salvo el paso 1, porque la cuenta ya existe.
 * descripciones: textos que reemplazan la descripción de un paso, por número ({ 4: '...' }).
 */
export default function RegistroPasos({ paso, pie, onIrAPaso, descripciones, children }) {
  const final = paso > PASOS.length
  const lista = useRef(null)

  // En celular la lista es una fila con scroll: se desplaza (solo en horizontal) hasta el paso actual
  useEffect(() => {
    const ol = lista.current
    const actual = ol?.querySelector('[aria-current="step"]') ?? ol?.lastElementChild
    if (!ol || !actual || ol.scrollWidth <= ol.clientWidth) return
    ol.scrollLeft = actual.offsetLeft - ol.offsetLeft - 16
  }, [paso])

  return (
    <div className="registro-pasos">
      <nav className="pasos-lateral" aria-label="Pasos del registro">
        <p className="pasos-titulo">Registro de botica</p>
        <p className="pasos-duracion">Toma unos 5 minutos</p>
        <ol ref={lista} className="pasos-lista">
          {PASOS.map(({ titulo, descripcion, hecho }, i) => {
            const numero = i + 1
            const estado = numero < paso ? 'hecho' : numero === paso ? 'actual' : 'pendiente'
            const visitable = Boolean(onIrAPaso) && estado === 'hecho' && numero > 1
            return (
              <li key={titulo}>
                <button
                  type="button"
                  className={`paso paso-${estado}`}
                  aria-current={estado === 'actual' ? 'step' : undefined}
                  disabled={!visitable}
                  onClick={visitable ? () => onIrAPaso(numero) : undefined}
                >
                  <span className="paso-circulo">
                    {estado === 'hecho' ? <Icono nombre="check" /> : numero}
                  </span>
                  <span className="paso-texto">
                    <span className="paso-nombre">{titulo}</span>
                    <span className="paso-descripcion">
                      {descripciones?.[numero] ?? ((estado === 'hecho' && hecho) || descripcion)}
                    </span>
                  </span>
                </button>
              </li>
            )
          })}
        </ol>
      </nav>

      <section className="pasos-tarjeta">
        <div className="pasos-barra" aria-hidden="true">
          <div style={{ width: `${Math.min(paso, PASOS.length) * 20}%` }} />
        </div>
        <div className="pasos-cuerpo">
          {!final && <p className="pasos-contador">Paso {paso} de 5</p>}
          {/* La key hace que el contenido entre con un fundido al cambiar de paso */}
          <div key={paso} className="pasos-contenido">
            {children}
          </div>
        </div>
        {pie && <div className="pasos-pie">{pie}</div>}
      </section>
    </div>
  )
}
