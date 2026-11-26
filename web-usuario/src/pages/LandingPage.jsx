import { useEffect, useRef } from 'react'
import { Link } from 'react-router'
import ComparadorHero from '../components/landing/ComparadorHero.jsx'
import NavLanding from '../components/landing/NavLanding.jsx'
import Icono from '../components/Icono.jsx'
import Marca from '../components/Marca.jsx'
import GradientText from '../components/reactbits/GradientText.jsx'
import SpotlightCard from '../components/reactbits/SpotlightCard.jsx'
import StarBorder from '../components/reactbits/StarBorder.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import useMenosMovimiento from '../hooks/useMenosMovimiento.js'
import useRevelar from '../hooks/useRevelar.js'
import './LandingPage.css'

// Página de presentación pública ("/"). No llama a la API: todo es contenido fijo.
// Los precios son los de ejemplo de la demo y se dicen así; no hay testimonios ni cifras de uso.

const SECCIONES = [
  { id: 'como-funciona', texto: 'Cómo funciona', corto: 'Cómo', icono: 'buscar' },
  { id: 'compradores', texto: 'Compradores', corto: 'App', icono: 'celular' },
  { id: 'boticas', texto: 'Boticas', corto: 'Boticas', icono: 'tienda' },
  { id: 'confianza', texto: 'Confianza', corto: 'Confianza', icono: 'escudo' },
  { id: 'equipo', texto: 'Equipo', corto: 'Equipo', icono: 'usuario' },
]

const PASOS = [
  {
    icono: 'buscar',
    titulo: 'Busca',
    texto: 'Por nombre comercial o por principio activo. Ves las boticas cercanas con stock y su precio.',
    visual: 'buscar',
  },
  {
    icono: 'ticket',
    titulo: 'Reserva',
    texto: 'Aparta tu medicamento desde la app y recibe un código BC-XXXXXX.',
    visual: 'codigo',
  },
  {
    icono: 'tienda',
    titulo: 'Recoge',
    texto: 'Muestra el código en la botica, paga allí y te lo llevas. Sin vueltas.',
    visual: 'recoger',
  },
]

const COMPRADORES = [
  { icono: 'buscar', titulo: 'Compara precios', texto: 'Y descubre alternativas equivalentes: mismo principio activo, misma presentación.' },
  { icono: 'pin', titulo: 'Mapa de boticas', texto: 'Mira qué tienes cerca y cuánto cuesta en cada una.' },
  { icono: 'corazon', titulo: 'Favoritos y alertas', texto: 'Guarda lo que compras siempre y entérate cuando baja de precio.' },
  { icono: 'microfono', titulo: 'Asistente por texto y voz', texto: 'Pregunta como le hablarías al boticario.' },
  { icono: 'estrella', titulo: 'Reservas y reseñas', texto: 'Reserva en segundos y cuenta cómo te atendieron.' },
]

const BOTICAS = [
  { icono: 'casa', titulo: 'Inicio con tu día', texto: 'El resumen de lo que importa hoy, apenas entras.', grande: true, visual: 'inicio' },
  { icono: 'pedido', titulo: 'Pedidos', texto: 'Confirma, prepara y entrega cada reserva paso a paso.', grande: true, visual: 'pedidos' },
  { icono: 'campana', titulo: 'Notificaciones al instante', texto: 'Cada reserva nueva te llega en el momento.', visual: 'aviso' },
  { icono: 'tabla', titulo: 'Catálogo y carga desde Excel', texto: 'Sube todo tu inventario de una vez, sin teclear producto por producto.' },
  { icono: 'chispa', titulo: 'Asistente IA', texto: 'Pregúntale por tu stock, tus precios y cómo estás frente a otras boticas.' },
  { icono: 'estrella', titulo: 'Reseñas de tus clientes', texto: 'Lo que opinan de tu atención, en un solo lugar.' },
]

const CONFIANZA = [
  { icono: 'receta', titulo: 'Boticas verificadas', texto: 'Cada botica se aprueba después de revisar su RUC y su licencia de funcionamiento. Sin aprobación, no aparece.' },
  { icono: 'alerta', titulo: 'Respeta a DIGEMID', texto: 'Manda la condición de venta: un medicamento con receta retenida no se puede reservar.' },
  { icono: 'candado', titulo: 'Datos protegidos', texto: 'Tratamos tus datos según la Ley 29733. El asistente no recibe tus datos personales.' },
  { icono: 'escudo', titulo: 'Límites contra el abuso', texto: 'Topes de intentos y de uso para frenar bots, accesos forzados y consultas masivas.' },
]

