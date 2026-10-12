// H3 - Dirección aproximada de un punto del mapa (geocodificación inversa).
// Usa Nominatim de OpenStreetMap: gratis, sin clave, máximo 1 consulta por segundo.
const URL_NOMINATIM = 'https://nominatim.openstreetmap.org/reverse'

/** Devuelve { direccion, distrito, completa } o null si no se encontró nada. */
export async function direccionDesdeCoordenadas({ latitud, longitud }, signal) {
  const params = new URLSearchParams({
    format: 'jsonv2',
    lat: String(latitud),
    lon: String(longitud),
    'accept-language': 'es',
    zoom: '18',
  })
  const respuesta = await fetch(`${URL_NOMINATIM}?${params}`, { signal })
  if (!respuesta.ok) throw new Error('No se pudo obtener la dirección.')
  const datos = await respuesta.json()
  if (!datos?.address) return null

  const a = datos.address
  const calle = [a.road ?? a.pedestrian ?? a.footway, a.house_number].filter(Boolean).join(' ')
  const distrito = a.city_district ?? a.suburb ?? a.town ?? a.city ?? a.county ?? ''
  return {
    direccion: calle,
    distrito,
    completa: datos.display_name ?? [calle, distrito].filter(Boolean).join(', '),
  }
}
