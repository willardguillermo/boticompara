"""
H64: consultas del panel principal del admin (/admin/).

Están separadas del sitio del admin para poder probarlas solas. Solo las búsquedas y las
comparaciones dependen del periodo; el resto de indicadores son el total actual.
"""
from django.db.models import Count, Q

from boticas.models import Botica, Producto, Usuario
from catalogo.models import ConsultaComparacion, Medicamento

from .consultas import en_rango
from .models import Busqueda


def indicadores(desde, hasta):
    """Números de las tarjetas del panel. desde/hasta en None: sin límite por ese lado."""
    usuarios = Usuario.objects.filter(activo=True).aggregate(
        compradores=Count('id', filter=Q(rol=Usuario.Rol.COMPRADOR)),
        duenos=Count('id', filter=Q(rol=Usuario.Rol.DUENO_BOTICA)),
    )
    por_estado = dict(Botica.objects.values_list('estado').annotate(total=Count('id')).order_by())
    catalogo = Medicamento.objects.aggregate(total=Count('id'), ocultos=Count('id', filter=Q(oculto=True)))
    return {
        'compradores_activos': usuarios['compradores'],
        'duenos_activos': usuarios['duenos'],
        # Siempre los tres estados, aunque alguno no tenga boticas
        'boticas_pendientes': por_estado.get(Botica.Estado.PENDIENTE, 0),
        'boticas_aprobadas': por_estado.get(Botica.Estado.APROBADO, 0),
        'boticas_rechazadas': por_estado.get(Botica.Estado.RECHAZADO, 0),
        'productos_activos': Producto.objects.filter(activo=True).count(),
        'medicamentos': catalogo['total'],
        'medicamentos_ocultos': catalogo['ocultos'],
        'busquedas': en_rango(Busqueda.objects.all(), desde, hasta).count(),
        'comparaciones': en_rango(ConsultaComparacion.objects.all(), desde, hasta).count(),
    }