const EQUIPO = [
  { nombre: 'Guillermo Willard', iniciales: 'GW', rol: 'Web del dueño, base de datos, asistente IA, administración, despliegue y seguridad' },
  { nombre: 'Alexander Faustino', iniciales: 'AF', rol: 'Backend: la API que conecta la web y la app' },
  { nombre: 'Mijael Lino', iniciales: 'ML', rol: 'App móvil del comprador' },
]

const STACK = ['Spring Boot', 'React', 'Kotlin + Compose', 'Django', 'PostgreSQL (Supabase)', 'Gemini', 'Docker', 'Render', 'Vercel']

const COLORES_TITULO = ['var(--rb-gradiente-1)', 'var(--rb-gradiente-2)', 'var(--rb-gradiente-3)']

function BotonPrincipal({ a, children }) {
  return (
    <StarBorder as={Link} to={a} className="btn btn-primario lp-btn-grande" radius={14} duration={6} thickness={1.5} trailLength={0.45} glow={0.9} hover="none" clickPulse={false}>
      {children}
      <Icono nombre="flecha" className="lp-flecha" />
    </StarBorder>
  )
}

function Encabezado({ id, etiqueta, titulo, texto }) {
  return (
    <div className="lp-encabezado revelar">
      <span className="lp-etiqueta">{etiqueta}</span>
      <h2 id={id}>{titulo}</h2>
      {texto && <p>{texto}</p>}
    </div>
  )
}

function TarjetaFuncion({ icono, titulo, texto, children, className = '', retraso = 0 }) {
  return (
    <SpotlightCard
      className={`lp-tarjeta revelar ${className}`}
      intensity={0.35}
      spotlightSize={260}
      style={{ '--spotlight-card-border': 'transparent', '--lp-retraso': `${retraso}ms` }}
    >
      <span className="lp-tarjeta-icono">
        <Icono nombre={icono} />
      </span>
      <h3>{titulo}</h3>
      <p>{texto}</p>
      {children}
    </SpotlightCard>
  )
}

function VisualPaso({ tipo }) {
  if (tipo === 'buscar') {
    return (
      <div className="lp-paso-visual" aria-hidden="true">
        <div className="lp-mini-busqueda">
          <Icono nombre="buscar" /> <span>paracetamol</span>
        </div>
        <div className="lp-mini-resultados">
          <span />
          <span />
        </div>
      </div>
    )
  }
  if (tipo === 'codigo') {
    return (
      <div className="lp-paso-visual" aria-hidden="true">
        <div className="lp-ticket">
          <span className="lp-ticket-titulo">Tu código</span>
          <span className="lp-ticket-codigo">BC-7K2QXM</span>
        </div>
      </div>
    )
  }
  return (
    <div className="lp-paso-visual" aria-hidden="true">
      <div className="lp-recoger">
        <Icono nombre="tienda" />
        <span className="lp-recoger-check">
          <Icono nombre="check" />
        </span>
      </div>
    </div>
  )
}

function Celular() {
  return (
    <figure className="lp-celular-figura revelar">
      <div className="lp-celular" aria-hidden="true">
        <div className="lp-celular-pantalla">
          <div className="lp-app-barra">
            <span className="lp-app-logo"><Icono nombre="cruz" /></span>
            <span>BotiCompara</span>
          </div>
          <div className="lp-app-busqueda">
            <Icono nombre="buscar" /> paracetamol
          </div>
          <div className="lp-app-mapa">
            <span className="lp-app-pin lp-app-pin-1"><Icono nombre="pin" /></span>
            <span className="lp-app-pin lp-app-pin-2"><Icono nombre="pin" /></span>
            <span className="lp-app-tu" />
          </div>
          <div className="lp-app-resultado lp-app-resultado-mejor">
            <div>
              <strong>FarmaVida</strong>
              <span>Paracetamol genérico</span>
            </div>
            <b>S/ 2.20</b>
          </div>
          <div className="lp-app-resultado">
            <div>
              <strong>Botica Salud Total</strong>
              <span>Paracetamol genérico</span>
            </div>
            <b>S/ 2.50</b>
          </div>
          <div className="lp-app-boton">Reservar</div>
          <div className="lp-app-alerta">
            <Icono nombre="campana" /> Alerta de precio activada
          </div>
        </div>
      </div>
      <figcaption>Ilustración de la app · precios de ejemplo de la demo</figcaption>
    </figure>
  )
}

