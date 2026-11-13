"""
H89: moderación de reseñas por el administrador.

Las reseñas las escribe el comprador desde la app (Spring Boot). Aquí nadie edita el texto ni las
estrellas ni borra reseñas.
"""

VISIBLE = 'VISIBLE'
OCULTA = 'OCULTA'
ESTADOS = ((VISIBLE, 'Visible'), (OCULTA, 'Oculta'))


def texto_estado(resena):
    return 'Oculta' if resena.oculta else 'Visible'
