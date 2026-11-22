// Tema claro u oscuro. El valor vive en <html data-tema>: lo pone public/tema.js antes de pintar y
// aquí se cambia. index.css redefine sus variables con :root[data-tema='oscuro'].
const CLAVE = 'boticompara.tema'
const EVENTO = 'boticompara:tema'

/** Manda lo que el usuario eligió; sin elección (o con un valor raro) sigue al sistema. */
export function resolverTema(guardado, sistemaOscuro) {
  if (guardado === 'oscuro' || guardado === 'claro') return guardado
  return sistemaOscuro ? 'oscuro' : 'claro'
}

function leerGuardado() {
  try {
    return localStorage.getItem(CLAVE)
  } catch {
    return null
  }
}

const mediaOscuro = () => window.matchMedia('(prefers-color-scheme: dark)')

function aplicar(tema) {
  const raiz = document.documentElement
  if (raiz.dataset.tema === tema) return
  raiz.dataset.tema = tema
  raiz.style.colorScheme = tema === 'oscuro' ? 'dark' : 'light'
  window.dispatchEvent(new Event(EVENTO))
}

export const temaActual = () => document.documentElement.dataset.tema ?? 'claro'

/** Cambia al otro tema y recuerda la elección. */
export function alternarTema() {
  const tema = temaActual() === 'oscuro' ? 'claro' : 'oscuro'
  try {
    localStorage.setItem(CLAVE, tema)
  } catch {
    // Sin almacenamiento el cambio dura hasta recargar
  }
  aplicar(tema)
}

/** Para useSyncExternalStore. */
export function suscribirTema(alCambiar) {
  window.addEventListener(EVENTO, alCambiar)
  return () => window.removeEventListener(EVENTO, alCambiar)
}

/** Sigue al sistema mientras no haya una elección, y a las otras pestañas abiertas. */
export function seguirTema() {
  const actualizar = () => aplicar(resolverTema(leerGuardado(), mediaOscuro().matches))
  mediaOscuro().addEventListener('change', actualizar)
  window.addEventListener('storage', (e) => {
    if (e.key === CLAVE || e.key === null) actualizar()
  })
}
