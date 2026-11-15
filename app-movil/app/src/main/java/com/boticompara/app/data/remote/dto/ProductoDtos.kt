package com.boticompara.app.data.remote.dto

data class ProductoResponse(
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
    val boticaLatitud: Double? = null,    // H48: null si la botica no registró ubicación
    val boticaLongitud: Double? = null,
    val distanciaKm: Double? = null   // H27: null si no se envió la ubicación
)

data class ProductoDetalleResponse(
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
    val boticaTelefono: String? = null,   // null si la botica no lo registró
    val fabricante: String? = null,
    val requerimientoReceta: Boolean? = false
)

data class AlternativaResponse(
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
    val tipoCoincidencia: String,          // "EQUIVALENTE" o "PRINCIPIO_ACTIVO"
    val ahorroPorcentaje: Double? = null   // H37: null si no es más barata que el consultado
)

data class AlternativasResponse(
    val productoId: Long,
    val sinEquivalentes: Boolean,          // H41: true cuando no hay alternativas
    val alternativas: List<AlternativaResponse> = emptyList()
)
