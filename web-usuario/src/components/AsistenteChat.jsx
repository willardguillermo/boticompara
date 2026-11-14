// H69 - Chat con el asistente (POST /chat, docs/API.md sección 8)
// Botón "Asistente" en la cabecera del panel (solo con sesión iniciada) que abre
// un panel lateral sin salir de la página actual.
// Esta web es solo para dueños de botica (H18): el chat de la web es para el dueño.
// El chat del comprador es H70, en la app móvil.
import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router'
import { useChat } from '../hooks/useChat.js'
import { formatoDistancia, MAX_CARACTERES_CHAT, partirNumerosAyuda } from '../utils/chat.js'
import { formatoSoles } from '../utils/formato.js'
import Icono from './Icono.jsx'

const AVISO = 'No escribas datos personales. El asistente no reemplaza al químico farmacéutico ni al médico.'
const NOTA_RESPALDO = 'Respuesta resumida: el asistente estaba ocupado.'

/**
 * Texto plano en el que el 106 (SAMU) y el 113 son enlaces tel: que se pueden tocar.
 * Se arma con nodos de React, nunca con HTML: el texto puede venir de un modelo de IA.
 */
function TextoConTelefonos({ texto }) {
  return partirNumerosAyuda(texto).map((trozo, i) =>
    typeof trozo === 'string' ? (
      trozo
    ) : (
      <a key={i} href={`tel:${trozo.telefono}`} className="chat-telefono" aria-label={`Llamar al ${trozo.telefono}`}>
        {trozo.telefono}
      </a>
    ),
  )
}

function TarjetaProducto({ producto, onNavegar }) {
  const { nombreComercial, presentacion, precio, stock, stockMinimo, boticaNombre, boticaDistrito, distanciaKm } = producto
  // stockMinimo solo viene en los productos propios del dueño
  const propio = stockMinimo != null
  return (
    <li className="chat-tarjeta">
      <div className="chat-tarjeta-titulo">
        <strong>{nombreComercial}</strong>
        <span className="chat-precio">{formatoSoles(precio)}</span>
      </div>
      <div className="texto-suave">{presentacion}</div>
      <div className="texto-suave">
        {boticaNombre} · {boticaDistrito}
        {distanciaKm != null && ` · a ${formatoDistancia(distanciaKm)}`}
      </div>
      {propio && (
        <div className="chat-tarjeta-pie">
          {stock <= stockMinimo && (
            <span className="etiqueta-aviso">
              {stock === 0 ? 'Agotado' : `Stock bajo: ${stock}`} (mínimo {stockMinimo})
            </span>
          )}
          <Link to="/panel/catalogo" onClick={onNavegar}>
            Ver en mi catálogo
          </Link>
        </div>
      )}
    </li>
  )
}

function Mensaje({ mensaje, onReintentar, puedeReintentar, onNavegar }) {
  if (mensaje.rol === 'usuario') {
    return (
      <li className="chat-fila chat-fila-usuario">
        <div className={`chat-burbuja chat-burbuja-usuario ${mensaje.estado === 'fallido' ? 'chat-burbuja-fallida' : ''}`}>
          <span className="sr-only">Tú: </span>
          {mensaje.texto}
        </div>
        {mensaje.estado === 'fallido' && (
          <div className="chat-error" role="alert">
            <Icono nombre="alerta" />
            <span className="chat-error-texto">
              <TextoConTelefonos texto={mensaje.error} /> <span className="sr-only">Tu mensaje no se envió.</span>
            </span>
            <button
              type="button"
              className="btn btn-secundario btn-icono"
              onClick={() => onReintentar(mensaje.id)}
              disabled={!puedeReintentar}
            >
              <Icono nombre="actualizar" /> Reintentar
            </button>
          </div>
        )}
      </li>
    )
  }

  return (
    <li className="chat-fila">
      <div className={`chat-burbuja chat-burbuja-asistente ${mensaje.ayuda ? 'chat-burbuja-ayuda' : ''}`}>
        <span className="sr-only">Asistente: </span>
        {/* Texto de un modelo de IA: siempre como texto plano, nunca como HTML */}
        {mensaje.ayuda ? (
          <>
            <Icono nombre="telefono" className="chat-icono-ayuda" />
            <TextoConTelefonos texto={mensaje.texto} />
          </>
        ) : (
          mensaje.texto
        )}
        {mensaje.respaldo && <p className="chat-respaldo">{NOTA_RESPALDO}</p>}
      </div>
      {mensaje.productos.length > 0 && (
        <ul className="chat-tarjetas" aria-label="Productos de la respuesta">
          {mensaje.productos.map((p) => (
            <TarjetaProducto key={p.productoId} producto={p} onNavegar={onNavegar} />
          ))}
        </ul>
      )}
    </li>
  )
}

