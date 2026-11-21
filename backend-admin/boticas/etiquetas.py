"""
Etiquetas de color del admin (estado de usuarios, boticas, reseñas, historiales y vigencia del catálogo).

Un solo juego de colores, todos con contraste AA (≥ 4.5:1) sobre el texto blanco. El naranja es más
oscuro que el de antes (#ef6c00 tenía 3:1 y #b26a00, 4.2:1).
"""
from django.utils.html import format_html

VERDE = '#2e7d32'    # 5.1:1
NARANJA = '#9a5b00'  # 5.4:1
ROJO = '#ba2121'     # 6.3:1
GRIS = '#757575'     # 4.6:1


def etiqueta(texto, color):
    return format_html(
        '<span style="color: #fff; background: {}; padding: 2px 8px; border-radius: 10px;'
        ' white-space: nowrap;">{}</span>',
        color, texto,
    )
