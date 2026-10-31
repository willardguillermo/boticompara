"""
H47: reglas del historial de revisiones que no necesitan la BD (se prueban con SimpleTestCase).
"""
from datetime import datetime

SIN_REVISOR = 'Sin registro (carga inicial)'

# El <input type="date"> envía aaaa-mm-dd; también se acepta dd/mm/aaaa escrito a mano
FORMATOS_FECHA = ('%Y-%m-%d', '%d/%m/%Y')


def texto_revisado_por(revisado_por):
    """Las revisiones anteriores a este historial no guardaron quién las hizo."""
    return revisado_por or SIN_REVISOR


def _fecha(texto, etiqueta):
    texto = (texto or '').strip()
    if not texto:
        return None, None
    for formato in FORMATOS_FECHA:
        try:
            return datetime.strptime(texto, formato).date(), None
        except ValueError:
            continue
    return None, (f'La fecha «{etiqueta}» ({texto}) no es válida (usa dd/mm/aaaa): '
                  'no se aplicó el filtro de fechas.')


def validar_rango(desde, hasta):
    """(desde, hasta, error). Con error no se filtra nada; vacío = sin límite por ese lado."""
    fecha_desde, error_desde = _fecha(desde, 'Desde')
    fecha_hasta, error_hasta = _fecha(hasta, 'Hasta')
    error = error_desde or error_hasta
    if not error and fecha_desde and fecha_hasta and fecha_desde > fecha_hasta:
        error = (f'La fecha «Desde» ({fecha_desde:%d/%m/%Y}) es posterior a «Hasta» '
                 f'({fecha_hasta:%d/%m/%Y}): no se aplicó el filtro de fechas.')
    if error:
        return None, None, error
    return fecha_desde, fecha_hasta, None
