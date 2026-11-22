"""
H5/H6/H47: única forma de aprobar o rechazar boticas en el admin.

La usan la acción masiva "Aprobar boticas seleccionadas", el guardado del formulario de la ficha
(select "estado") y los botones Aprobar / Rechazar de la ficha. Así las tres comparten las reglas:

- Solo cambian las boticas que de verdad cambian de estado (y, si se indica, solo desde ciertos estados).
- El motivo solo se guarda al rechazar; al aprobar se borra.
- El cambio de estado y su fila en botica_revision se guardan juntos o no se guarda nada.
- El correo al dueño sale después del commit y, si falla, la revisión igual queda guardada.
"""
from django.db import transaction

from .models import Botica, BoticaRevision
from .notificaciones import notificar_revision

ESTADOS_REVISADOS = (Botica.Estado.APROBADO, Botica.Estado.RECHAZADO)

# Botones de la ficha: desde qué estados se puede aprobar o rechazar
PUEDE_APROBAR = (Botica.Estado.PENDIENTE,)
PUEDE_RECHAZAR = (Botica.Estado.PENDIENTE, Botica.Estado.APROBADO)

MENSAJE_SIN_MOTIVO = 'Indica el motivo del rechazo.'


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
        por_revisar = Botica.objects.filter(pk__in=pks).exclude(estado=estado)
        if desde is not None:
            por_revisar = por_revisar.filter(estado__in=desde)
        por_revisar = list(por_revisar.select_related('usuario').select_for_update(of=('self',)))
        if not por_revisar:
            return []
        # Solo se escriben estado y motivo, para no pisar datos que el dueño
        # haya cambiado desde la web mientras el admin tenía la ficha abierta
        Botica.objects.filter(pk__in=[b.pk for b in por_revisar]).update(estado=estado, motivo_rechazo=motivo)
        BoticaRevision.objects.bulk_create([
            BoticaRevision(botica=b, estado=estado, motivo=motivo, revisado_por=revisado_por)
            for b in por_revisar
        ])
        for botica in por_revisar:
            botica.estado, botica.motivo_rechazo = estado, motivo
            # Después del commit: si algo falla al guardar, no se avisa de un cambio que no ocurrió
            transaction.on_commit(lambda b=botica: _avisar(b, si_falla_correo))
    return por_revisar


def _avisar(botica, si_falla_correo):
    if not notificar_revision(botica) and si_falla_correo:
        si_falla_correo(botica)
