// H2 - Validación del RUC: en vivo con el dígito verificador y, cuando pasa, una sola consulta
//      a GET /boticas/validar-ruc/{ruc} (SUNAT) que trae la razón social, el estado y la condición;
//      la dirección fiscal y el distrito se sugieren en sus campos, que siguen siendo editables.
// Lo usan el formulario de la botica (BoticaForm) y el registro por pasos.
import { useRef, useState } from 'react'
import { api } from '../services/index.js'
import { distritoDeLista } from '../utils/distritos.js'
import { validarRuc } from '../utils/ruc.js'

const SIN_SUGERENCIAS = { direccion: null, distrito: null }

/** SUNAT manda la dirección con espacios de más: se recortan y se reducen a uno. */
function limpiarDireccion(direccion) {
  return direccion?.trim().replace(/\s+/g, ' ') || null
}

/** Sugerencias de dirección y distrito de una respuesta de validar-ruc (solo SUNAT y válido). */
function sugerenciasDe(resultado) {
  if (!resultado?.valido || resultado.fuente !== 'SUNAT') return SIN_SUGERENCIAS
  return { direccion: limpiarDireccion(resultado.direccion), distrito: distritoDeLista(resultado.distrito) }
}

/** Pone cada sugerencia solo si el campo está vacío o todavía tiene la sugerencia anterior de SUNAT. */
function aplicarSugerencias(d, anteriores, sugerencias) {
  const cambios = {}
  for (const campo of ['direccion', 'distrito']) {
    const esNuestro = !d[campo].trim() || d[campo] === anteriores[campo]
    if (sugerencias[campo] && esNuestro) cambios[campo] = sugerencias[campo]
  }
  return cambios
}

/** Borra los campos que todavía tienen la sugerencia de SUNAT (lo escrito por el usuario se queda). */
function quitarSugerencias(d, sugerencias) {
  const cambios = {}
  for (const campo of ['direccion', 'distrito']) {
    if (sugerencias[campo] && d[campo] === sugerencias[campo]) cambios[campo] = ''
  }
  return cambios
}

/** Mensaje del RUC mientras se escribe: ayuda, error u OK. */
function estadoRucEnVivo(ruc) {
  if (!ruc) return { ayuda: '11 dígitos. Empieza con 10, 15, 17 o 20.' }
  const resultado = validarRuc(ruc)
  if (resultado.valido) return { ok: '✓ RUC válido' }
  // Mientras faltan dígitos no es un error todavía, salvo que el prefijo ya sea incorrecto
  return resultado.completo || resultado.mensaje.startsWith('El RUC debe empezar')
    ? { error: resultado.mensaje }
    : { ayuda: resultado.mensaje }
}

/** Mensaje del RUC según la respuesta de GET /boticas/validar-ruc/{ruc}. */
function estadoRucConsultado(resultado) {
  if (!resultado.valido) return { error: resultado.mensaje || 'El RUC no es válido.' }
  if (resultado.fuente === 'SUNAT') {
    return { ok: `✓ Verificado en SUNAT: ${resultado.razonSocial} · ${resultado.estado} · ${resultado.condicion}` }
  }
  return { ok: 'RUC válido. No se pudo consultar SUNAT ahora; se verificará al enviar.' }
}

/** Cuerpo de POST /boticas y PUT /boticas/mia con el RUC ya verificado. */
export function cuerpoBotica(datos, verificacion, ubicacion) {
  return {
    nombreComercial: datos.nombreComercial.trim(),
    ruc: datos.ruc,
    razonSocial: verificacion.fuente === 'SUNAT' ? verificacion.razonSocial : datos.razonSocial.trim(),
    direccion: datos.direccion.trim(),
    distrito: datos.distrito.trim(),
    telefono: datos.telefono.trim() || null,
    // H3: el backend exige ambas coordenadas o ninguna
    ...(ubicacion && { latitud: ubicacion.latitud, longitud: ubicacion.longitud }),
  }
}

/** Errores de la API que corresponden al campo RUC (inválido o ya registrado). */
export function esErrorDeRuc(err) {
  return (err.status === 400 || err.status === 409) && /ruc/i.test(err.mensaje)
}

/**
 * Estado de la verificación del RUC de un formulario cuyos datos (ruc, razonSocial, direccion, distrito)
 * y errores guarda quien lo usa; el hook los actualiza con setDatos y setErrores.
 */
