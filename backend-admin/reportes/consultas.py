"""
H62: consulta agregada del reporte "Medicamentos más buscados".
H63: rango de fechas elegido en el formulario Desde/Hasta (días en hora de Lima).

Está separada del admin para poder probarla sola: la vista solo valida el formulario.
"""
from datetime import datetime, time, timedelta

from django.db.models import Avg, Count, Q
from django.db.models.functions import Lower
from django.utils import timezone

from .models import Busqueda

DIAS_POR_DEFECTO = 30
MAXIMO_FILAS = 20


def rango_por_defecto(ahora=None):
    """Los últimos 30 días hasta este momento: (desde, hasta) como datetimes con zona."""
    hasta = ahora or timezone.now()
    return hasta - timedelta(days=DIAS_POR_DEFECTO), hasta


def _inicio_del_dia(fecha):
    """00:00 de ese día en la zona del proyecto (America/Lima)."""
    return timezone.make_aware(datetime.combine(fecha, time.min))


def rango_de_fechas(fecha_desde, fecha_hasta, ahora=None):
    """
    Convierte los días del formulario en (desde, hasta, titulo) para mas_buscados.

    "Desde" empieza a las 00:00 de ese día y "Hasta" incluye todo su día (hasta las 00:00 del
    siguiente, sin incluirlas). Un lado vacío no limita; sin ninguno, los últimos 30 días.
    """
    if not fecha_desde and not fecha_hasta:
        desde, hasta = rango_por_defecto(ahora)
        titulo = (f'Últimos {DIAS_POR_DEFECTO} días: del {timezone.localtime(desde):%d/%m/%Y} '
                  f'al {timezone.localtime(hasta):%d/%m/%Y}')
        return desde, hasta, titulo
    desde = _inicio_del_dia(fecha_desde) if fecha_desde else None
    hasta = _inicio_del_dia(fecha_hasta + timedelta(days=1)) if fecha_hasta else None
    if fecha_desde and fecha_hasta:
        titulo = f'Del {fecha_desde:%d/%m/%Y} al {fecha_hasta:%d/%m/%Y}'
    elif fecha_desde:
        titulo = f'Desde el {fecha_desde:%d/%m/%Y}'
    else:
        titulo = f'Hasta el {fecha_hasta:%d/%m/%Y}'
    return desde, hasta, titulo


def mas_buscados(desde, hasta, limite=MAXIMO_FILAS):
    """
    Agrupa las búsquedas con desde <= fecha < hasta por texto (sin distinguir mayúsculas) y tipo.
    desde o hasta en None: sin límite por ese lado.

    Cada fila trae: texto, tipo, busquedas, promedio_resultados y sin_resultados. Orden: más
    búsquedas primero; si empatan, más búsquedas sin resultados primero y luego por texto.
    """
    busquedas = Busqueda.objects.all()
    if desde:
        busquedas = busquedas.filter(fecha__gte=desde)
    if hasta:
        busquedas = busquedas.filter(fecha__lt=hasta)
    return list(
        busquedas
        .annotate(texto_normalizado=Lower('texto'))
        .values('texto_normalizado', 'tipo')
        .annotate(
            busquedas=Count('id'),
            promedio_resultados=Avg('resultados'),
            sin_resultados=Count('id', filter=Q(resultados=0)),
        )
        .order_by('-busquedas', '-sin_resultados', 'texto_normalizado', 'tipo')
        .values('texto_normalizado', 'tipo', 'busquedas', 'promedio_resultados', 'sin_resultados')
        [:limite]
    )
