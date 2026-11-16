package com.boticompara.app.data.remote.dto

import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

data class FavoritoRequest(val productoId: Long)

data class FavoritoResponse(
    val productoId: Long,
    val nombreComercial: String,
    val principioActivo: String,
    val presentacion: String,
    val precio: Double,
    val stock: Int,
    val boticaId: Long,
    val boticaNombre: String,
    val boticaDireccion: String,
    val boticaDistrito: String,
    val fecha: String,                     // fecha en que se guardó (ISO 8601)
    val precioObjetivo: Double? = null     // H60: precio al que quiere el aviso, null si no puso
)

@JsonAdapter(PrecioObjetivoRequestAdapter::class)
data class PrecioObjetivoRequest(val precioObjetivo: Double?)

class PrecioObjetivoRequestAdapter : TypeAdapter<PrecioObjetivoRequest>() {
    override fun write(out: JsonWriter, value: PrecioObjetivoRequest?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.beginObject()
        out.serializeNulls = true
        out.name("precioObjetivo")
        if (value.precioObjetivo == null) out.nullValue() else out.value(value.precioObjetivo)
        out.endObject()
    }

    override fun read(reader: JsonReader): PrecioObjetivoRequest =
        throw UnsupportedOperationException("Solo se usa para enviar")
}
