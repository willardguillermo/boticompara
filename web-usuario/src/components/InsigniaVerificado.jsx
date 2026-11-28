// H20 - Insignia del paso "Tu cuenta" del registro: el check se dibuja, un brillo pasa una vez y dice
// "Correo verificado". Con prefers-reduced-motion aparece quieta.
import './InsigniaVerificado.css'

export default function InsigniaVerificado({ correo }) {
  return (
    <p className="insignia-verificado">
      <span className="insignia">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M5 12.5l4.5 4.5L19 7.5" pathLength="1" />
        </svg>
        Correo verificado
      </span>
      {correo && <span className="insignia-correo">{correo}</span>}
    </p>
  )
}
