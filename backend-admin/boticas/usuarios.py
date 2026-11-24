"""
H90-H94: estado de las cuentas y acciones del administrador (suspender, reactivar, dar de baja).

No hay columna estado; se calcula con activo y suspendido_hasta (database/v5_sprint4.sql):
    ACTIVO     = activo and (suspendido_hasta is null or suspendido_hasta <= now())
    SUSPENDIDO = activo and suspendido_hasta > now()
    BAJA       = not activo

Cada acción es un UPDATE que lleva en su WHERE el estado que exige. Si otro administrador cambió
la cuenta un instante antes, el UPDATE no toca ninguna fila y la acción se rechaza: no hace falta
bloquear la fila ni puede quedar en el historial algo que no ocurrió.

S4-02: al dar de baja a un dueño, su botica pasa a RECHAZADO (libera el RUC, A5) y sus reservas activas se
rechazan, como cuando el dueño elimina su cuenta desde la web (DELETE /usuarios/me).
"""
from datetime import datetime, time, timedelta

from django.db import transaction
from django.db.models import Q
from django.utils import timezone

from . import revision
from .models import Botica, Reserva, Usuario, UsuarioAccion
from .notificaciones import notificar_reserva_rechazada

ACTIVO = 'ACTIVO'
SUSPENDIDO = 'SUSPENDIDO'
BAJA = 'BAJA'
ESTADOS = ((ACTIVO, 'Activo'), (SUSPENDIDO, 'Suspendido'), (BAJA, 'Baja'))

MAX_DIAS_SUSPENSION = 90
MOTIVO_MIN = 10
MOTIVO_MAX = 300


class AccionNoPermitida(Exception):
    """La acción no corresponde al estado actual de la cuenta."""


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


def mensaje_rechazo(usuario, accion, requisito):
    return f'No se puede {accion} la cuenta de {usuario.nombre}: su estado es «{texto_estado(usuario)}». {requisito}'


def _rechazar(usuario_id, accion, requisito):
    usuario = Usuario.objects.filter(pk=usuario_id).first()
    if usuario is None:
        raise AccionNoPermitida('La cuenta no existe.')
    raise AccionNoPermitida(mensaje_rechazo(usuario, accion, requisito))


# H91: suspensión
PUEDE_SUSPENDER = (ACTIVO,)
REQUISITO_SUSPENDER = 'Solo se suspende una cuenta activa.'


def limites_suspension(hoy=None):
    """Primer y último día que se pueden elegir en "hasta": mañana y hoy + 90 días (hora de Lima)."""
    hoy = hoy or timezone.localdate()
    return hoy + timedelta(days=1), hoy + timedelta(days=MAX_DIAS_SUSPENSION)


def fin_del_dia(fecha):
    """La suspensión dura todo el día elegido: termina a las 23:59:59 de ese día en Lima."""
    return timezone.make_aware(datetime.combine(fecha, time(23, 59, 59)))


def suspender(usuario_id, hasta, motivo, realizado_por):
    """H91: solo una cuenta Activa. hasta es la fecha (date) del último día de la suspensión."""
    with transaction.atomic():
        filas = (Usuario.objects.filter(q_estado(ACTIVO), pk=usuario_id)
                 .update(suspendido_hasta=fin_del_dia(hasta), motivo_estado=motivo))
        if not filas:
            _rechazar(usuario_id, 'suspender', REQUISITO_SUSPENDER)
        UsuarioAccion.objects.create(usuario_id=usuario_id, accion=UsuarioAccion.Accion.SUSPENDER,
                                     motivo=motivo, realizado_por=realizado_por)


# H92: reactivación
PUEDE_REACTIVAR = (SUSPENDIDO,)
REQUISITO_REACTIVAR = 'Solo se reactiva una cuenta suspendida.'


def reactivar(usuario_id, motivo, realizado_por):
    """H92: solo una cuenta Suspendida. El motivo es opcional."""
    motivo = motivo or None
    with transaction.atomic():
        filas = (Usuario.objects.filter(q_estado(SUSPENDIDO), pk=usuario_id)
                 .update(suspendido_hasta=None, motivo_estado=None))
        if not filas:
            _rechazar(usuario_id, 'reactivar', REQUISITO_REACTIVAR)
        UsuarioAccion.objects.create(usuario_id=usuario_id, accion=UsuarioAccion.Accion.REACTIVAR,
                                     motivo=motivo, realizado_por=realizado_por)


# H93: baja definitiva
PUEDE_DAR_BAJA = (ACTIVO, SUSPENDIDO)
REQUISITO_DAR_BAJA = 'La cuenta ya fue dada de baja y no se puede revertir.'

# S4-02: la botica y las reservas de un dueño dado de baja. El de las reservas es el mismo de la API
MOTIVO_BOTICA_BAJA = 'Cuenta dada de baja por el administrador'
MOTIVO_RESERVAS_BAJA = 'La botica dejó de atender en BotiCompara'
RESERVAS_ACTIVAS = (Reserva.Estado.PENDIENTE, Reserva.Estado.CONFIRMADA, Reserva.Estado.LISTA)


def dar_baja(usuario_id, motivo, realizado_por):
    """H93: cuenta activa o suspendida. Un solo UPDATE que también quita la suspensión, para cumplir
    el check chk_usuario_baja_sin_suspension (activo or suspendido_hasta is null)."""
    with transaction.atomic():
        filas = (Usuario.objects.filter(pk=usuario_id, activo=True)
                 .update(activo=False, suspendido_hasta=None, motivo_estado=motivo))
        if not filas:
            _rechazar(usuario_id, 'dar de baja', REQUISITO_DAR_BAJA)
        UsuarioAccion.objects.create(usuario_id=usuario_id, accion=UsuarioAccion.Accion.DAR_BAJA,
                                     motivo=motivo, realizado_por=realizado_por)
        _cerrar_botica(usuario_id, realizado_por)


def _cerrar_botica(usuario_id, realizado_por):
    """S4-02, dentro de la transacción de la baja. Los correos a los compradores salen después del commit."""
    botica_id = Botica.objects.filter(usuario_id=usuario_id).values_list('pk', flat=True).first()
    if botica_id is None:
        return
    # La misma función que el botón Rechazar: bloquea la fila y deja la revisión en el historial (H47). Una botica
    # ya RECHAZADO no cambia (su RUC ya está libre). Sin correo al dueño: ya no puede corregir ni reenviar nada
    revision.revisar([botica_id], Botica.Estado.RECHAZADO, realizado_por, motivo=MOTIVO_BOTICA_BAJA,
                     desde=revision.PUEDE_RECHAZAR, avisar_dueno=False)
    _rechazar_reservas_activas(botica_id)


def _rechazar_reservas_activas(botica_id):
    activas = list(Reserva.objects.filter(producto__botica_id=botica_id, estado__in=RESERVAS_ACTIVAS)
                   .select_related('usuario', 'producto__botica').order_by('pk').select_for_update(of=('self',)))
    if not activas:
        return
    Reserva.objects.filter(pk__in=[r.pk for r in activas]).update(estado=Reserva.Estado.RECHAZADA,
                                                                 motivo_rechazo=MOTIVO_RESERVAS_BAJA)
    for reserva in activas:
        reserva.estado, reserva.motivo_rechazo = Reserva.Estado.RECHAZADA, MOTIVO_RESERVAS_BAJA
        transaction.on_commit(lambda r=reserva: notificar_reserva_rechazada(r))
