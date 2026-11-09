// H8 - Agregar producto (POST /productos)
// H9 - Editar producto (PUT /productos/{id})
// H12 - Stock mínimo para la alerta de stock bajo
// D3 - Enlace con el catálogo maestro (medicamentoId, regla D2 y DELETE /productos/{id}/medicamento)
import { useEffect, useState } from 'react'
import { presentacionMedicamento, useBuscarMedicamentos } from '../hooks/useBuscarMedicamentos.js'
import { api } from '../services/index.js'
import { propsError } from '../utils/validacion.js'
import Alerta from './Alerta.jsx'
import Cargando from './Cargando.jsx'
import Campo from './Campo.jsx'
import Icono from './Icono.jsx'
import Modal from './Modal.jsx'

const PRECIO_VALIDO = /^\d+(\.\d{1,2})?$/
const ENTERO_VALIDO = /^\d+$/

function validar(datos) {
  const errores = {}
  if (!datos.nombreComercial.trim()) errores.nombreComercial = 'El nombre comercial es obligatorio.'
  if (!datos.principioActivo.trim()) errores.principioActivo = 'El principio activo es obligatorio.'
  if (!datos.presentacion.trim()) errores.presentacion = 'La presentación es obligatoria.'

  const precio = datos.precio.trim()
  if (!precio) errores.precio = 'El precio es obligatorio.'
  else if (!PRECIO_VALIDO.test(precio)) errores.precio = 'Usa un número con hasta 2 decimales (ej. 8.50).'
  else if (Number(precio) <= 0) errores.precio = 'El precio debe ser mayor a 0.'

  const stock = datos.stock.trim()
  if (!stock) errores.stock = 'El stock es obligatorio.'
  else if (!ENTERO_VALIDO.test(stock)) errores.stock = 'El stock debe ser un número entero igual o mayor a 0.'

  // Opcional: vacío = el backend usa 5 al crear y conserva el valor actual al editar
  const stockMinimo = datos.stockMinimo.trim()
  if (stockMinimo && !ENTERO_VALIDO.test(stockMinimo)) {
    errores.stockMinimo = 'El stock mínimo debe ser un número entero igual o mayor a 0.'
  }
  return errores
}

const ERROR_MEDICAMENTO =
  'El medicamento elegido ya no está en el catálogo (no existe o el administrador lo ocultó). Quita la selección y elige otro.'

/**
 * La API del producto solo trae medicamentoId: el nombre se busca en el catálogo maestro por el
 * nombre comercial o el principio activo del producto. Un medicamento oculto no aparece: se muestra su ID.
 */
async function buscarMedicamentoEnlazado({ medicamentoId, nombreComercial, principioActivo }) {
  for (const termino of [nombreComercial, principioActivo].map((t) => t?.trim() ?? '')) {
    if (termino.length < 2) continue
    const encontrado = (await api.buscarMedicamentos(termino)).find((m) => m.id === medicamentoId)
    if (encontrado) return encontrado
  }
  return null
}

