import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { seccionActiva } from '../../utils/landing.js'
import BotonTema from '../BotonTema.jsx'
import Icono from '../Icono.jsx'
import Marca from '../Marca.jsx'

// Barra flotante de vidrio de la landing. En escritorio es una sola píldora arriba; en celular la
// lista de secciones baja a una segunda píldora fija abajo (como la barra de una app).
// El realce del ítem activo es un ::before de la lista que se desliza: su posición y ancho van en
// variables CSS que se escriben con el CSSOM (la CSP no permite atributos style en el HTML).

/** Línea (desde arriba de la ventana) que una sección debe cruzar para quedar activa. */
const LINEA = 0.38

export default function NavLanding({ secciones, accion }) {
  const [activa, setActiva] = useState(null)
  const listaRef = useRef(null)

  useEffect(() => {
    let cuadro = 0
    const medir = () => {
      cuadro = 0
      const medidas = secciones.map(({ id }) => ({
        id,
        top: document.getElementById(id)?.getBoundingClientRect().top ?? Infinity,
      }))
      const raiz = document.documentElement
      const alFinal = window.scrollY > 0 && window.innerHeight + window.scrollY >= raiz.scrollHeight - 4
      setActiva(seccionActiva(medidas, window.innerHeight * LINEA, alFinal))
    }
    const pedir = () => {
      if (!cuadro) cuadro = requestAnimationFrame(medir)
    }
    medir()
    window.addEventListener('scroll', pedir, { passive: true })
    window.addEventListener('resize', pedir)
    return () => {
      cancelAnimationFrame(cuadro)
      window.removeEventListener('scroll', pedir)
      window.removeEventListener('resize', pedir)
    }
  }, [secciones])

  useLayoutEffect(() => {
    const lista = listaRef.current
    if (!lista) return undefined
    const colocar = () => {
      // Sin sección activa (en el hero) el realce se esconde sobre el primer ítem: así aparece
      // en su lugar en vez de deslizarse desde el borde.
      const enlace = lista.querySelector(`[data-seccion="${activa ?? secciones[0].id}"]`)
      if (!enlace) return
      lista.style.setProperty('--lp-ind-x', `${enlace.offsetLeft}px`)
      lista.style.setProperty('--lp-ind-w', `${enlace.offsetWidth}px`)
      lista.dataset.indicador = activa ? 'visible' : 'oculto'
    }
    colocar()
    const observador = new ResizeObserver(colocar)
    observador.observe(lista)
    return () => observador.disconnect()
  }, [activa, secciones])

  return (
    <header className="lp-nav">
      <div className="lp-nav-pildora lp-vidrio">
        <Marca a="/" />
        <nav aria-label="Secciones de la página" className="lp-nav-secciones">
          <ul ref={listaRef} className="lp-nav-lista lp-vidrio-movil" data-indicador="oculto">
            {secciones.map(({ id, texto, corto, icono }) => (
              <li key={id}>
                <a href={`#${id}`} data-seccion={id} aria-current={activa === id ? 'true' : undefined}>
                  <Icono nombre={icono} />
                  <span className="lp-nav-texto">{texto}</span>
                  <span className="lp-nav-corto" aria-hidden="true">{corto}</span>
                </a>
              </li>
            ))}
          </ul>
        </nav>
        <div className="lp-nav-acciones">
          <BotonTema />
          <Link to={accion.a} className="btn btn-primario lp-nav-cta">
            {accion.texto}
          </Link>
        </div>
      </div>
    </header>
  )
}
