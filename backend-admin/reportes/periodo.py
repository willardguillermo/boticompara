"""
Periodo elegido con el formulario Desde/Hasta (H63), compartido por el reporte de más buscados
y el panel principal (H64).
"""
from django.contrib import messages
from django.utils.http import urlencode

from boticas.historial import validar_rango

from .consultas import DIAS_POR_DEFECTO, rango_de_fechas


def periodo_de_la_peticion(request, avisar=True):
    """
    Lee desde/hasta de la URL con las reglas del historial de revisiones (H47). Con un error
    lo avisa (si avisar) y no filtra: se usan los últimos 30 días.

    Devuelve un dict listo para el contexto: desde y hasta (datetimes para las consultas),
    rango (título del periodo), rango_desde y rango_hasta (lo escrito, para el formulario), dias
    y sufijo_url ("?desde=...&hasta=..." o "", para enlaces que conservan el periodo, como la
    exportación a Excel de H65).
    """
    texto_desde = request.GET.get('desde', '')
    texto_hasta = request.GET.get('hasta', '')
    fecha_desde, fecha_hasta, error = validar_rango(texto_desde, texto_hasta)
    if error and avisar:
        messages.error(request, error)
    desde, hasta, rango = rango_de_fechas(fecha_desde, fecha_hasta)
    fechas = {'desde': texto_desde, 'hasta': texto_hasta}
    fechas = {campo: texto for campo, texto in fechas.items() if texto}
    return {
        'desde': desde,
        'hasta': hasta,
        'rango': rango,
        'rango_desde': texto_desde,
        'rango_hasta': texto_hasta,
        'dias': DIAS_POR_DEFECTO,
        'sufijo_url': f'?{urlencode(fechas)}' if fechas else '',
    }
