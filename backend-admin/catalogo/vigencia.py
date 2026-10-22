"""
H34: estado del registro sanitario de un medicamento según fecha_vencimiento_registro.

Igual que registro_vigente de v_producto_busqueda, el registro sigue vigente el mismo
día en que vence (fecha >= hoy). La columna, el filtro del admin y el comando
revisar_registros_sanitarios usan estas mismas reglas.
"""
from datetime import timedelta

from django.db.models import Q
from django.utils import timezone

DIAS_POR_VENCER = 30

VIGENTE = 'vigente'
POR_VENCER = 'por_vencer'
VENCIDO = 'vencido'
SIN_FECHA = 'sin_fecha'

ETIQUETAS = {
    VIGENTE: 'Vigente',
    POR_VENCER: 'Por vencer',
    VENCIDO: 'Vencido',
    SIN_FECHA: 'Sin fecha',
}


def hoy():
    return timezone.localdate()


def limite_por_vencer(dia):
    return dia + timedelta(days=DIAS_POR_VENCER)


def estado(fecha, dia=None):
    if fecha is None:
        return SIN_FECHA
    dia = dia or hoy()
    if fecha < dia:
        return VENCIDO
    if fecha <= limite_por_vencer(dia):
        return POR_VENCER
    return VIGENTE


def condicion(valor, dia=None):
    """Q que filtra los medicamentos con ese estado."""
    dia = dia or hoy()
    campo = 'fecha_vencimiento_registro'
    return {
        SIN_FECHA: Q(**{f'{campo}__isnull': True}),
        VENCIDO: Q(**{f'{campo}__lt': dia}),
        POR_VENCER: Q(**{f'{campo}__gte': dia, f'{campo}__lte': limite_por_vencer(dia)}),
        VIGENTE: Q(**{f'{campo}__gt': limite_por_vencer(dia)}),
    }[valor]
