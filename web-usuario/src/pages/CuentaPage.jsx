// H16 - Ver y editar mis datos (GET y PUT /usuarios/me). El correo y el rol no se editan.
// H19 - Eliminar mi cuenta (DELETE /usuarios/me), en la zona de peligro.
// A7 - Con un RUC 10 o 15 la cuenta debe estar a nombre del titular: aquí se corrige el nombre.
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import Alerta from '../components/Alerta.jsx'
import Campo from '../components/Campo.jsx'
import Cargando from '../components/Cargando.jsx'
import EliminarCuentaModal from '../components/EliminarCuentaModal.jsx'
import Icono from '../components/Icono.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { useBotica } from '../context/BoticaContext.jsx'
import { api } from '../services/index.js'
import {
  cuerpoPerfil,
  formularioDesdePerfil,
  hayCambios,
  MAX_DIRECCION,
  MAX_NOMBRE,
  MAX_TELEFONO,
  mostrarAyudaTitular,
  validarPerfil,
} from '../utils/cuenta.js'
import { propsError } from '../utils/validacion.js'

const ROLES = { DUENO_BOTICA: 'Dueño de botica', COMPRADOR: 'Comprador' }

export default function CuentaPage() {
  const { actualizarUsuario, salirConAviso } = useAuth()
  const { botica } = useBotica()
  const [perfil, setPerfil] = useState(null)
  const [errorCarga, setErrorCarga] = useState('')
  const [datos, setDatos] = useState(formularioDesdePerfil(null))
  const [errores, setErrores] = useState({})
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [confirmando, setConfirmando] = useState(false)

  // recarga: el botón Reintentar la incrementa y el efecto vuelve a pedir el perfil
  const [recarga, setRecarga] = useState(0)

  useEffect(() => {
    let activo = true
    api
      .obtenerPerfil()
      .then((p) => {
        if (!activo) return
        setPerfil(p)
        setDatos(formularioDesdePerfil(p))
        setErrorCarga('')
      })
      .catch((err) => activo && setErrorCarga(err.mensaje ?? 'No se pudieron cargar tus datos.'))
    return () => {
      activo = false
    }
  }, [recarga])

  const cambiar = (campo) => (e) => {
    setDatos((d) => ({ ...d, [campo]: e.target.value }))
    setErrores((er) => ({ ...er, [campo]: undefined }))
    setExito('')
  }

  async function guardar(e) {
    e.preventDefault()
    const nuevos = validarPerfil(datos)
    setErrores(nuevos)
    setError('')
    setExito('')
    if (Object.keys(nuevos).length) return

    setGuardando(true)
    try {
      const actualizado = await api.actualizarPerfil(cuerpoPerfil(datos))
      setPerfil(actualizado)
      setDatos(formularioDesdePerfil(actualizado))
      actualizarUsuario(actualizado)
      setExito('Tus datos se guardaron.')
    } catch (err) {
      setError(err.mensaje ?? 'No se pudieron guardar tus datos.')
    } finally {
      setGuardando(false)
    }
  }

  if (errorCarga) {
    return (
      <div className="tarjeta formulario">
        <Alerta tipo="error">
          <p>{errorCarga}</p>
        </Alerta>
        <div>
          <button type="button" className="btn btn-secundario" onClick={() => {
            setErrorCarga('')
            setRecarga((n) => n + 1)
          }}>
            <Icono nombre="actualizar" /> Reintentar
          </button>
        </div>
      </div>
    )
  }
  if (!perfil) return <Cargando texto="Cargando tus datos..." />

  const sinCambios = !hayCambios(perfil, datos)

  return (
    <div className="cuenta">
      <div className="encabezado-pagina">
        <div>
          <h1>Mi cuenta</h1>
          <p className="texto-suave">Revisa y actualiza tus datos personales.</p>
        </div>
      </div>

      {mostrarAyudaTitular(botica) && (
        <Alerta tipo="info">
          <p>
            <strong>Tu botica usa un RUC de persona (10 o 15).</strong> SUNAT exige que la cuenta esté a nombre del
            titular. Primero corrige aquí tu nombre tal como figura en SUNAT y guárdalo; luego reenvía la solicitud
            en <Link to="/panel/botica/corregir">Corregir botica</Link>.
          </p>
        </Alerta>
      )}

      <form className="tarjeta formulario" onSubmit={guardar} noValidate aria-labelledby="titulo-datos">
        <h2 id="titulo-datos">Datos personales</h2>

        <Campo
          id="nombre"
          etiqueta="Nombre completo"
          error={errores.nombre}
          ayuda="Si tu botica tiene un RUC 10 o 15, escríbelo como figura en SUNAT."
        >
          <input
            {...propsError('nombre', errores.nombre)}
            value={datos.nombre}
            onChange={cambiar('nombre')}
            autoComplete="name"
            maxLength={MAX_NOMBRE}
            required
          />
        </Campo>

        <div className="fila-campos">
          <Campo id="correo" etiqueta="Correo" ayuda="El correo no se puede cambiar.">
            <input id="correo" aria-describedby="correo-mensaje" value={perfil.correo} readOnly />
          </Campo>
          <Campo id="rol" etiqueta="Tipo de cuenta">
            <input id="rol" value={ROLES[perfil.rol] ?? perfil.rol} readOnly />
          </Campo>
        </div>

        <div className="fila-campos">
          <Campo id="telefono" etiqueta="Teléfono" opcional error={errores.telefono}>
            <input
              {...propsError('telefono', errores.telefono)}
              type="tel"
              inputMode="tel"
              autoComplete="tel"
              value={datos.telefono}
              onChange={cambiar('telefono')}
              maxLength={MAX_TELEFONO}
            />
          </Campo>
          <Campo id="direccion" etiqueta="Dirección" opcional error={errores.direccion}>
            <input
              {...propsError('direccion', errores.direccion)}
              autoComplete="street-address"
              value={datos.direccion}
              onChange={cambiar('direccion')}
              maxLength={MAX_DIRECCION}
            />
          </Campo>
        </div>

        <Alerta tipo="error">{error && <p>{error}</p>}</Alerta>
        <Alerta tipo="exito">{exito && <p>{exito}</p>}</Alerta>

        <div className="acciones-form">
          <button type="submit" className="btn btn-primario" disabled={guardando || sinCambios}>
            {guardando ? 'Guardando...' : 'Guardar cambios'}
          </button>
        </div>
      </form>

      <section className="tarjeta zona-peligro" aria-labelledby="titulo-peligro">
        <div>
          <h2 id="titulo-peligro">Zona de peligro</h2>
          <p className="texto-suave">
            Eliminar tu cuenta anonimiza tus datos, da de baja todos los productos de tu botica, hace que deje de
            estar aprobada y rechaza sus pedidos activos. No se puede deshacer.
          </p>
        </div>
        <button type="button" className="btn btn-peligro" onClick={() => setConfirmando(true)}>
          <Icono nombre="borrar" /> Eliminar mi cuenta
        </button>
      </section>

      {confirmando && (
        <EliminarCuentaModal
          botica={botica}
          onCerrar={() => setConfirmando(false)}
          onEliminada={() =>
            salirConAviso(
              'Tu cuenta fue eliminada. Tus datos se anonimizaron, tus productos ya no aparecen en las búsquedas y '
                + 'tus pedidos activos se rechazaron.',
            )
          }
        />
      )}
    </div>
  )
}