export function useVerificacionRuc({ rucInicial = '', setDatos, setErrores }) {
  // { ruc, cargando: true } mientras se consulta, luego { ruc, resultado }
  const [consulta, setConsulta] = useState(null)
  const [razonDeSunat, setRazonDeSunat] = useState(false)
  // SUNAT (Decolecta) tiene 100 consultas al mes: se guarda el último RUC consultado y la
  // consulta en curso para no repetir la llamada
  const ultimaConsulta = useRef(null)
  const consultaEnCurso = useRef(null)
  const rucActual = useRef(rucInicial)
  // Última dirección y distrito que pusimos desde SUNAT: un campo con otro valor lo escribió el usuario
  // o venía cargado (H7), y no se toca
  const sugeridos = useRef(SIN_SUGERENCIAS)

  /** Devuelve la respuesta de validar-ruc, reutilizando la última o la que está en curso. */
  function consultarRuc(ruc) {
    if (ultimaConsulta.current?.ruc === ruc) return Promise.resolve(ultimaConsulta.current.resultado)
    if (consultaEnCurso.current?.ruc === ruc) return consultaEnCurso.current.promesa
    const promesa = api
      .validarRuc(ruc)
      .then((resultado) => {
        ultimaConsulta.current = { ruc, resultado }
        return resultado
      })
      .finally(() => {
        if (consultaEnCurso.current?.promesa === promesa) consultaEnCurso.current = null
      })
    consultaEnCurso.current = { ruc, promesa }
    return promesa
  }

  /** Muestra la respuesta solo si el RUC no cambió mientras se consultaba. */
  function aplicarConsulta(ruc, resultado) {
    if (rucActual.current !== ruc) return
    setConsulta({ ruc, resultado })
    if (resultado.valido && resultado.fuente === 'SUNAT') {
      const anteriores = sugeridos.current
      const sugerencias = sugerenciasDe(resultado)
      sugeridos.current = sugerencias
      setDatos((d) => ({
        ...d,
        razonSocial: resultado.razonSocial,
        ...aplicarSugerencias(d, anteriores, sugerencias),
      }))
      setErrores((er) => ({
        ...er,
        razonSocial: undefined,
        ...(sugerencias.direccion && { direccion: undefined }),
        ...(sugerencias.distrito && { distrito: undefined }),
      }))
      setRazonDeSunat(true)
    }
  }

  /** Llamar cada vez que cambia el RUC escrito (ya limpio: solo dígitos, máximo 11). */
  function cambiarRuc(ruc) {
    if (ruc === rucActual.current) return
    rucActual.current = ruc
    setConsulta(null)
    // La razón social vuelve a ser editable; ella, la dirección y el distrito se borran solo si los
    // habíamos puesto desde SUNAT
    const anteriores = sugeridos.current
    sugeridos.current = SIN_SUGERENCIAS
    setDatos((d) => ({
      ...d,
      ...(razonDeSunat && { razonSocial: '' }),
      ...quitarSugerencias(d, anteriores),
    }))
    setRazonDeSunat(false)
    if (!validarRuc(ruc).valido) return

    if (ultimaConsulta.current?.ruc === ruc) {
      aplicarConsulta(ruc, ultimaConsulta.current.resultado)
      return
    }
    setConsulta({ ruc, cargando: true })
    consultarRuc(ruc).then(
      (resultado) => aplicarConsulta(ruc, resultado),
      // Si falla la red se muestra solo la validación local; al enviar se vuelve a intentar
      () => rucActual.current === ruc && setConsulta(null),
    )
  }

  /** Consulta el RUC (o reutiliza la consulta hecha al escribirlo), aplica la respuesta y la devuelve. */
  async function verificar(ruc) {
    const resultado = await consultarRuc(ruc)
    aplicarConsulta(ruc, resultado)
    return resultado
  }

  /** Mensaje para el campo RUC: { error }, { ok } o { ayuda }. */
  function estadoRuc(ruc, error) {
    if (error) return { error }
    if (consulta?.ruc !== ruc) return estadoRucEnVivo(ruc)
    if (consulta.cargando) return { ayuda: 'Consultando SUNAT...' }
    return estadoRucConsultado(consulta.resultado)
  }

  /** Ayuda de la dirección si todavía es la dirección fiscal que sugirió SUNAT para este RUC. */
  function ayudaDireccion(ruc, direccion) {
    const direccionSunat = consulta?.ruc === ruc ? sugerenciasDe(consulta.resultado).direccion : null
    return direccionSunat && direccion === direccionSunat
      ? 'Dirección fiscal según SUNAT. Cámbiala si tu local está en otra dirección.'
      : undefined
  }

  return { razonDeSunat, cambiarRuc, verificar, estadoRuc, ayudaDireccion }
}
