"""
Modelos de los reportes del administrador.

managed = False: Django NO crea ni altera la tabla busqueda, que se define solo en
database/schema.sql. La llena Spring Boot con cada búsqueda del comprador; aquí solo se lee.
"""
from django.db import models
from django.db.models.functions import Now


class Busqueda(models.Model):
    """Una búsqueda hecha por un comprador (o anónima) en la app móvil."""

    class Tipo(models.TextChoices):
        NOMBRE = 'nombre', 'Nombre comercial'
        PRINCIPIO = 'principio', 'Principio activo'

    id = models.BigAutoField(primary_key=True)
    # Solo el id: el reporte no necesita al comprador. null = búsqueda anónima o usuario borrado
    usuario_id = models.BigIntegerField(null=True, blank=True)
    texto = models.CharField(max_length=150)
    tipo = models.CharField(max_length=20, choices=Tipo.choices)
    resultados = models.IntegerField()
    fecha = models.DateTimeField(db_default=Now())

    class Meta:
        managed = False
        db_table = 'busqueda'
        ordering = ['-fecha']
        verbose_name = 'medicamento más buscado'
        verbose_name_plural = 'medicamentos más buscados'

    def __str__(self):
        return f'{self.texto} ({self.tipo})'
