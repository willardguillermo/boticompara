// H69 - La conversación del asistente se conserva un rato en sessionStorage.
// - Se guarda junto con el usuario y la hora en que se cerró el panel.
// - Si al abrir pasaron más de 10 minutos desde el cierre, empieza vacía y se borra.
//   Mientras el panel está abierto (cerradaEn = null) no vence.
// - Se borra al cerrar sesión y no se muestra a otro usuario.
// - Solo se guardan mensajes que salieron bien: nada "enviando", nada fallido
//   (los errores 429/503/504 traen los números de ayuda) ni las respuestas de crisis.
// Lógica pura: el almacenamiento y la hora se reciben como parámetros para poder probarla.
// Si sessionStorage falla (modo privado estricto), el chat funciona solo en memoria.

export const CLAVE_CHAT = 'boticompara.chat'
export const VENCE_CHAT_MS = 10 * 60_000

function almacenamientoPorDefecto() {
  try {
    return globalThis.sessionStorage ?? null
  } catch {
    return null
  }
}

/** true si el panel se cerró hace más de 10 minutos. Abierto (null) nunca vence. */
export function conversacionVencida(cerradaEn, ahora) {
  return typeof cerradaEn === 'number' && ahora - cerradaEn > VENCE_CHAT_MS
}

/**
 * Mensajes que se pueden guardar: los que ya se mostraron y salieron bien.
 * La respuesta de crisis (ayuda) y la pregunta que la provocó no se guardan.
 */
export function mensajesParaGuardar(mensajes) {
  const guardables = []
  for (const m of mensajes) {
    if (m.estado !== 'ok') continue
    if (m.rol === 'asistente' && m.ayuda) {
      if (guardables.at(-1)?.rol === 'usuario') guardables.pop()
      continue
    }
    guardables.push(m)
  }
  return guardables.map(({ rol, texto, productos, respaldo }) =>
    rol === 'usuario'
      ? { rol, texto, estado: 'ok' }
      : { rol, texto, estado: 'ok', productos: productos ?? [], respaldo: respaldo === true, ayuda: false },
  )
}

export function borrarConversacion(almacenamiento = almacenamientoPorDefecto()) {
  try {
    almacenamiento?.removeItem(CLAVE_CHAT)
  } catch {
    // sin almacenamiento: no hay nada que borrar
  }
}

/** Guarda la conversación; sin mensajes que guardar, borra lo guardado. */
export function guardarConversacion(
  { usuarioId, mensajes, cerradaEn },
  almacenamiento = almacenamientoPorDefecto(),
) {
  const guardables = mensajesParaGuardar(mensajes)
  if (usuarioId == null || guardables.length === 0) {
    borrarConversacion(almacenamiento)
    return
  }
  try {
    almacenamiento?.setItem(CLAVE_CHAT, JSON.stringify({ usuarioId, cerradaEn: cerradaEn ?? null, mensajes: guardables }))
  } catch {
    // lleno o no disponible: la conversación sigue solo en memoria
  }
}

/**
 * Lee la conversación guardada de este usuario. Devuelve { mensajes, cerradaEn }
 * o null (y borra lo guardado) si no hay, está dañada, es de otro usuario o venció.
 */
export function leerConversacion(usuarioId, ahora, almacenamiento = almacenamientoPorDefecto()) {
  let datos
  try {
    datos = JSON.parse(almacenamiento?.getItem(CLAVE_CHAT) ?? 'null')
  } catch {
    datos = null
  }
  const valida =
    datos != null &&
    usuarioId != null &&
    datos.usuarioId === usuarioId &&
    Array.isArray(datos.mensajes) &&
    !conversacionVencida(datos.cerradaEn, ahora)
  if (!valida) {
    borrarConversacion(almacenamiento)
    return null
  }
  return {
    mensajes: mensajesParaGuardar(datos.mensajes.filter((m) => m && typeof m.texto === 'string')),
    cerradaEn: typeof datos.cerradaEn === 'number' ? datos.cerradaEn : null,
  }
}
