// H17, H20 - Tarjeta de vidrio para escribir el código que llegó al correo. La usan el registro (paso 2), el login
// (403 CORREO_NO_VERIFICADO) y la recuperación de contraseña (con los campos de la contraseña nueva como children).
// Al completar el código: las casillas se funden en la cruz LED de botica, que se enciende mientras se espera la
// respuesta (mínimo 900 ms); si es correcto se vuelve check y la tarjeta se tiñe de verde; si no, la cruz falla en
// rojo y se rompe en las 6 casillas. "¿No te llegó el código? Reenviar en 00:38" usa el Retry-After real.
// El código solo vive en el estado de este componente.
import { useEffect, useRef, useState } from 'react'
import useMenosMovimiento from '../hooks/useMenosMovimiento.js'
import { codigoCompleto } from '../utils/codigoOtp.js'
import { ESPERA_POR_DEFECTO, formatoReloj, segundosDeEspera } from '../utils/cuentaRegresiva.js'
import Alerta from './Alerta.jsx'
import CodigoOtp from './CodigoOtp.jsx'
import Icono from './Icono.jsx'
import './VerificacionCodigo.css'

const pausa = (ms) => new Promise((r) => setTimeout(r, ms))

// Tiempos de la secuencia (con movimiento reducido son más cortos: solo hay fundidos)
const TIEMPOS = {
  normal: { minimo: 900, fija: 300, exito: 1100, falla: 650, volver: 600 },
  reducido: { minimo: 400, fija: 0, exito: 700, falla: 400, volver: 250 },
}

/**
 * onVerificar(codigo): promesa que resuelve con lo que haya que pasar a onVerificado (por ejemplo el token)
 * o falla con el ApiError. esErrorDeCodigo(err): si el error es del código (la cruz falla y las casillas quedan en
 * rojo); los demás se muestran arriba, sin borrar el código. onReenviar(): pide otro código. espera:
 * useCuentaRegresiva() de quien lo usa, para que la cuenta siga aunque la tarjeta se desmonte.
 * validarAntes(): opcional, para los campos de children (devuelve false si hay errores).
 * subtituloExito: lo que dice debajo del título al verificar (si no se pasa, queda el subtítulo).
 * Titulo: 'h1' o 'h2'; tituloRef recibe el foco al cambiar de paso.
 */
