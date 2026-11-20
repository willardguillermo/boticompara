"""
H89: moderación de reseñas por el administrador (ocultar y mostrar de nuevo).

Las reseñas las escribe el comprador desde la app (Spring Boot). Aquí nadie edita el texto ni las
estrellas ni borra reseñas: solo se cambian oculta y motivo_ocultamiento, que van juntas por el
check chk_resena_motivo_ocultamiento (not oculta or motivo_ocultamiento is not null).

Igual que en las acciones sobre usuarios (boticas/usuarios.py), cada acción es un UPDATE que lleva
en su WHERE el estado que exige: si otro administrador ya la ocultó o mostró, no toca ninguna fila
y la acción se rechaza.
"""
import re
import unicodedata

from django.conf import settings
from django.contrib.admin.models import CHANGE, LogEntry
from django.db import transaction

from .models import Resena

VISIBLE = 'VISIBLE'
OCULTA = 'OCULTA'
ESTADOS = ((VISIBLE, 'Visible'), (OCULTA, 'Oculta'))


class AccionNoPermitida(Exception):
    """La acción no corresponde al estado actual de la reseña."""


def texto_estado(resena):
    return 'Oculta' if resena.oculta else 'Visible'


# ---- Palabras a revisar ----

def normalizar(texto):
    """Minúsculas y sin tildes ni diéresis ('Huevón' -> 'huevon'). La ñ se queda como n."""
    descompuesto = unicodedata.normalize('NFKD', (texto or '').lower())
    return ''.join(c for c in descompuesto if not unicodedata.combining(c))


def _patron():
    palabras = sorted({normalizar(p).strip() for p in settings.PALABRAS_A_REVISAR} - {''}, key=len, reverse=True)
    if not palabras:
        return None
    # Al inicio de una palabra: "rata" encuentra "ratas" pero no "barata"
    return re.compile(r'\b(?:' + '|'.join(re.escape(p) for p in palabras) + r')\w*')


def palabras_encontradas(comentario):
    """Palabras del comentario que coinciden con la lista, tal como quedan normalizadas, sin repetir."""
    patron = _patron()
    if patron is None or not comentario:
        return []
    return list(dict.fromkeys(patron.findall(normalizar(comentario))))


def ids_con_palabras(queryset):
    """ids de las reseñas del queryset con alguna palabra a revisar.

    Se compara en Python porque la BD no quita tildes sin la extensión unaccent (y las pruebas usan
    SQLite). Solo se leen id y comentario, así que sirve de sobra para el volumen de BotiCompara.
    """
    patron = _patron()
    if patron is None:
        return []
    filas = queryset.exclude(comentario__isnull=True).values_list('id', 'comentario')
    return [pk for pk, comentario in filas if patron.search(normalizar(comentario))]


# ---- Acciones ----

def _registrar(resena_id, usuario_admin, mensaje):
    """Deja la acción en el historial del admin de Django (LogEntry), con quién la hizo."""
    resena = Resena.objects.select_related('botica').get(pk=resena_id)
    LogEntry.objects.log_actions(
        user_id=usuario_admin.pk, queryset=[resena], action_flag=CHANGE, change_message=mensaje,
        single_object=True,
    )


def _rechazar(resena_id, requisito):
    resena = Resena.objects.filter(pk=resena_id).first()
    if resena is None:
        raise AccionNoPermitida('La reseña no existe.')
    raise AccionNoPermitida(f'No se puede hacer esto: la reseña está «{texto_estado(resena)}». {requisito}')


REQUISITO_OCULTAR = 'Solo se oculta una reseña visible.'
REQUISITO_MOSTRAR = 'Solo se vuelve a mostrar una reseña oculta.'


def ocultar(resena_id, motivo, usuario_admin):
    """Un solo UPDATE con oculta = false en el WHERE: oculta y motivo quedan juntos."""
    with transaction.atomic():
        filas = (Resena.objects.filter(pk=resena_id, oculta=False)
                 .update(oculta=True, motivo_ocultamiento=motivo))
        if not filas:
            _rechazar(resena_id, REQUISITO_OCULTAR)
        _registrar(resena_id, usuario_admin, f'Ocultó la reseña. Motivo: {motivo}')


def mostrar(resena_id, usuario_admin):
    """Vuelve a mostrarla y borra el motivo (queda en el historial del admin)."""
    with transaction.atomic():
        filas = (Resena.objects.filter(pk=resena_id, oculta=True)
                 .update(oculta=False, motivo_ocultamiento=None))
        if not filas:
            _rechazar(resena_id, REQUISITO_MOSTRAR)
        _registrar(resena_id, usuario_admin, 'Volvió a mostrar la reseña.')
