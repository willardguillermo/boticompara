// H3 - Selector de la ubicación de la botica en un mapa.
// Leaflet + OpenStreetMap: gratis y sin clave de API.
// Clic en el mapa = marca la ubicación; el marcador se puede arrastrar para ajustarla.
// Debajo se muestra la dirección aproximada del punto, que se puede usar en el formulario.
// Con `recuadro` (registro por pasos), las coordenadas y la dirección van juntas en un recuadro
// "Ubicación marcada".
import { useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { direccionDesdeCoordenadas } from '../utils/geocodificacion.js'

const CENTRO_LIMA = [-12.0464, -77.0428]
// El backend acepta hasta 6 decimales (~10 cm de precisión)
const redondear = (n) => Math.round(n * 1e6) / 1e6
const aUbicacion = ({ lat, lng }) => ({ latitud: redondear(lat), longitud: redondear(lng) })
const claveDe = (valor) => (valor ? `${valor.latitud},${valor.longitud}` : null)

const iconoBotica = L.divIcon({
  className: 'marcador-botica',
  html: '<span></span>',
  iconSize: [28, 28],
  iconAnchor: [14, 34],
})

export default function MapaUbicacion({ valor, onCambio, onUsarDireccion, recuadro = false }) {
  const contenedor = useRef(null)
  const mapa = useRef(null)
  const marcador = useRef(null)
  const onCambioRef = useRef(onCambio)
  // Solo se usa al crear el mapa: después el marcador sigue a `valor`
  const valorInicial = useRef(valor)
  const [buscando, setBuscando] = useState(false)
  const [errorGps, setErrorGps] = useState('')
  // Última dirección obtenida, junto con las coordenadas que la originaron
  const [consulta, setConsulta] = useState(null)

  // Si la última respuesta no es del punto actual, todavía se está buscando
  const clave = claveDe(valor)
  const direccion = !valor
    ? { estado: 'vacio' }
    : consulta?.clave === clave
      ? consulta
      : { estado: 'cargando' }

  useEffect(() => {
    onCambioRef.current = onCambio
  }, [onCambio])

  // Crea el mapa una sola vez
  useEffect(() => {
    const valor = valorInicial.current
    const inicial = valor ? [valor.latitud, valor.longitud] : CENTRO_LIMA
    const m = L.map(contenedor.current, { center: inicial, zoom: valor ? 16 : 12 })
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).addTo(m)
    m.on('click', (e) => onCambioRef.current(aUbicacion(e.latlng)))
    mapa.current = m
    return () => {
      m.remove()
      mapa.current = null
      marcador.current = null
    }
  }, [])

  // Mantiene el marcador sincronizado con el valor del formulario
  useEffect(() => {
    const m = mapa.current
    if (!m) return
    if (!valor) {
      marcador.current?.remove()
      marcador.current = null
      return
    }
    const posicion = [valor.latitud, valor.longitud]
    if (marcador.current) {
      marcador.current.setLatLng(posicion)
    } else {
      marcador.current = L.marker(posicion, { icon: iconoBotica, draggable: true, title: 'Ubicación de la botica' })
        .on('dragend', (e) => onCambioRef.current(aUbicacion(e.target.getLatLng())))
        .addTo(m)
    }
  }, [valor])

  // Busca la dirección del punto marcado (espera 600 ms para no consultar en cada arrastre)
  useEffect(() => {
    if (!valor) return undefined
    const claveConsulta = claveDe(valor)
    const control = new AbortController()
    const espera = setTimeout(() => {
      direccionDesdeCoordenadas(valor, control.signal)
        .then((resultado) =>
          setConsulta(
            resultado
              ? { clave: claveConsulta, estado: 'ok', ...resultado }
              : { clave: claveConsulta, estado: 'sin-datos' },
          ),
        )
        .catch((err) => {
          if (err.name !== 'AbortError') setConsulta({ clave: claveConsulta, estado: 'error' })
        })
    }, 600)
    return () => {
      clearTimeout(espera)
      control.abort()
    }
  }, [valor])

  function usarMiUbicacion() {
    if (!navigator.geolocation) {
      setErrorGps('Tu navegador no permite obtener la ubicación.')
      return
    }
    setBuscando(true)
    setErrorGps('')
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        const ubicacion = aUbicacion({ lat: coords.latitude, lng: coords.longitude })
        onCambioRef.current(ubicacion)
        mapa.current?.setView([ubicacion.latitud, ubicacion.longitud], 17)
        setBuscando(false)
      },
      () => {
        setErrorGps('No pudimos obtener tu ubicación. Márcala haciendo clic en el mapa.')
        setBuscando(false)
      },
      { enableHighAccuracy: true, timeout: 10000 },
    )
  }

  const textoDireccion = (
    <>
      {direccion.estado === 'cargando' && <span className="ayuda">Buscando la dirección...</span>}
      {direccion.estado === 'error' && (
        <span className="ayuda">No pudimos obtener la dirección, pero la ubicación quedó marcada.</span>
      )}
      {direccion.estado === 'sin-datos' && <span className="ayuda">No hay una dirección conocida para este punto.</span>}
    </>
  )
  const botonUsarDireccion = direccion.estado === 'ok' && onUsarDireccion && (direccion.direccion || direccion.distrito) && (
    <button
      type="button"
      className="btn btn-secundario btn-icono"
      onClick={() => onUsarDireccion({ direccion: direccion.direccion, distrito: direccion.distrito })}
    >
      Usar esta dirección
    </button>
  )

  return (
    <div className="selector-mapa">
      <p className="ayuda">
        Haz clic en el mapa para marcar dónde está tu botica. Puedes arrastrar el marcador para ajustarlo.
      </p>
      <div ref={contenedor} className="mapa-ubicacion" role="application" aria-label="Mapa para marcar la ubicación de la botica" />
      <div className="mapa-pie">
        {!(recuadro && valor) && (
          <span className={valor ? 'ok-campo' : 'ayuda'}>
            {valor
              ? `✓ Ubicación marcada (${valor.latitud.toFixed(6)}, ${valor.longitud.toFixed(6)})`
              : 'Aún no marcaste la ubicación.'}
          </span>
        )}
        <div className="mapa-acciones">
          <button type="button" className="btn btn-secundario btn-icono" onClick={usarMiUbicacion} disabled={buscando}>
            {buscando ? 'Buscando...' : 'Usar mi ubicación'}
          </button>
          {valor && (
            <button type="button" className="btn btn-secundario btn-icono" onClick={() => onCambioRef.current(null)}>
              Quitar
            </button>
          )}
        </div>
      </div>
      {valor && !recuadro && (
        <div className="mapa-direccion" aria-live="polite">
          {textoDireccion}
          {direccion.estado === 'ok' && (
            <>
              <span>
                <strong>Dirección aproximada:</strong> {direccion.completa}
              </span>
              {botonUsarDireccion}
            </>
          )}
        </div>
      )}
      {valor && recuadro && (
        <div className="mapa-recuadro" aria-live="polite">
          <div className="mapa-recuadro-texto">
            <strong>✓ Ubicación marcada</strong>
            {textoDireccion}
            {direccion.estado === 'ok' && <span>{direccion.completa}</span>}
            <span className="mapa-coordenadas">
              {valor.latitud.toFixed(6)}, {valor.longitud.toFixed(6)}
            </span>
          </div>
          {botonUsarDireccion}
        </div>
      )}
      {errorGps && <span className="error-campo" role="alert">{errorGps}</span>}
    </div>
  )
}