/** D3: elige un medicamento del catálogo maestro, con el mismo buscador de la carga masiva. */
function SelectorMedicamento({ medicamento, enlazadoEnServidor, quitando, error, onElegir, onQuitar }) {
  const [busqueda, setBusqueda] = useState('')
  const { termino, buscando, medicamentos, error: errorBusqueda } = useBuscarMedicamentos(busqueda)

  if (medicamento) {
    return (
      <div className="campo">
        <span className="etiqueta-campo" id="medicamento-etiqueta">
          Medicamento del catálogo <span className="opcional">(opcional)</span>
        </span>
        <div className="medicamento-elegido" aria-labelledby="medicamento-etiqueta">
          <div>
            <strong>{medicamento.nombreComercial ?? `Medicamento ID ${medicamento.id}`}</strong>
            <span className="texto-suave">
              {medicamento.nombreComercial
                ? `${medicamento.principioActivo} · ${presentacionMedicamento(medicamento)} · ID ${medicamento.id}`
                : 'No aparece en la búsqueda del catálogo: puede estar oculto.'}
            </span>
          </div>
          <button type="button" className="btn btn-secundario btn-icono" onClick={onQuitar} disabled={quitando}>
            {quitando ? 'Quitando...' : enlazadoEnServidor ? 'Quitar enlace' : 'Quitar'}
          </button>
        </div>
        {error && (
          <span className="error-campo" role="alert">
            {error}
          </span>
        )}
      </div>
    )
  }

  return (
    <Campo
      id="buscar-medicamento"
      etiqueta="Medicamento del catálogo"
      opcional
      error={error || errorBusqueda}
      ayuda="Búscalo por nombre comercial o principio activo para enlazar tu producto con el catálogo de medicamentos."
    >
      <div className="buscador">
        <Icono nombre="buscar" />
        <input
          id="buscar-medicamento"
          type="search"
          placeholder="Por ejemplo: paracetamol"
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
          aria-describedby="buscar-medicamento-mensaje"
        />
      </div>
      {buscando && !errorBusqueda && (
        <div className="tabla-medicamentos" aria-live="polite">
          {medicamentos === null ? (
            <Cargando texto="Buscando medicamentos..." />
          ) : medicamentos.length === 0 ? (
            <p className="texto-suave">No hay medicamentos que coincidan con &quot;{termino}&quot;.</p>
          ) : (
            <ul className="lista-medicamentos">
              {medicamentos.map((m) => (
                <li key={m.id}>
                  <button type="button" className="opcion-medicamento" onClick={() => onElegir(m)}>
                    <strong>{m.nombreComercial}</strong>
                    <span className="texto-suave">
                      {m.principioActivo} · {presentacionMedicamento(m)} · ID {m.id}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Campo>
  )
}

export default function ProductoFormModal({ producto, onCerrar, onGuardado, onEnlaceQuitado }) {
  const editando = Boolean(producto?.id)
  const [datos, setDatos] = useState({
    nombreComercial: producto?.nombreComercial ?? '',
    principioActivo: producto?.principioActivo ?? '',
    presentacion: producto?.presentacion ?? '',
    precio: producto?.precio != null ? Number(producto.precio).toFixed(2) : '',
    stock: producto?.stock != null ? String(producto.stock) : '',
    stockMinimo: producto?.stockMinimo != null ? String(producto.stockMinimo) : '',
  })
  const [errores, setErrores] = useState({})
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  // D3: medicamento elegido ({ id } solo, hasta saber su nombre) y enlace que ya está guardado en la API
  const [medicamento, setMedicamento] = useState(producto?.medicamentoId ? { id: producto.medicamentoId } : null)
  const [enlaceGuardado, setEnlaceGuardado] = useState(producto?.medicamentoId ?? null)
  const [errorMedicamento, setErrorMedicamento] = useState('')
  const [quitando, setQuitando] = useState(false)

  // Al editar un producto enlazado, busca el nombre del medicamento para mostrarlo
  useEffect(() => {
    if (!producto?.medicamentoId) return undefined
    let activo = true
    buscarMedicamentoEnlazado(producto)
      .then((encontrado) => {
        if (activo && encontrado) setMedicamento((actual) => (actual?.id === encontrado.id ? encontrado : actual))
      })
      .catch(() => {}) // Sin nombre se muestra el ID
    return () => {
      activo = false
    }
  }, [producto])

  function elegirMedicamento(m) {
    setMedicamento(m)
    setErrorMedicamento('')
    // Regla D2: el principio activo es el del medicamento mientras esté enlazado
    setDatos((d) => ({ ...d, principioActivo: m.principioActivo }))
    setErrores((er) => ({ ...er, principioActivo: undefined }))
  }

  async function quitarMedicamento() {
    setErrorMedicamento('')
    // En un producto sin enlace guardado solo se limpia la selección
    if (enlaceGuardado) {
      setQuitando(true)
      setError('')
      try {
        await api.quitarEnlaceMedicamento(producto.id)
        setEnlaceGuardado(null)
        onEnlaceQuitado?.()
      } catch (err) {
        setError(err.mensaje ?? 'No se pudo quitar el enlace con el catálogo.')
        return
      } finally {
        setQuitando(false)
      }
    }
    // El producto conserva el texto del principio activo y vuelve a ser editable
    setMedicamento(null)
  }

  const cambiar = (campo) => (e) => {
    setDatos((d) => ({ ...d, [campo]: e.target.value }))
    setErrores((er) => ({ ...er, [campo]: undefined }))
  }

  async function enviar(e) {
    e.preventDefault()
    const nuevos = validar(datos)
    setErrores(nuevos)
    setError('')
    if (Object.keys(nuevos).length) return

    const cuerpo = {
      nombreComercial: datos.nombreComercial.trim(),
      principioActivo: datos.principioActivo.trim(),
      presentacion: datos.presentacion.trim(),
      precio: Number(datos.precio),
      stock: Number(datos.stock),
    }
    if (datos.stockMinimo.trim()) cuerpo.stockMinimo = Number(datos.stockMinimo)
    // Sin medicamentoId el PUT conserva el enlace guardado: solo se envía al elegir uno nuevo
    if (medicamento && medicamento.id !== enlaceGuardado) cuerpo.medicamentoId = medicamento.id
    setGuardando(true)
    try {
      const guardado = editando
        ? await api.actualizarProducto(producto.id, cuerpo)
        : await api.crearProducto(cuerpo)
      onGuardado(guardado, editando)
    } catch (err) {
      // 400 del backend si el medicamento ya no existe o está oculto
      if (err.status === 400 && cuerpo.medicamentoId && /medicamento/i.test(err.mensaje ?? '')) {
        setErrorMedicamento(ERROR_MEDICAMENTO)
        setError('')
      } else {
        setError(err.mensaje ?? 'No se pudo guardar el producto.')
      }
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={editando ? 'Editar producto' : 'Agregar producto'} onCerrar={onCerrar}>
      <form className="formulario" onSubmit={enviar} noValidate>
        <Alerta tipo="error">{error && <p>{error}</p>}</Alerta>

        <Campo id="nombreComercial" etiqueta="Nombre comercial" error={errores.nombreComercial}>
          <input
            {...propsError('nombreComercial', errores.nombreComercial)}
            value={datos.nombreComercial}
            onChange={cambiar('nombreComercial')}
            placeholder="Panadol"
            autoFocus
          />
        </Campo>
        <SelectorMedicamento
          medicamento={medicamento}
          enlazadoEnServidor={Boolean(enlaceGuardado)}
          quitando={quitando}
          error={errorMedicamento}
          onElegir={elegirMedicamento}
          onQuitar={quitarMedicamento}
        />
        <Campo
          id="principioActivo"
          etiqueta="Principio activo"
          error={errores.principioActivo}
          ayuda={
            medicamento
              ? 'Viene del catálogo de medicamentos mientras el producto esté enlazado. Para escribirlo a mano, quita el enlace.'
              : undefined
          }
        >
          <input
            {...propsError('principioActivo', errores.principioActivo)}
            value={datos.principioActivo}
            onChange={cambiar('principioActivo')}
            readOnly={Boolean(medicamento)}
            placeholder="Paracetamol"
          />
        </Campo>
        <Campo id="presentacion" etiqueta="Presentación" error={errores.presentacion}>
          <input
            {...propsError('presentacion', errores.presentacion)}
            value={datos.presentacion}
            onChange={cambiar('presentacion')}
            placeholder="Tableta 500 mg x 10"
          />
        </Campo>
        <div className="fila-campos">
          <Campo id="precio" etiqueta="Precio (S/)" error={errores.precio}>
            <input
              {...propsError('precio', errores.precio)}
              type="number"
              inputMode="decimal"
              min="0.01"
              step="0.01"
              value={datos.precio}
              onChange={cambiar('precio')}
              placeholder="8.50"
            />
          </Campo>
          <Campo id="stock" etiqueta="Stock (unidades)" error={errores.stock}>
            <input
              {...propsError('stock', errores.stock)}
              type="number"
              inputMode="numeric"
              min="0"
              step="1"
              value={datos.stock}
              onChange={cambiar('stock')}
              placeholder="40"
            />
          </Campo>
        </div>
        <Campo
          id="stockMinimo"
          etiqueta="Stock mínimo"
          opcional
          error={errores.stockMinimo}
          ayuda="Te avisaremos cuando el stock llegue a este número."
        >
          <input
            {...propsError('stockMinimo', errores.stockMinimo)}
            type="number"
            inputMode="numeric"
            min="0"
            step="1"
            value={datos.stockMinimo}
            onChange={cambiar('stockMinimo')}
            placeholder="5"
          />
        </Campo>

        <div className="acciones-form">
          <button type="button" className="btn btn-secundario" onClick={onCerrar} disabled={guardando}>
            Cancelar
          </button>
          <button type="submit" className="btn btn-primario" disabled={guardando || quitando}>
            {guardando ? 'Guardando...' : editando ? 'Guardar cambios' : 'Agregar producto'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