function PanelChat({ chat, onCerrar }) {
  const { mensajes, esperando, borrador, setBorrador, enviar, reintentar } = chat
  const idTitulo = useId()
  const idCaja = useId()
  const idContador = useId()
  const caja = useRef(null)
  const lista = useRef(null)

  // Al abrir, el foco va a la caja de texto
  useEffect(() => {
    caja.current?.focus()
  }, [])

  // Esc cierra el panel, salvo que haya una ventana modal abierta encima
  useEffect(() => {
    const alPresionar = (e) => {
      if (e.key !== 'Escape') return
      if (document.querySelector('[role="dialog"][aria-modal="true"]')) return
      onCerrar()
    }
    document.addEventListener('keydown', alPresionar)
    return () => document.removeEventListener('keydown', alPresionar)
  }, [onCerrar])

  // Siempre a la vista el último mensaje
  useEffect(() => {
    if (lista.current) lista.current.scrollTop = lista.current.scrollHeight
  }, [mensajes, esperando])

  const ultimo = mensajes.at(-1)
  const anuncio = ultimo?.rol === 'asistente'
    ? `El asistente respondió: ${ultimo.texto}${ultimo.respaldo ? ` ${NOTA_RESPALDO}` : ''}`
    : ''

  const mandar = () => {
    if (esperando || !borrador.trim()) return
    enviar(borrador)
    setBorrador('')
  }

  const alPresionarTecla = (e) => {
    // Enter envía; Shift+Enter hace salto de línea
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      mandar()
    }
  }

  // En pantallas chicas el panel tapa la página: se cierra al ir al catálogo
  const alNavegar = () => {
    if (window.matchMedia('(max-width: 600px)').matches) onCerrar()
  }

  return (
    <section className="chat-panel" role="dialog" aria-modal="false" aria-labelledby={idTitulo}>
      <header className="chat-cabecera">
        <h2 id={idTitulo}>
          <Icono nombre="chat" /> Asistente
        </h2>
        <button type="button" className="btn btn-secundario btn-icono" onClick={onCerrar} aria-label="Cerrar el asistente">
          <Icono nombre="equis" />
        </button>
      </header>

      <p className="chat-aviso">
        <Icono nombre="alerta" />
        <span>{AVISO}</span>
      </p>

      <div className="chat-mensajes" ref={lista}>
        {mensajes.length === 0 ? (
          <div className="chat-vacio texto-suave">
            <p>Pregunta sobre tu catálogo o los precios de otras boticas. Por ejemplo:</p>
            <ul>
              <li>¿Qué productos tengo con stock bajo?</li>
              <li>¿A cuánto tengo la amoxicilina?</li>
              <li>¿A cuánto venden el paracetamol otras boticas?</li>
            </ul>
          </div>
        ) : (
          <ul className="chat-lista" aria-label="Conversación con el asistente">
            {mensajes.map((m) => (
              <Mensaje
                key={m.id}
                mensaje={m}
                onReintentar={reintentar}
                puedeReintentar={!esperando}
                onNavegar={alNavegar}
              />
            ))}
          </ul>
        )}
        {esperando && (
          <div className="chat-escribiendo" role="status">
            <span className="spinner" aria-hidden="true" /> El asistente está buscando…
          </div>
        )}
      </div>

      {/* Solo se anuncian las respuestas nuevas del asistente */}
      <div className="sr-only" aria-live="polite" aria-atomic="true">
        {anuncio}
      </div>

      <form
        className="chat-formulario"
        onSubmit={(e) => {
          e.preventDefault()
          mandar()
        }}
      >
        <label htmlFor={idCaja} className="sr-only">
          Escribe tu pregunta al asistente
        </label>
        <textarea
          id={idCaja}
          ref={caja}
          className="chat-caja"
          rows={2}
          maxLength={MAX_CARACTERES_CHAT}
          value={borrador}
          onChange={(e) => setBorrador(e.target.value)}
          onKeyDown={alPresionarTecla}
          placeholder="Escribe tu pregunta…"
          aria-describedby={idContador}
        />
        <div className="chat-formulario-pie">
          <span id={idContador} className="chat-contador texto-suave">
            {borrador.length}/{MAX_CARACTERES_CHAT}
            <span className="sr-only"> caracteres. Enter envía, Shift más Enter hace un salto de línea.</span>
          </span>
          <button type="submit" className="btn btn-primario" disabled={esperando || !borrador.trim()}>
            <Icono nombre="enviar" /> {esperando ? 'Esperando…' : 'Enviar'}
          </button>
        </div>
      </form>
    </section>
  )
}

/** Botón de la cabecera + panel. La conversación se mantiene mientras la página siga abierta. */
export default function AsistenteChat() {
  const [abierto, setAbierto] = useState(false)
  const chat = useChat()
  const boton = useRef(null)

  const cerrar = useCallback(() => {
    setAbierto(false)
    boton.current?.focus()
  }, [])

  return (
    <>
      <button
        ref={boton}
        type="button"
        className={`btn btn-icono ${abierto ? 'btn-primario' : 'btn-secundario'}`}
        aria-haspopup="dialog"
        aria-expanded={abierto}
        onClick={() => (abierto ? cerrar() : setAbierto(true))}
      >
        <Icono nombre="chat" /> Asistente
      </button>
      {abierto && createPortal(<PanelChat chat={chat} onCerrar={cerrar} />, document.body)}
    </>
  )
}
