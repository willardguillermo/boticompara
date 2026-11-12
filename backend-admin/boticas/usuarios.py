"""
H90-H94: estado de las cuentas y acciones del administrador (suspender, reactivar, dar de baja).

No hay columna estado; se calcula con activo y suspendido_hasta (database/v5_sprint4.sql):
    ACTIVO     = activo and (suspendido_hasta is null or suspendido_hasta <= now())
    SUSPENDIDO = activo and suspendido_hasta > now()
    BAJA       = not activo
"""
from django.db.models import Q
from django.utils import timezone

ACTIVO = 'ACTIVO'
SUSPENDIDO = 'SUSPENDIDO'
BAJA = 'BAJA'
ESTADOS = ((ACTIVO, 'Activo'), (SUSPENDIDO, 'Suspendido'), (BAJA, 'Baja'))


def q_estado(estado, ahora=None):
    """Filtro de la BD para un estado (lo usan el filtro de la lista y los UPDATE de las acciones)."""
    ahora = ahora or timezone.now()
    if estado == ACTIVO:
        return Q(activo=True) & (Q(suspendido_hasta__isnull=True) | Q(suspendido_hasta__lte=ahora))
    if estado == SUSPENDIDO:
        return Q(activo=True, suspendido_hasta__gt=ahora)
    if estado == BAJA:
        return Q(activo=False)
    raise ValueError(f'Estado desconocido: {estado}')


def estado_de(usuario, ahora=None):
    ahora = ahora or timezone.now()
    if not usuario.activo:
        return BAJA
    if usuario.suspendido_hasta and usuario.suspendido_hasta > ahora:
        return SUSPENDIDO
    return ACTIVO


def texto_estado(usuario, ahora=None):
    """'Activo', 'Suspendido hasta dd/mm/aaaa' o 'Baja' (fecha en hora de Lima)."""
    estado = estado_de(usuario, ahora)
    if estado == SUSPENDIDO:
        return f'Suspendido hasta {timezone.localtime(usuario.suspendido_hasta):%d/%m/%Y}'
    return dict(ESTADOS)[estado]
