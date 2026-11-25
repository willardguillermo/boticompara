"""
H6: correo al dueño cuando su botica se aprueba o se rechaza.
S4-02: correo al comprador cuando su reserva se rechaza porque la botica dejó de atender.

El envío nunca debe impedir guardar la revisión: si falla, se registra en el log
y se devuelve False para que el admin muestre un aviso.
"""
import logging
from decimal import ROUND_HALF_UP, Decimal

from django.core.mail import send_mail

from .models import Botica

logger = logging.getLogger(__name__)


def _contenido(botica):
    saludo = f'Hola {botica.usuario.nombre}:'
    if botica.estado == Botica.Estado.APROBADO:
        return (
            'Tu botica fue aprobada en BotiCompara',
            f'{saludo}\n\n'
            f'Tu botica "{botica.nombre_comercial}" (RUC {botica.ruc}) fue aprobada.\n\n'
            'Ya puedes publicar tu catálogo desde la web de BotiCompara: los productos que agregues '
            'aparecerán en las búsquedas de los compradores en la app.\n\n'
            'Equipo de BotiCompara',
        )
    return (
        'Tu solicitud en BotiCompara fue observada',
        f'{saludo}\n\n'
        f'Revisamos la solicitud de tu botica "{botica.nombre_comercial}" (RUC {botica.ruc}) '
        'y encontramos una observación:\n\n'
        f'Motivo: {botica.motivo_rechazo or "No se indicó un motivo."}\n\n'
        'Corrige los datos y reenvía la solicitud desde la web de BotiCompara, en "Mi botica". '
        'Volveremos a revisarla.\n\n'
        'Equipo de BotiCompara',
    )


def notificar_revision(botica):
    """Envía el correo según el estado (APROBADO o RECHAZADO). Devuelve True si se envió."""
    if botica.estado not in (Botica.Estado.APROBADO, Botica.Estado.RECHAZADO):
        return True
    asunto, mensaje = _contenido(botica)
    try:
        send_mail(asunto, mensaje, None, [botica.usuario.correo], fail_silently=False)
    except Exception:  # SMTP, red, credenciales o configuración: la revisión ya está guardada
        logger.exception('No se pudo enviar el correo de revisión de la botica %s', botica.pk)
        return False
    return True


def _contenido_reserva_rechazada(reserva):
    """El mismo texto que el correo de rechazo de la API (PedidoCorreoService.cuerpoCambioEstado, H83)."""
    producto, botica = reserva.producto, reserva.producto.botica
    total = (reserva.precio_unitario * reserva.cantidad).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
    return (
        f'Tu reserva {reserva.codigo} fue rechazada',
        f'Hola {reserva.usuario.nombre},\n\n'
        'Lamentablemente la botica no pudo atender tu reserva.\n\n'
        f'Código: {reserva.codigo}\n'
        f'Producto: {producto.nombre_comercial} ({producto.presentacion})\n'
        f'Cantidad: {reserva.cantidad}\n'
        f'Total: S/ {total}\n'
        f'Botica: {botica.nombre_comercial} — {botica.direccion}, {botica.distrito}\n'
        f'Teléfono: {botica.telefono or "no registrado"}\n'
        f'Motivo: {reserva.motivo_rechazo}\n'
        '\nBotiCompara\n',
    )


def notificar_reserva_rechazada(reserva):
    """Envía el correo al comprador. Devuelve True si se envió; si falla, el rechazo ya está guardado."""
    asunto, mensaje = _contenido_reserva_rechazada(reserva)
    try:
        send_mail(asunto, mensaje, None, [reserva.usuario.correo], fail_silently=False)
    except Exception:  # SMTP, red, credenciales o configuración
        logger.exception('No se pudo enviar el correo de rechazo de la reserva %s', reserva.pk)
        return False
    return True
