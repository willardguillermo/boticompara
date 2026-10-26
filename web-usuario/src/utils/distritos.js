// Distritos de Lima que ofrece el formulario de la botica
export const DISTRITOS = [
  'Ate', 'Barranco', 'Breña', 'Carabayllo', 'Chaclacayo', 'Chorrillos', 'Cieneguilla', 'Comas',
  'El Agustino', 'Independencia', 'Jesús María', 'La Molina', 'La Victoria', 'Lima', 'Lince',
  'Los Olivos', 'Lurigancho-Chosica', 'Lurín', 'Magdalena del Mar', 'Miraflores', 'Pueblo Libre',
  'Puente Piedra', 'Rímac', 'San Borja', 'San Isidro', 'San Juan de Lurigancho',
  'San Juan de Miraflores', 'San Luis', 'San Martín de Porres', 'San Miguel', 'Santa Anita',
  'Santiago de Surco', 'Surquillo', 'Villa El Salvador', 'Villa María del Triunfo',
]

const sinTildes = (texto) => texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim()

/**
 * SUNAT manda el distrito en mayúsculas ("SAN ISIDRO", "LURIGANCHO"). Devuelve el nombre de DISTRITOS
 * que coincide sin distinguir mayúsculas ni tildes, o el primero que empiece con ese texto; si no, null.
 */
export function distritoDeLista(distrito) {
  const buscado = distrito ? sinTildes(distrito) : ''
  if (!buscado) return null
  return (
    DISTRITOS.find((d) => sinTildes(d) === buscado) ??
    DISTRITOS.find((d) => sinTildes(d).startsWith(buscado)) ??
    null
  )
}
