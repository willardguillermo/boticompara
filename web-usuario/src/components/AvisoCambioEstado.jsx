// H6 - Aviso destacado, una sola vez por cambio, cuando la botica se aprueba o se rechaza.
// Lo muestran el Inicio (aprobada) y Mi botica.
import { useEffect, useState } from 'react'
import { useBotica } from '../context/BoticaContext.jsx'
import { esCambioSinVer, guardarEstadoVisto, leerEstadoVisto } from '../utils/estadoVisto.js'
import Icono from './Icono.jsx'

const AVISOS_CAMBIO = {
  APROBADO: { tipo: 'exito', icono: 'check', texto: '¡Tu botica fue aprobada! Ya puedes publicar tu catálogo.' },
  RECHAZADO: { tipo: 'aviso', icono: 'alerta', texto: 'Tu solicitud fue observada: revisa el motivo y corrígela.' },
}

export default function AvisoCambioEstado() {
  const { botica } = useBotica()
  // Estado que el dueño ya había visto al entrar y estado cuyo aviso cerró
  const [vistoAlEntrar] = useState(() => leerEstadoVisto(botica.id))
  const [cerrado, setCerrado] = useState(null)

  // Lo que se muestra queda como visto: el aviso no se repite en la próxima visita
  useEffect(() => {
    guardarEstadoVisto(botica.id, botica.estado)
  }, [botica.id, botica.estado])

  if (!esCambioSinVer(botica.estado, vistoAlEntrar) || botica.estado === cerrado) return null
  const aviso = AVISOS_CAMBIO[botica.estado]

  return (
    <div className={`alerta alerta-${aviso.tipo} aviso-cambio`} role="status">
      <Icono nombre={aviso.icono} />
      <p>
        <strong>{aviso.texto}</strong>
      </p>
      <button type="button" className="aviso-cerrar" onClick={() => setCerrado(botica.estado)} aria-label="Cerrar aviso">
        <Icono nombre="equis" />
      </button>
    </div>
  )
}
