"""
H30: crea en principio_activo los nombres que ya usan los productos activos de las boticas.

Lee los valores distintos de producto.principio_activo (solo activo = true) normalizados con
initcap(trim(...)) y crea solo los que falten (comparando sin distinguir
mayúsculas, igual que el índice uq_principio_activo_nombre). Se puede repetir:
la segunda vez no crea nada.

    python manage.py poblar_principios_activos --dry-run   # solo muestra el resumen
    python manage.py poblar_principios_activos             # pide confirmación y crea
"""
from django.core.management.base import BaseCommand
from django.db import transaction
from django.db.models import Count, Func
from django.db.models.functions import Lower, Trim

from boticas.models import Producto
from catalogo.models import PrincipioActivo


class Initcap(Func):
    function = 'INITCAP'


class Command(BaseCommand):
    help = 'Crea los principios activos que faltan a partir de producto.principio_activo de los productos activos (H30).'

    def add_arguments(self, parser):
        parser.add_argument(
            '--dry-run',
            action='store_true',
            help='Muestra lo que se crearía sin escribir en la base de datos.',
        )

    def handle(self, *args, **opciones):
        encontrados = list(
            Producto.objects
            .filter(activo=True)
            .annotate(nombre=Initcap(Trim('principio_activo')))
            .exclude(nombre='')
            .values('nombre')
            .annotate(productos=Count('id'))
            .order_by('nombre')
        )
        existentes = set(PrincipioActivo.objects.annotate(clave=Lower('nombre')).values_list('clave', flat=True))
        por_crear = [f for f in encontrados if f['nombre'].lower() not in existentes]

        self.stdout.write(f'Principios activos distintos en productos activos: {len(encontrados)}')
        self.stdout.write(f'Ya registrados en principio_activo:               {len(encontrados) - len(por_crear)}')
        self.stdout.write(f'Por crear:                                        {len(por_crear)}')
        for fila in por_crear:
            self.stdout.write(f'  + {fila["nombre"]} ({fila["productos"]} producto(s))')

        if not por_crear:
            self.stdout.write(self.style.SUCCESS('No hay principios activos por crear.'))
            return
        if opciones['dry_run']:
            self.stdout.write(self.style.WARNING('Modo --dry-run: no se escribió nada en la base de datos.'))
            return

        respuesta = input(f'¿Crear {len(por_crear)} principio(s) activo(s)? [s/N] ').strip().lower()
        if respuesta not in ('s', 'si', 'sí'):
            self.stdout.write('Cancelado: no se creó nada.')
            return

        with transaction.atomic():
            antes = PrincipioActivo.objects.count()
            # ignore_conflicts: si otro proceso creó el mismo nombre mientras tanto,
            # el índice único de la BD lo descarta en vez de fallar
            PrincipioActivo.objects.bulk_create(
                [PrincipioActivo(nombre=f['nombre']) for f in por_crear],
                ignore_conflicts=True,
            )
            creados = PrincipioActivo.objects.count() - antes
        self.stdout.write(self.style.SUCCESS(f'Se crearon {creados} principio(s) activo(s).'))
