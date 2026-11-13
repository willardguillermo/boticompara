"""
H89: moderación de reseñas por el administrador.

Las reseñas las escribe el comprador desde la app (Spring Boot). Aquí nadie edita el texto ni las
estrellas ni borra reseñas.
"""
import re
import unicodedata

from django.conf import settings

VISIBLE = 'VISIBLE'
OCULTA = 'OCULTA'
ESTADOS = ((VISIBLE, 'Visible'), (OCULTA, 'Oculta'))


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
