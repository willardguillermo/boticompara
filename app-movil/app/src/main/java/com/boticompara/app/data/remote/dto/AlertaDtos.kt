package com.boticompara.app.data.remote.dto

data class AlertaResponse(
    val id: Long,
    val productoId: Long,
    val nombreComercial: String,
    val presentacion: String? = null,
    val boticaNombre: String,
    val precioAnterior: Double,
    val precioNuevo: Double,
    val ahorro: Double,                    // precioAnterior - precioNuevo
    val leida: Boolean,
    val fecha: String                      // ISO 8601
)

data class ResumenAlertasResponse(val noLeidas: Int)
