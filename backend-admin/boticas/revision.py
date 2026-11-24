"""
H5/H6/H47: única forma de aprobar o rechazar boticas en el admin.

La usan la acción masiva "Aprobar boticas seleccionadas", el guardado del formulario de la ficha
(select "estado") y los botones Aprobar / Rechazar de la ficha. Así las tres comparten las reglas:

- Solo cambian las boticas que de verdad cambian de estado (y, si se indica, solo desde ciertos estados).
- El motivo solo se guarda al rechazar; al aprobar se borra.
- El cambio de estado y su fila en botica_revision se guardan juntos o no se guarda nada.
- El correo al dueño sale después del commit y, si falla, la revisión igual queda guardada.
- A5: el RUC solo se reserva al aprobar. No se aprueba una botica si otra APROBADO ya tiene su RUC, y al
  aprobarla, las demás PENDIENTE con el mismo RUC pasan a RECHAZADO (con historial y correo, como un rechazo).
"""
from django.db import transaction

from .models import Botica, BoticaRevision
from .notificaciones import notificar_revision

ESTADOS_REVISADOS = (Botica.Estado.APROBADO, Botica.Estado.RECHAZADO)

# Botones de la ficha: desde qué estados se puede aprobar o rechazar
PUEDE_APROBAR = (Botica.Estado.PENDIENTE,)
PUEDE_RECHAZAR = (Botica.Estado.PENDIENTE, Botica.Estado.APROBADO)

MENSAJE_SIN_MOTIVO = 'Indica el motivo del rechazo.'
MOTIVO_RUC_ACREDITADO = 'Otra cuenta acreditó la licencia de este RUC'


class RucYaAprobado(ValueError):
    """A5: no se puede aprobar: otra botica APROBADO ya tiene ese RUC (o se eligieron dos con el mismo)."""


class Revisadas(list):
    """Las boticas que pasaron al estado pedido. `rechazadas_por_ruc`: las PENDIENTE que se rechazaron solas (A5)."""

    def __init__(self, boticas=(), rechazadas_por_ruc=()):
        super().__init__(boticas)
        self.rechazadas_por_ruc = list(rechazadas_por_ruc)


def mensaje_ruc_aprobado(ruc, aprobada):
    return (f'No se puede aprobar: la botica {aprobada.nombre_comercial} ya está aprobada con el RUC {ruc}. '
            'Si la licencia es de esta cuenta, primero rechaza esa.')


def aprobada_con_ruc(botica):
    """A5: la otra botica APROBADO con el mismo RUC, o None."""
    return Botica.objects.filter(ruc=botica.ruc, estado=Botica.Estado.APROBADO).exclude(pk=botica.pk).first()


def revisar(pks, estado, revisado_por, motivo=None, desde=None, si_falla_correo=None):
    """
    Pasa a `estado` (APROBADO o RECHAZADO) las boticas de `pks` que aún no lo tienen y, si se da
    `desde`, solo las que están en uno de esos estados. Devuelve la lista de boticas que cambiaron.

    `pks` puede ser una lista o un queryset de pk (subconsulta). `si_falla_correo(botica)` se llama
    si no se pudo enviar el correo de esa botica.
    """
    if estado not in ESTADOS_REVISADOS:
        raise ValueError(f'Estado de revisión no válido: {estado}')
    if estado == Botica.Estado.RECHAZADO:
        motivo = (motivo or '').strip()
        if not motivo:
            # chk_motivo_rechazo de la BD: una botica rechazada siempre tiene motivo
            raise ValueError(MENSAJE_SIN_MOTIVO)
    else:
        motivo = None

    with transaction.atomic():
        # select_for_update: si otro administrador la revisa a la vez, no se registra dos veces
        # (se vuelve a consultar por pk: el queryset del changelist puede traer DISTINCT)
        candidatas = Botica.objects.filter(pk__in=pks).exclude(estado=estado)
        if desde is not None:
            candidatas = candidatas.filter(estado__in=desde)
        mismo_ruc = []
        if estado == Botica.Estado.APROBADO:
            # A5: se bloquean de una vez, en orden de pk, todas las boticas con esos RUC. Así dos administradores
            # que aprueban a la vez dos solicitudes del mismo RUC no se cruzan ni se bloquean entre sí
            rucs = set(candidatas.values_list('ruc', flat=True))
            mismo_ruc = list(Botica.objects.filter(ruc__in=rucs).select_related('usuario')
                             .order_by('pk').select_for_update(of=('self',)))
        por_revisar = list(candidatas.select_related('usuario').select_for_update(of=('self',)))
        if not por_revisar:
            return Revisadas()
        rechazadas_por_ruc = []
        if estado == Botica.Estado.APROBADO:
            rechazadas_por_ruc = _pendientes_del_mismo_ruc(por_revisar, mismo_ruc)
        _guardar(por_revisar, estado, motivo, revisado_por, si_falla_correo)
        if rechazadas_por_ruc:
            _guardar(rechazadas_por_ruc, Botica.Estado.RECHAZADO, MOTIVO_RUC_ACREDITADO, revisado_por,
                     si_falla_correo)
    return Revisadas(por_revisar, rechazadas_por_ruc)


def _pendientes_del_mismo_ruc(por_aprobar, mismo_ruc):
    """
    A5: lanza RucYaAprobado si otra botica APROBADO ya tiene el RUC de una de `por_aprobar`, o si dos de ellas
    comparten RUC. Si no, devuelve las otras PENDIENTE con esos RUC, que se rechazan al aprobar.
    """
    elegidas = {b.pk for b in por_aprobar}
    por_ruc = {}
    for botica in por_aprobar:
        if botica.ruc in por_ruc:
            raise RucYaAprobado(f'Elegiste más de una botica con el RUC {botica.ruc} '
                                f'({por_ruc[botica.ruc].nombre_comercial} y {botica.nombre_comercial}): '
                                'aprueba solo la que acreditó la licencia.')
        por_ruc[botica.ruc] = botica
    otras = [b for b in mismo_ruc if b.pk not in elegidas]
    for otra in otras:
        if otra.estado == Botica.Estado.APROBADO:
            raise RucYaAprobado(mensaje_ruc_aprobado(otra.ruc, otra))
    return [b for b in otras if b.estado == Botica.Estado.PENDIENTE]


def _guardar(boticas, estado, motivo, revisado_por, si_falla_correo):
    # Solo se escriben estado y motivo, para no pisar datos que el dueño
    # haya cambiado desde la web mientras el admin tenía la ficha abierta
    Botica.objects.filter(pk__in=[b.pk for b in boticas]).update(estado=estado, motivo_rechazo=motivo)
    BoticaRevision.objects.bulk_create([
        BoticaRevision(botica=b, estado=estado, motivo=motivo, revisado_por=revisado_por)
        for b in boticas
    ])
    for botica in boticas:
        botica.estado, botica.motivo_rechazo = estado, motivo
        # Después del commit: si algo falla al guardar, no se avisa de un cambio que no ocurrió
        transaction.on_commit(lambda b=botica: _avisar(b, si_falla_correo))


def _avisar(botica, si_falla_correo):
    if not notificar_revision(botica) and si_falla_correo:
        si_falla_correo(botica)
