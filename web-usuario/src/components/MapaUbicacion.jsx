// H3 - Selector de la ubicación de la botica en un mapa.
// Leaflet + OpenStreetMap: gratis y sin clave de API.
// Clic en el mapa = marca la ubicación; el marcador se puede arrastrar para ajustarla.
// Debajo se muestra la dirección aproximada del punto, que se puede usar en el formulario.
import { useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { direccionDesdeCoordenadas } from '../utils/geocodificacion.js'

const CENTRO_LIMA = [-12.0464, -77.0428]
// El backend acepta hasta 6 decimales (~10 cm de precisión)
const redondear = (n) => Math.round(n * 1e6) / 1e6
const aUbicacion = ({ lat, lng }) => ({ latitud: redondear(lat), longitud: redondear(lng) })

const iconoBotica = L.divIcon({
  className: 'marcador-botica',
  html: '<span></span>',
  iconSize: [28, 28],
  iconAnchor: [14, 34],
})

export default function MapaUbicacion({ valor, onCambio, onUsarDireccion }) {
  const contenedor = useRef(null)
  const mapa = useRef(null)
  const marcador = useRef(null)
  const onCambioRef = useRef(onCambio)
  const [buscando, setBuscando] = useState(false)
  const [errorGps, setErrorGps] = useState('')
  const [direccion, setDireccion] = useState({ estado: 'vacio' })

  useEffect(() => {
    onCambioRef.current = onCambio
  }, [onCambio])

  // Crea el mapa una sola vez
  useEffect(() => {
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
    if (!valor) {
      setDireccion({ estado: 'vacio' })
      return
    }
    const control = new AbortController()
    setDireccion({ estado: 'cargando' })
    const espera = setTimeout(() => {
      direccionDesdeCoordenadas(valor, control.signal)
        .then((resultado) => setDireccion(resultado ? { estado: 'ok', ...resultado } : { estado: 'sin-datos' }))
        .catch((err) => {
          if (err.name !== 'AbortError') setDireccion({ estado: 'error' })
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

  return (
    <div className="selector-mapa">
      <p className="ayuda">
        Haz clic en el mapa para marcar dónde está tu botica. Puedes arrastrar el marcador para ajustarlo.
      </p>
      <div ref={contenedor} className="mapa-ubicacion" role="application" aria-label="Mapa para marcar la ubicación de la botica" />
      <div className="mapa-pie">
        <span className={valor ? 'ok-campo' : 'ayuda'}>
          {valor
            ? `✓ Ubicación marcada (${valor.latitud.toFixed(6)}, ${valor.longitud.toFixed(6)})`
            : 'Aún no marcaste la ubicación.'}
        </span>
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
      {valor && (
        <div className="mapa-direccion" aria-live="polite">
          {direccion.estado === 'cargando' && <span className="ayuda">Buscando la dirección...</span>}
          {direccion.estado === 'error' && (
            <span className="ayuda">No pudimos obtener la dirección, pero la ubicación quedó marcada.</span>
          )}
          {direccion.estado === 'sin-datos' && <span className="ayuda">No hay una dirección conocida para este punto.</span>}
          {direccion.estado === 'ok' && (
            <>
              <span>
                <strong>Dirección aproximada:</strong> {direccion.completa}
              </span>
              {onUsarDireccion && (direccion.direccion || direccion.distrito) && (
                <button
                  type="button"
                  className="btn btn-secundario btn-icono"
                  onClick={() => onUsarDireccion({ direccion: direccion.direccion, distrito: direccion.distrito })}
                >
                  Usar esta dirección
                </button>
              )}
            </>
          )}
        </div>
      )}
      {errorGps && <span className="error-campo" role="alert">{errorGps}</span>}
    </div>
  )
}
