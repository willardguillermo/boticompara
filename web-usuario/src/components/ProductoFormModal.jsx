// H8 - Agregar producto (POST /productos)
// H9 - Editar producto (PUT /productos/{id})
import { useState } from 'react'
import { api } from '../services/index.js'
import { propsError } from '../utils/validacion.js'
import Alerta from './Alerta.jsx'
import Campo from './Campo.jsx'
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
  return errores
}

export default function ProductoFormModal({ producto, onCerrar, onGuardado }) {
  const editando = Boolean(producto?.id)
  const [datos, setDatos] = useState({
    nombreComercial: producto?.nombreComercial ?? '',
    principioActivo: producto?.principioActivo ?? '',
    presentacion: producto?.presentacion ?? '',
    precio: producto?.precio != null ? Number(producto.precio).toFixed(2) : '',
    stock: producto?.stock != null ? String(producto.stock) : '',
  })
  const [errores, setErrores] = useState({})
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)

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
    setGuardando(true)
    try {
      const guardado = editando
        ? await api.actualizarProducto(producto.id, cuerpo)
        : await api.crearProducto(cuerpo)
      onGuardado(guardado, editando)
    } catch (err) {
      setError(err.mensaje ?? 'No se pudo guardar el producto.')
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
        <Campo id="principioActivo" etiqueta="Principio activo" error={errores.principioActivo}>
          <input
            {...propsError('principioActivo', errores.principioActivo)}
            value={datos.principioActivo}
            onChange={cambiar('principioActivo')}
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

        <div className="acciones-form">
          <button type="button" className="btn btn-secundario" onClick={onCerrar} disabled={guardando}>
            Cancelar
          </button>
          <button type="submit" className="btn btn-primario" disabled={guardando}>
            {guardando ? 'Guardando...' : editando ? 'Guardar cambios' : 'Agregar producto'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
