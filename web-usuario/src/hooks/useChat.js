// H69 - Estado del chat con el asistente (POST /chat).
// La conversación vive solo en memoria: se pierde al recargar o cerrar sesión
// (nada en localStorage, para no guardar lo que el usuario escribió).
import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../services/index.js'
import { armarHistorial, errorTiempoAgotado, mensajeDeError, TIEMPO_ESPERA_CHAT_MS } from '../utils/chat.js'

let contador = 0
const nuevoId = () => ++contador

/**
 * mensajes: { id, rol: 'usuario' | 'asistente', texto, estado, ... }
 *   usuario:   estado 'enviando' | 'ok' | 'fallido' (con `error`)
 *   asistente: estado 'ok', con `productos`, `respaldo` y `ayuda`
 *
 * `ubicacion` ({ lat, lng }) es opcional: solo se envía si la web ya la tiene.
 */
export function useChat({ ubicacion = null } = {}) {
  const [mensajes, setMensajes] = useState([])
  const [esperando, setEsperando] = useState(false)
  const [borrador, setBorrador] = useState('')
  const controlador = useRef(null)

  // Si el panel deja de existir (por ejemplo, al cerrar sesión) se cancela el pedido
  useEffect(() => () => controlador.current?.abort(), [])

  const enviar = useCallback(
    async (texto) => {
      const mensaje = texto.trim()
      if (!mensaje || controlador.current) return

      const historial = armarHistorial(mensajes)
      const id = nuevoId()
      setMensajes((previos) => [...previos, { id, rol: 'usuario', texto: mensaje, estado: 'enviando' }])
      setEsperando(true)

      const actual = new AbortController()
      controlador.current = actual
      const temporizador = setTimeout(() => actual.abort(errorTiempoAgotado()), TIEMPO_ESPERA_CHAT_MS)

      try {
        const respuesta = await api.enviarChat(
          {
            mensaje,
            historial,
            ...(ubicacion?.lat != null && ubicacion?.lng != null && { lat: ubicacion.lat, lng: ubicacion.lng }),
          },
          { signal: actual.signal },
        )
        setMensajes((previos) => [
          ...previos.map((m) => (m.id === id ? { ...m, estado: 'ok' } : m)),
          {
            id: nuevoId(),
            rol: 'asistente',
            texto: String(respuesta?.respuesta ?? ''),
            estado: 'ok',
            productos: Array.isArray(respuesta?.productos) ? respuesta.productos : [],
            respaldo: respuesta?.respaldo === true,
            ayuda: respuesta?.ayuda === true,
          },
        ])
      } catch (error) {
        // Cancelado al desmontar: no hay pantalla que actualizar
        if (actual.signal.aborted && actual.signal.reason?.name !== 'TimeoutError') return
        // 401: services/index.js ya cerró la sesión y la web vuelve al login
        if (error?.status === 401) return
        // El mensaje queda en pantalla, marcado como no enviado, para reintentarlo
        setMensajes((previos) =>
          previos.map((m) => (m.id === id ? { ...m, estado: 'fallido', error: mensajeDeError(error) } : m)),
        )
      } finally {
        clearTimeout(temporizador)
        if (controlador.current === actual) {
          controlador.current = null
          setEsperando(false)
        }
      }
    },
    [mensajes, ubicacion],
  )

  /** Vuelve a enviar un mensaje que falló; se mueve al final para mantener el orden. */
  const reintentar = useCallback(
    (id) => {
      if (controlador.current) return
      const fallido = mensajes.find((m) => m.id === id && m.estado === 'fallido')
      if (!fallido) return
      setMensajes((previos) => previos.filter((m) => m.id !== id))
      enviar(fallido.texto)
    },
    [mensajes, enviar],
  )

  return { mensajes, esperando, borrador, setBorrador, enviar, reintentar }
}
