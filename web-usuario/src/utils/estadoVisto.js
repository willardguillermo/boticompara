// H6: último estado de la botica que el dueño ya vio, guardado por id de botica.
// Sirve para avisarle una sola vez cuando el administrador la aprueba o la rechaza.
const ESTADOS_CON_AVISO = ['APROBADO', 'RECHAZADO']
const clave = (boticaId) => `boticompara.estadoVisto.${boticaId}`

export function leerEstadoVisto(boticaId) {
  try {
    return localStorage.getItem(clave(boticaId))
  } catch {
    return null
  }
}

export function guardarEstadoVisto(boticaId, estado) {
  try {
    localStorage.setItem(clave(boticaId), estado)
  } catch {
    // Sin localStorage (modo privado estricto) el aviso puede repetirse; no es grave
  }
}

/** true si el estado merece aviso y es distinto del último que el dueño vio. */
export function esCambioSinVer(estado, visto) {
  return ESTADOS_CON_AVISO.includes(estado) && estado !== visto
}

/** true si la botica está APROBADO o RECHAZADO y el dueño aún no vio ese estado. */
export function hayCambioSinVer(botica) {
  return Boolean(botica) && esCambioSinVer(botica.estado, leerEstadoVisto(botica.id))
}
