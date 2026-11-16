"""
H95: ranking de las boticas más activas ("para identificar a los socios más comprometidos").

Está separado del admin para poder probarlo solo. Cada indicador se cuenta con una consulta agrupada
por botica (así no se multiplican filas al cruzar reservas, reseñas y productos) y se junta aquí.

Puntaje = 3 × pedidos entregados + 2 × reseñas respondidas + 1 × productos actualizados.
"""
from django.db.models import Avg, Count

from boticas.models import Botica, Producto, Resena, Reserva
from catalogo.models import ConsultaComparacion

from .consultas import en_rango

PUNTOS_POR_PEDIDO = 3
PUNTOS_POR_RESPUESTA = 2
PUNTOS_POR_PRODUCTO = 1

# Nota que va debajo de la tabla y al final del Excel
FORMULA = (f'Cada pedido entregado suma {PUNTOS_POR_PEDIDO} puntos, cada reseña respondida suma '
           f'{PUNTOS_POR_RESPUESTA} y cada producto actualizado suma {PUNTOS_POR_PRODUCTO}, todo dentro del '
           'periodo elegido. Si dos boticas empatan, va primero la de mejor calificación promedio y luego '
           'por orden alfabético. La calificación cuenta todas las reseñas visibles (no las ocultas) y '
           '"Veces comparado" es solo informativo: no suma puntos.')


def _por_botica(consulta, campo_botica, **agregado):
    """{botica_id: valor} de una consulta agrupada por la botica."""
    nombre, funcion = next(iter(agregado.items()))
    return dict(consulta.values_list(campo_botica).annotate(**{nombre: funcion}).order_by())


def _orden(fila):
    # Puntaje de mayor a menor; luego la mejor calificación (sin reseñas al final); luego el nombre
    promedio = fila['calificacion']
    return (-fila['puntaje'], promedio is None, -(promedio or 0), fila['botica'].casefold(), fila['botica_id'])


def ranking_de_boticas(desde, hasta):
    """
    Boticas APROBADO con al menos un punto en el periodo (desde <= fecha < hasta; None: sin límite).

    Cada fila trae: puesto, botica_id, botica, distrito, pedidos_entregados, resenas_respondidas,
    productos_actualizados, puntaje, calificacion (promedio de las reseñas no ocultas, de siempre;
    None si no tiene) y veces_comparado (solo informativo, no suma al puntaje).
    """
    aprobadas = Botica.objects.filter(estado=Botica.Estado.APROBADO)
    ids = aprobadas.values('id')

    # Entregada: la fecha de entrega es la última actualización de la reserva
    pedidos = _por_botica(
        en_rango(Reserva.objects.filter(estado=Reserva.Estado.ENTREGADA, producto__botica__in=ids),
                 desde, hasta, 'actualizado_en'),
        'producto__botica_id', total=Count('id'))
    respuestas = _por_botica(
        en_rango(Resena.objects.filter(botica__in=ids), desde, hasta, 'respondida_en'),
        'botica_id', total=Count('id'))
    productos = _por_botica(
        en_rango(Producto.objects.filter(botica__in=ids), desde, hasta, 'actualizado_en'),
        'botica_id', total=Count('id'))
    comparado = _por_botica(
        en_rango(ConsultaComparacion.objects.filter(producto__botica__in=ids), desde, hasta),
        'producto__botica_id', total=Count('id'))
    calificaciones = _por_botica(
        Resena.objects.filter(botica__in=ids, oculta=False), 'botica_id', promedio=Avg('estrellas'))

    filas = []
    for botica_id, nombre, distrito in aprobadas.values_list('id', 'nombre_comercial', 'distrito'):
        # PostgreSQL devuelve el promedio como Decimal y SQLite como float: se deja en float
        promedio = calificaciones.get(botica_id)
        fila = {
            'botica_id': botica_id,
            'botica': nombre,
            'distrito': distrito,
            'pedidos_entregados': pedidos.get(botica_id, 0),
            'resenas_respondidas': respuestas.get(botica_id, 0),
            'productos_actualizados': productos.get(botica_id, 0),
            'calificacion': float(promedio) if promedio is not None else None,
            'veces_comparado': comparado.get(botica_id, 0),
        }
        fila['puntaje'] = (PUNTOS_POR_PEDIDO * fila['pedidos_entregados']
                           + PUNTOS_POR_RESPUESTA * fila['resenas_respondidas']
                           + PUNTOS_POR_PRODUCTO * fila['productos_actualizados'])
        if fila['puntaje']:
            filas.append(fila)

    filas.sort(key=_orden)
    for puesto, fila in enumerate(filas, start=1):
        fila['puesto'] = puesto
    return filas
