/** Etiqueta + control + mensaje de ayuda o de error, con ids enlazados para accesibilidad. */
export default function Campo({ id, etiqueta, opcional = false, error, ok, ayuda, children }) {
  const mensaje = error || ok || ayuda
  const clase = error ? 'error-campo' : ok ? 'ok-campo' : 'ayuda'
  return (
    <div className="campo">
      <label htmlFor={id}>
        {etiqueta} {opcional && <span className="opcional">(opcional)</span>}
      </label>
      {children}
      {mensaje && (
        <span id={`${id}-mensaje`} className={clase} role={error ? 'alert' : undefined}>
          {mensaje}
        </span>
      )}
    </div>
  )
}
