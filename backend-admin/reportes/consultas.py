"""
H62: consulta agregada del reporte "Medicamentos más buscados".

Está separada del admin para poder probarla sola y para que H63 (filtro de fechas) solo tenga
que pasar otro rango.
"""
from datetime import timedelta

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


def mas_buscados(desde, hasta, limite=MAXIMO_FILAS):
    """
    Agrupa las búsquedas con fecha en [desde, hasta] por texto (sin distinguir mayúsculas) y tipo.

    Cada fila trae: texto, tipo, busquedas, promedio_resultados y sin_resultados. Orden: más
    búsquedas primero; si empatan, más búsquedas sin resultados primero y luego por texto.
    """
    return list(
        Busqueda.objects
        .filter(fecha__gte=desde, fecha__lte=hasta)
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