export default function VerificacionCodigo({
  titulo = 'Verifica tu correo',
  tituloExito = 'Correo verificado',
  subtitulo,
  subtituloExito,
  Titulo = 'h2',
  tituloRef,
  onVerificar,
  onVerificado,
  esErrorDeCodigo = (err) => err?.status === 400,
  onReenviar,
  onCambiarCorreo,
  espera,
  textoCambiar = 'Cambiar correo',
  textoBoton = 'Verificar',
  textoExito = 'Verificado',
  anuncioExito = 'Correo verificado',
  autoEnviar = true,
  validarAntes,
  children,
}) {
  const menosMovimiento = useMenosMovimiento()
  const [valor, setValor] = useState('')
  const [fase, setFase] = useState('escribiendo')
  const [errorCodigo, setErrorCodigo] = useState('')
  const [errorGeneral, setErrorGeneral] = useState('')
  const [sacudir, setSacudir] = useState(0)
  const [reenviando, setReenviando] = useState(false)
  const [mensaje, setMensaje] = useState('')
  // Lo que oye el lector de pantalla: "Verificando código" y "Correo verificado" (el error lo anuncia CodigoOtp)
  const [anuncio, setAnuncio] = useState('')
  const vivo = useRef(true)

  useEffect(() => {
    vivo.current = true
    return () => {
      vivo.current = false
    }
  }, [])

  const ocupado = fase !== 'escribiendo'
  const exito = fase === 'exito'

  async function verificar(codigo) {
    if (ocupado) return
    if (!codigo) {
      setErrorCodigo('Escribe los 6 dígitos del código.')
      setSacudir((n) => n + 1)
      return
    }
    if (validarAntes && !validarAntes()) return
    const t = menosMovimiento ? TIEMPOS.reducido : TIEMPOS.normal
    setErrorCodigo('')
    setErrorGeneral('')
    setMensaje('')
    setFase('reunir')
    setAnuncio('Verificando código')

    // La cruz se ve encendida al menos 900 ms aunque la API responda antes
    const [resultado] = await Promise.allSettled([onVerificar(codigo), pausa(t.minimo)])
    if (!vivo.current) return

    if (resultado.status === 'fulfilled') {
      setFase('fija')
      await pausa(t.fija)
      setFase('exito')
      setAnuncio(anuncioExito)
      await pausa(t.exito)
      if (vivo.current) await onVerificado(resultado.value)
      return
    }

    const err = resultado.reason
    const delCodigo = esErrorDeCodigo(err)
    if (delCodigo) {
      setFase('falla')
      await pausa(t.falla)
    }
    setFase('volver')
    await pausa(t.volver)
    if (!vivo.current) return
    setFase('escribiendo')
    setAnuncio('')
    if (delCodigo) {
      setErrorCodigo(err.mensaje ?? 'El código no es válido.')
      setValor('')
      setSacudir((n) => n + 1)
    } else {
      setErrorGeneral(err?.mensaje ?? 'No se pudo verificar el código.')
    }
  }

  async function reenviar() {
    setErrorCodigo('')
    setErrorGeneral('')
    setMensaje('')
    setReenviando(true)
    try {
      await onReenviar()
      setValor('')
      setMensaje('Te enviamos un código nuevo. El anterior ya no sirve.')
      espera.iniciar(ESPERA_POR_DEFECTO)
    } catch (err) {
      setErrorGeneral(err.mensaje ?? 'No se pudo reenviar el código.')
      if (err.status === 429) espera.iniciar(segundosDeEspera(err.retryAfter))
    } finally {
      setReenviando(false)
    }
  }

  function enviar(e) {
    e.preventDefault()
    verificar(codigoCompleto(valor))
  }

  return (
    <div className="vidrio-escena">
      <span className="vidrio-luz vidrio-luz-1" aria-hidden="true" />
      <span className="vidrio-luz vidrio-luz-2" aria-hidden="true" />
      <form className="vidrio" data-estado={exito ? 'exito' : 'normal'} onSubmit={enviar} noValidate>
        <span className="vidrio-tinte" aria-hidden="true" />
        <span className="vidrio-brillo" aria-hidden="true" />

        <span className="vidrio-icono" aria-hidden="true">
          <Icono nombre="llave" className="vidrio-icono-llave" />
          <Icono nombre="escudo" className="vidrio-icono-escudo" />
        </span>
        <Titulo ref={tituloRef} tabIndex={tituloRef ? -1 : undefined} className="vidrio-titulo">
          {exito ? tituloExito : titulo}
        </Titulo>
        {(exito ? subtituloExito ?? subtitulo : subtitulo) && (
          <p className="vidrio-subtitulo">{exito ? subtituloExito ?? subtitulo : subtitulo}</p>
        )}

        <Alerta tipo="info">{mensaje && <p>{mensaje}</p>}</Alerta>
        <Alerta tipo="error">{errorGeneral && <p>{errorGeneral}</p>}</Alerta>

        <CodigoOtp
          valor={valor}
          onCambio={(nuevo) => {
            setValor(nuevo)
            if (errorCodigo) setErrorCodigo('')
          }}
          onCompleto={autoEnviar ? verificar : undefined}
          error={errorCodigo}
          sacudir={sacudir}
          fase={fase}
          autoFocus
        />

        {children && <div className="vidrio-campos">{children}</div>}

        <button
          type="submit"
          className={`btn btn-primario btn-bloque vidrio-boton${exito ? ' vidrio-boton-exito' : ''}`}
          disabled={ocupado}
        >
          {exito ? (
            <>
              <Icono nombre="escudo" /> {textoExito}
            </>
          ) : ocupado ? (
            <>
              <span className="vidrio-spinner" aria-hidden="true" /> Verificando código…
            </>
          ) : (
            textoBoton
          )}
        </button>

        <div className="vidrio-pie">
          <p>
            ¿No te llegó el código?{' '}
            <button
              type="button"
              className="btn-enlace"
              onClick={reenviar}
              disabled={reenviando || ocupado || espera.segundos > 0}
            >
              {reenviando ? (
                'Reenviando…'
              ) : espera.segundos > 0 ? (
                <>
                  Reenviar en <span className="espera">{formatoReloj(espera.segundos)}</span>
                </>
              ) : (
                'Reenviar'
              )}
            </button>
          </p>
          {onCambiarCorreo && (
            <button type="button" className="btn-enlace" onClick={onCambiarCorreo} disabled={ocupado}>
              {textoCambiar}
            </button>
          )}
        </div>

        <span className="sr-only" role="status">
          {anuncio}
        </span>
      </form>
    </div>
  )
}
