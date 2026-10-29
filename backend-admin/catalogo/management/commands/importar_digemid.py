"""
H46: carga el catálogo maestro desde un archivo .xlsx o .csv con el formato de DIGEMID.

Crea los medicamentos nuevos y actualiza los existentes usando el registro sanitario
como clave. Las filas con error no detienen la carga: se listan al final.

    python manage.py importar_digemid catalogo/muestras/catalogo-digemid-muestra.xlsx --simulacion
    python manage.py importar_digemid archivo.xlsx       # guarda de verdad
"""
import os

from django.core.management.base import BaseCommand, CommandError

from catalogo.digemid import importador, lector


class Command(BaseCommand):
    help = 'Importa el catálogo maestro desde un Excel o CSV con las columnas de DIGEMID (H46).'

    def add_arguments(self, parser):
        parser.add_argument('archivo', help='Ruta del archivo .xlsx o .csv.')
        parser.add_argument(
            '--simulacion',
            action='store_true',
            help='Hace toda la carga y la deshace al final: no se guarda nada.',
        )

    def handle(self, *args, **opciones):
        ruta = opciones['archivo']
        if not os.path.isfile(ruta):
            raise CommandError(f'No existe el archivo {ruta}.')
        try:
            resultado = importador.importar_archivo(ruta, ruta, simulacion=opciones['simulacion'])
        except lector.ArchivoInvalido as exc:
            raise CommandError(str(exc)) from exc

        self.stdout.write(f'Archivo: {ruta} ({resultado.filas_leidas} fila(s) leída(s))')
        self.stdout.write(self.style.SUCCESS(f'Creados: {resultado.creados}'))
        self.stdout.write(self.style.SUCCESS(f'Actualizados: {resultado.actualizados}'))
        estilo_errores = self.style.ERROR if resultado.errores else self.style.SUCCESS
        self.stdout.write(estilo_errores(f'Filas con error: {len(resultado.errores)}'))
        self.stdout.write(f'Principios activos nuevos: {len(resultado.principios_creados)}')
        for nombre in resultado.principios_creados:
            self.stdout.write(f'  - {nombre}')

        if resultado.errores:
            ancho = max(len('Reg Sanitario'), *(len(registro) for _, registro, _ in resultado.errores))
            self.stdout.write('')
            self.stdout.write(f'{"Fila":>4}  {"Reg Sanitario":<{ancho}}  Motivo')
            for fila, registro, motivo in resultado.errores:
                self.stdout.write(f'{fila:>4}  {registro or "(vacío)":<{ancho}}  {motivo}')

        if resultado.simulacion:
            self.stdout.write('')
            self.stdout.write(self.style.WARNING('SIMULACIÓN: no se guardó nada.'))
