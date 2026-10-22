"""
H34: lista los medicamentos con el registro sanitario vencido y los que vencen en los
próximos 30 días, con cuántos productos de boticas están enlazados a cada uno.

Sin --ocultar solo lee. Con --ocultar pide confirmación y marca oculto = true en los
vencidos que aún se muestran (H35): sus productos dejan de salir en la búsqueda.

    python manage.py revisar_registros_sanitarios            # solo muestra el reporte
    python manage.py revisar_registros_sanitarios --ocultar  # pide confirmación y oculta los vencidos
"""
from django.core.management.base import BaseCommand
from django.db.models import Count

from catalogo import vigencia
from catalogo.models import Medicamento


class Command(BaseCommand):
    help = ('Lista los medicamentos con registro sanitario vencido o por vencer en '
            f'{vigencia.DIAS_POR_VENCER} días (H34).')

    def add_arguments(self, parser):
        parser.add_argument(
            '--ocultar',
            action='store_true',
            help='Marca oculto = true en los medicamentos vencidos (pide confirmación).',
        )

    def handle(self, *args, **opciones):
        dia = vigencia.hoy()
        base = (Medicamento.objects
                .annotate(num_productos=Count('productos'))
                .order_by('fecha_vencimiento_registro', 'nombre_comercial'))
        vencidos = list(base.filter(vigencia.condicion(vigencia.VENCIDO, dia)))
        por_vencer = list(base.filter(vigencia.condicion(vigencia.POR_VENCER, dia)))

        self.stdout.write(f'Revisión del {dia:%d/%m/%Y}')
        self._listar(f'Vencidos: {len(vencidos)}', vencidos, self.style.ERROR)
        self._listar(f'Vencen en los próximos {vigencia.DIAS_POR_VENCER} días: {len(por_vencer)}',
                     por_vencer, self.style.WARNING)

        if not opciones['ocultar']:
            return

        por_ocultar = [m for m in vencidos if not m.oculto]
        if not por_ocultar:
            self.stdout.write(self.style.SUCCESS('No hay medicamentos vencidos por ocultar.'))
            return

        respuesta = input(f'¿Ocultar {len(por_ocultar)} medicamento(s) vencido(s) del catálogo? [s/N] ')
        if respuesta.strip().lower() not in ('s', 'si', 'sí'):
            self.stdout.write('Cancelado: no se ocultó nada.')
            return

        # Se vuelve a filtrar por fecha por si alguien renovó el registro mientras tanto
        ocultados = (Medicamento.objects
                     .filter(pk__in=[m.pk for m in por_ocultar], oculto=False)
                     .filter(vigencia.condicion(vigencia.VENCIDO, dia))
                     .update(oculto=True))
        self.stdout.write(self.style.SUCCESS(f'Se ocultaron {ocultados} medicamento(s).'))

    def _listar(self, titulo, medicamentos, estilo):
        self.stdout.write('')
        self.stdout.write(estilo(titulo))
        for m in medicamentos:
            marca = ' [oculto]' if m.oculto else ''
            self.stdout.write(
                f'  - {m}{marca} | registro {m.registro_sanitario or "(sin número)"} | '
                f'vence {m.fecha_vencimiento_registro:%d/%m/%Y} | {m.num_productos} producto(s) enlazado(s)'
            )
