import Icono from './Icono.jsx'

const ICONOS = { error: 'alerta', exito: 'check', aviso: 'reloj', info: 'alerta' }

export default function Alerta({ tipo = 'info', children }) {
  if (!children) return null
  return (
    <div className={`alerta alerta-${tipo}`} role={tipo === 'error' ? 'alert' : 'status'}>
      <Icono nombre={ICONOS[tipo]} />
      <div>{children}</div>
    </div>
  )
}
