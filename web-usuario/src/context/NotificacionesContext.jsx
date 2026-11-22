// H103 - Notificaciones del dueño (docs/API.md, sección 12)
// El contador se consulta cada 30 s solo con la pestaña visible; la lista se trae al abrir la
// campana, al entrar al Inicio y cuando el contador sube.
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../services/index.js'
import { subioContador, tituloPestana } from '../utils/notificaciones.js'

const INTERVALO_MS = 30 * 1000

const NotificacionesContext = createContext(null)

/**
 * total === null -> todavía no se pudo consultar; lista === null -> todavía no se cargó.
 * Vive dentro del panel: al cerrar sesión (o con un 401, que también la cierra) se desmonta
 * y deja de consultar.
 */
export function NotificacionesProvider({ children }) {
  const [total, setTotal] = useState(null)
  const [lista, setLista] = useState(null)
  const [errorLista, setErrorLista] = useState(false)
  const [aviso, setAviso] = useState(null) // { id, cantidad, notificacion }
  const [conTitulo, setConTitulo] = useState(false)
  // Cuántas veces subió el contador: el Inicio lo usa para saber cuándo volver a pedir los pedidos
  const [subidas, setSubidas] = useState(0)
  const [tituloOriginal] = useState(() => document.title)

  const ultimoTotal = useRef(null)
  // Marcar como leída cambia el contador al instante. Una consulta que salió antes o durante ese
  // cambio trae un número viejo: se descarta, para no avisar de algo que no es nuevo.
  const version = useRef(0)
  const cambiosEnCurso = useRef(0)
  const leidasAqui = useRef(new Set())
  const ultimaLista = useRef(0)

  const fijarTotal = useCallback((n) => {
    ultimoTotal.current = n
    setTotal(n)
    if (!n) setConTitulo(false)
  }, [])

  /** Trae la lista. Devuelve null si falló o si ya hay otra consulta más reciente. */
  const cargarLista = useCallback(async () => {
    const orden = ++ultimaLista.current
    setErrorLista(false)
    try {
      const datos = await api.listarNotificaciones()
      if (orden !== ultimaLista.current) return null
      const actual = datos.map((n) => (leidasAqui.current.has(n.id) ? { ...n, leida: true } : n))
      setLista(actual)
      return actual
    } catch {
      // El mensaje del servidor no se muestra: cada pantalla pone el suyo
      if (orden === ultimaLista.current) setErrorLista(true)
      return null
    }
  }, [])

  useEffect(() => {
    let activo = true
    let intervalo = null
    let ultimaConsulta = 0

    function detener() {
      clearInterval(intervalo)
      intervalo = null
    }

    async function consultar() {
      const orden = ++ultimaConsulta
      const versionInicial = version.current
      try {
        const { total: nuevo } = await api.contarNotificacionesNoLeidas()
        const vieja = orden !== ultimaConsulta || versionInicial !== version.current || cambiosEnCurso.current > 0
        if (!activo || vieja) return
        const anterior = ultimoTotal.current
        fijarTotal(nuevo)
        if (!subioContador(anterior, nuevo)) return
        setConTitulo(true)
        setSubidas((n) => n + 1)
        const cantidad = nuevo - anterior
        const reciente = (await cargarLista())?.find((n) => !n.leida)
        if (activo) setAviso({ id: crypto.randomUUID(), cantidad, notificacion: cantidad === 1 ? reciente : undefined })
      } catch (err) {
        // Sin conexión o con un error del servidor se queda el último número y se reintenta en 30 s
        if (err.status === 401) {
          activo = false
          detener()
        }
      }
    }

    function segunVisibilidad() {
      detener()
      if (!activo || document.visibilityState !== 'visible') return
      consultar()
      intervalo = setInterval(consultar, INTERVALO_MS)
    }

    segunVisibilidad()
    document.addEventListener('visibilitychange', segunVisibilidad)
    return () => {
      activo = false
      detener()
      document.removeEventListener('visibilitychange', segunVisibilidad)
    }
  }, [cargarLista, fijarTotal])

  // El título solo cambia después de un aviso; con todo leído, o al salir del panel, vuelve al de siempre
  useEffect(() => {
    document.title = conTitulo ? tituloPestana(total, tituloOriginal) : tituloOriginal
  }, [conTitulo, total, tituloOriginal])
  useEffect(() => () => void (document.title = tituloOriginal), [tituloOriginal])

  /** Aplica el cambio en pantalla, lo guarda y, si falla, vuelve a lo que diga el servidor. */
  const guardarLeidas = useCallback(
    async (ids, nuevoTotal, guardar) => {
      ids.forEach((id) => leidasAqui.current.add(id))
      setLista((l) => l?.map((n) => (ids.includes(n.id) ? { ...n, leida: true } : n)) ?? l)
      if (ultimoTotal.current !== null) fijarTotal(nuevoTotal)
      version.current++
      cambiosEnCurso.current++
      try {
        await guardar()
        return true
      } catch (err) {
        ids.forEach((id) => leidasAqui.current.delete(id))
        if (err.status !== 401) {
          cargarLista()
          api.contarNotificacionesNoLeidas().then((r) => fijarTotal(r.total)).catch(() => {})
        }
        return false
      } finally {
        cambiosEnCurso.current--
        version.current++
      }
    },
    [cargarLista, fijarTotal],
  )

  const marcarLeida = useCallback(
    async (notificacion) => {
      const { id, leida } = notificacion
      setAviso((a) => (a?.notificacion?.id === id ? null : a))
      if (leida || leidasAqui.current.has(id)) return true
      return guardarLeidas([id], Math.max(0, (ultimoTotal.current ?? 0) - 1), () => api.marcarNotificacionLeida(id))
    },
    [guardarLeidas],
  )

  const marcarTodas = useCallback(() => {
    setAviso(null)
    const ids = (lista ?? []).filter((n) => !n.leida).map((n) => n.id)
    return guardarLeidas(ids, 0, () => api.marcarNotificacionesLeidas())
  }, [guardarLeidas, lista])

  const cerrarAviso = useCallback(() => setAviso(null), [])

  const valor = useMemo(
    () => ({ total, lista, errorLista, aviso, subidas, cargarLista, marcarLeida, marcarTodas, cerrarAviso }),
    [total, lista, errorLista, aviso, subidas, cargarLista, marcarLeida, marcarTodas, cerrarAviso],
  )
  return <NotificacionesContext.Provider value={valor}>{children}</NotificacionesContext.Provider>
}

// eslint-disable-next-line react/only-export-components
export function useNotificaciones() {
  return useContext(NotificacionesContext)
}