function VisualBotica({ tipo }) {
  if (tipo === 'inicio') {
    return (
      <div className="lp-mini-panel" aria-hidden="true">
        <span className="lp-mini-saludo">Buenos días</span>
        <div className="lp-mini-kpis">
          <span><Icono nombre="pedido" /></span>
          <span><Icono nombre="caja" /></span>
          <span><Icono nombre="estrella" /></span>
        </div>
      </div>
    )
  }
  if (tipo === 'pedidos') {
    return (
      <ol className="lp-mini-estados" aria-label="Estados de un pedido">
        <li><Icono nombre="reloj" /> Pendiente</li>
        <li><Icono nombre="check" /> Confirmado</li>
        <li><Icono nombre="caja" /> Listo para recoger</li>
        <li><Icono nombre="check" /> Entregado</li>
      </ol>
    )
  }
  if (tipo === 'aviso') {
    return (
      <div className="lp-mini-aviso" aria-hidden="true">
        <Icono nombre="campana" />
        <span>Nueva reserva · BC-7K2QXM</span>
      </div>
    )
  }
  return null
}

export default function LandingPage() {
  const { usuario } = useAuth()
  const esDueno = usuario?.rol === 'DUENO_BOTICA'
  const raizRef = useRef(null)
  const menosMovimiento = useMenosMovimiento()
  useRevelar(raizRef, !menosMovimiento)

  useEffect(() => {
    const anterior = document.title
    document.title = 'BotiCompara · Compara precios de medicamentos entre boticas'
    return () => {
      document.title = anterior
    }
  }, [])

  const principal = esDueno ? { a: '/panel', texto: 'Ir a mi panel' } : { a: '/registro', texto: 'Soy dueño de botica' }
  const accionNav = esDueno ? { a: '/panel', texto: 'Ir a mi panel' } : { a: '/login', texto: 'Ingresar' }

  return (
    <div className="landing" ref={raizRef}>
      <a href="#contenido" className="lp-saltar">Saltar al contenido</a>
      <NavLanding secciones={SECCIONES} accion={accionNav} />

      <main id="contenido" tabIndex={-1}>
        {/* 1. Hero */}
        <section className="lp-hero" aria-labelledby="lp-titulo">
          <div className="lp-hero-fondo" aria-hidden="true" />
          <div className="lp-contenedor lp-hero-grid">
            <div className="lp-hero-texto">
              <span className="lp-etiqueta lp-entrada">Compara precios de medicamentos</span>
              <h1 id="lp-titulo" className="lp-entrada lp-entrada-2">
                El mismo medicamento,{' '}
                <GradientText className="lp-titulo-degradado" colors={COLORES_TITULO} animationSpeed={12} glow={0.15}>
                  otro precio
                </GradientText>{' '}
                a dos cuadras.
              </h1>
              <p className="lp-hero-bajada lp-entrada lp-entrada-3">
                Busca por nombre o principio activo, mira en qué botica cercana hay stock y a qué precio, y reserva para recoger.
              </p>
              <div className="lp-hero-acciones lp-entrada lp-entrada-4">
                <BotonPrincipal a={principal.a}>{principal.texto}</BotonPrincipal>
                {esDueno ? (
                  <a href="#como-funciona" className="btn btn-secundario lp-btn-grande">Cómo funciona</a>
                ) : (
                  <Link to="/login" className="btn btn-secundario lp-btn-grande">Ingresar</Link>
                )}
              </div>
              <p className="lp-hero-pie lp-entrada lp-entrada-5">
                <Icono nombre="celular" /> App para compradores: <strong>Próximamente en Google Play</strong>
              </p>
            </div>
            <div className="lp-hero-visual lp-entrada lp-entrada-3">
              <ComparadorHero />
            </div>
          </div>
        </section>

        {/* 2. Cómo funciona */}
        <section id="como-funciona" className="lp-seccion" aria-labelledby="t-como">
          <div className="lp-contenedor">
            <Encabezado etiqueta="Cómo funciona" id="t-como" titulo="Tres pasos. Cero vueltas." />
            <ol className="lp-pasos">
              {PASOS.map((paso, i) => (
                <li key={paso.titulo} className="lp-paso revelar" style={{ '--lp-retraso': `${i * 120}ms` }}>
                  <span className="lp-paso-numero" aria-hidden="true">{i + 1}</span>
                  <VisualPaso tipo={paso.visual} />
                  <h3>
                    <span className="sr-only">Paso {i + 1}: </span>
                    {paso.titulo}
                  </h3>
                  <p>{paso.texto}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        {/* 3. Compradores */}
        <section id="compradores" className="lp-seccion lp-seccion-tinte" aria-labelledby="t-compradores">
          <div className="lp-contenedor lp-dos-columnas">
            <Celular />
            <div>
              <Encabezado
                etiqueta="Para compradores · App Android"
                id="t-compradores" titulo="Tu botica más barata, en el bolsillo."
              />
              <ul className="lp-lista-funciones">
                {COMPRADORES.map((f, i) => (
                  <li key={f.titulo} className="revelar" style={{ '--lp-retraso': `${i * 80}ms` }}>
                    <span className="lp-tarjeta-icono">
                      <Icono nombre={f.icono} />
                    </span>
                    <div>
                      <h3>{f.titulo}</h3>
                      <p>{f.texto}</p>
                    </div>
                  </li>
                ))}
              </ul>
              <p className="lp-proximamente revelar">
                <Icono nombre="celular" />
                <span>
                  <strong>Próximamente en Google Play</strong>
                  <span className="lp-proximamente-sub">Aún no está disponible para descargar.</span>
                </span>
              </p>
            </div>
          </div>
        </section>

        {/* 4. Boticas */}
        <section id="boticas" className="lp-seccion" aria-labelledby="t-boticas">
          <div className="lp-contenedor">
            <Encabezado
              etiqueta="Para boticas · Web del dueño"
              id="t-boticas" titulo="Tu botica, en el mapa de quien te busca."
              texto="Todo lo que necesitas para vender más sin cambiar tu forma de trabajar."
            />
            <div className="lp-bento">
              {BOTICAS.map((f, i) => (
                <TarjetaFuncion key={f.titulo} {...f} className={f.grande ? 'lp-tarjeta-grande' : ''} retraso={(i % 3) * 90}>
                  {f.visual && <VisualBotica tipo={f.visual} />}
                </TarjetaFuncion>
              ))}
            </div>
            <div className="lp-centro revelar">
              <BotonPrincipal a={principal.a}>{esDueno ? 'Ir a mi panel' : 'Registra tu botica'}</BotonPrincipal>
            </div>
          </div>
        </section>

        {/* 5. Confianza */}
        <section id="confianza" className="lp-seccion lp-seccion-tinte" aria-labelledby="t-confianza">
          <div className="lp-contenedor">
            <Encabezado etiqueta="Confianza" id="t-confianza" titulo="Barato, sí. A cualquier costo, no." />
            <div className="lp-confianza">
              {CONFIANZA.map((f, i) => (
                <TarjetaFuncion key={f.titulo} {...f} retraso={i * 90} />
              ))}
            </div>
          </div>
        </section>

        {/* 6. Equipo */}
        <section id="equipo" className="lp-seccion" aria-labelledby="t-equipo">
          <div className="lp-contenedor">
            <Encabezado etiqueta="Equipo y tecnología" id="t-equipo" titulo="Hecho por tres, de punta a punta." />
            <ul className="lp-equipo">
              {EQUIPO.map((p, i) => (
                <li key={p.nombre} className="lp-persona revelar" style={{ '--lp-retraso': `${i * 110}ms` }}>
                  <span className="lp-avatar" aria-hidden="true">{p.iniciales}</span>
                  <h3>{p.nombre}</h3>
                  <p>{p.rol}</p>
                </li>
              ))}
            </ul>
            <ul className="lp-stack revelar" aria-label="Tecnologías">
              {STACK.map((t) => (
                <li key={t}>{t}</li>
              ))}
            </ul>
          </div>
        </section>

        {/* 7. Cierre */}
        <section className="lp-seccion lp-cierre-seccion" aria-labelledby="t-cierre">
          <div className="lp-contenedor">
            <div className="lp-cierre revelar">
              <div className="lp-cierre-brillo" aria-hidden="true" />
              <h2 id="t-cierre">Que el precio no decida tu salud.</h2>
              <p>Si tienes una botica, súmala hoy y aparece cuando alguien busque lo que vendes.</p>
              <div className="lp-hero-acciones lp-centro-flex">
                <BotonPrincipal a={principal.a}>{principal.texto}</BotonPrincipal>
                {!esDueno && (
                  <Link to="/login" className="btn btn-secundario lp-btn-grande">Ingresar</Link>
                )}
              </div>
            </div>
          </div>
        </section>
      </main>

      <footer className="lp-pie">
        <div className="lp-contenedor lp-pie-contenido">
          <Marca a="/" />
          <p>Proyecto de Tecsup · 2026</p>
          <nav aria-label="Accesos">
            {esDueno ? <Link to="/panel">Mi panel</Link> : (
              <>
                <Link to="/login">Ingresar</Link>
                <Link to="/registro">Registrar mi botica</Link>
              </>
            )}
          </nav>
        </div>
      </footer>
    </div>
  )
}
