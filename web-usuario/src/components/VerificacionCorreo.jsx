// H20 - Bloque para escribir el código que llegó al correo: CodigoOtp, "Reenviar código" con su cuenta
// regresiva (Retry-After o 60 s) y "Cambiar correo". Lo usan el registro (paso 2) y el login (403
// CORREO_NO_VERIFICADO). El código solo vive en el estado de este componente.
import { useState } from 'react'
import useMenosMovimiento from '../hooks/useMenosMovimiento.js'
import { codigoCompleto } from '../utils/codigoOtp.js'
import { ESPERA_POR_DEFECTO, formatoEspera, segundosDeEspera } from '../utils/cuentaRegresiva.js'
import Alerta from './Alerta.jsx'
import CodigoOtp from './CodigoOtp.jsx'

const PAUSA_CHECK_MS = 650

/**
 * onVerificar(codigo): promesa que resuelve con lo que haya que pasar a onVerificado (por ejemplo el token)
 * o falla con el ApiError. onReenviar(): pide otro código. espera: useCuentaRegresiva() de quien lo usa,
 * para que la cuenta siga aunque el bloque se desmonte. textoCambiar: el enlace para volver atrás.
 */
export default function VerificacionCorreo({
  onVerificar,
  onVerificado,
  onReenviar,
  onCambiarCorreo,
  espera,
  textoCambiar = 'Cambiar correo',
  textoBoton = 'Verificar',
}) {
  const menosMovimiento = useMenosMovimiento()
  const [valor, setValor] = useState('')
  const [error, setError] = useState('')
  const [sacudir, setSacudir] = useState(0)
  const [validado, setValidado] = useState(false)
  const [verificando, setVerificando] = useState(false)
  const [reenviando, setReenviando] = useState(false)
  const [mensaje, setMensaje] = useState('')

  async function verificar(codigo) {
    if (verificando || validado) return
    if (!codigo) {
      setError('Escribe los 6 dígitos del código.')
      setSacudir((n) => n + 1)
      return
    }
    setError('')
    setMensaje('')
    setVerificando(true)
    try {
      const resultado = await onVerificar(codigo)
      setValidado(true)
      // Deja ver el check un momento antes de pasar al siguiente paso
      await new Promise((r) => setTimeout(r, menosMovimiento ? 0 : PAUSA_CHECK_MS))
      await onVerificado(resultado)
    } catch (err) {
      setValidado(false)
      setVerificando(false)
      setError(err.mensaje ?? 'No se pudo verificar el código.')
      if (err.status === 400) {
        setValor('')
        setSacudir((n) => n + 1)
      }
    }
  }

  async function reenviar() {
    setError('')
    setMensaje('')
    setReenviando(true)
    try {
      await onReenviar()
      setValor('')
      setMensaje('Te enviamos un código nuevo. El anterior ya no sirve.')
      espera.iniciar(ESPERA_POR_DEFECTO)
    } catch (err) {
      setError(err.mensaje ?? 'No se pudo reenviar el código.')
      if (err.status === 429) espera.iniciar(segundosDeEspera(err.retryAfter))
    } finally {
      setReenviando(false)
    }
  }

  function enviar(e) {
    e.preventDefault()
    verificar(codigoCompleto(valor))
  }

  const ocupado = verificando || validado

  return (
    <form className="formulario" onSubmit={enviar} noValidate>
      <Alerta tipo="info">{mensaje && <p>{mensaje}</p>}</Alerta>
      <CodigoOtp
        valor={valor}
        onCambio={(nuevo) => {
          setValor(nuevo)
          if (error) setError('')
        }}
        onCompleto={verificar}
        error={error}
        sacudir={sacudir}
        validado={validado}
        autoFocus
      />

      <button type="submit" className="btn btn-primario btn-bloque" disabled={ocupado}>
        {validado ? 'Código verificado' : verificando ? 'Verificando...' : textoBoton}
      </button>

      <div className="verificacion-acciones">
        <button type="button" className="btn-enlace" onClick={reenviar} disabled={reenviando || ocupado || espera.segundos > 0}>
          {reenviando ? (
            'Reenviando...'
          ) : espera.segundos > 0 ? (
            <>
              Reenviar código en <span className="espera">{formatoEspera(espera.segundos)}</span>
            </>
          ) : (
            'Reenviar código'
          )}
        </button>
        <button type="button" className="btn-enlace" onClick={onCambiarCorreo} disabled={ocupado}>
          {textoCambiar}
        </button>
      </div>
    </form>
  )
}
