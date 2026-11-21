"""
Periodo elegido con el formulario Desde/Hasta (H63), compartido por el reporte de más buscados
y el panel principal (H64).
"""
from django.contrib import messages
from django.utils import timezone
from django.utils.http import urlencode

from boticas.historial import validar_rango

from .consultas import DIAS_POR_DEFECTO, rango_de_fechas


def periodo_de_la_peticion(request, avisar=True):
    """
    Lee desde/hasta de la URL con las reglas del historial de revisiones (H47). Con un error
    lo avisa (si avisar) y no filtra: se usan los últimos 30 días.

    Devuelve un dict listo para el contexto: desde y hasta (datetimes para las consultas),
    rango (título del periodo), rango_desde y rango_hasta (lo escrito: si hay algo, se muestra
    «Limpiar»), valor_desde y valor_hasta (lo que muestran los campos: sin fechas válidas, los días
    del rango por defecto, para que se vea qué periodo es), dias y sufijo_url
    ("?desde=...&hasta=..." o "", para enlaces que conservan el periodo, como la exportación a
    Excel de H65).
    """
    texto_desde = request.GET.get('desde', '')
    texto_hasta = request.GET.get('hasta', '')
    fecha_desde, fecha_hasta, error = validar_rango(texto_desde, texto_hasta)
    if error and avisar:
        messages.error(request, error)
    desde, hasta, rango = rango_de_fechas(fecha_desde, fecha_hasta)
    if fecha_desde or fecha_hasta:
        valor_desde = f'{fecha_desde:%Y-%m-%d}' if fecha_desde else ''
        valor_hasta = f'{fecha_hasta:%Y-%m-%d}' if fecha_hasta else ''
    else:
        # Los últimos 30 días: se llenan los campos con sus días (hora de Lima) en vez de dejarlos vacíos
        valor_desde = f'{timezone.localtime(desde):%Y-%m-%d}'
        valor_hasta = f'{timezone.localtime(hasta):%Y-%m-%d}'
    fechas = {'desde': texto_desde, 'hasta': texto_hasta}
    fechas = {campo: texto for campo, texto in fechas.items() if texto}
    return {
        'desde': desde,
        'hasta': hasta,
        'rango': rango,
        'rango_desde': texto_desde,
        'rango_hasta': texto_hasta,
        'valor_desde': valor_desde,
        'valor_hasta': valor_hasta,
        'dias': DIAS_POR_DEFECTO,
        'sufijo_url': f'?{urlencode(fechas)}' if fechas else '',
    }
