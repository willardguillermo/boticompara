"""
Modelos que mapean las tablas definidas en database/schema.sql.

managed = False: Django NO crea ni altera estas tablas. Cualquier cambio de
estructura se hace solo en database/schema.sql (por Pull Request).
Las columnas actualizado_en las mantiene el trigger set_actualizado_en() de la BD.
"""
from django.db import models


class Usuario(models.Model):
    class Rol(models.TextChoices):
        COMPRADOR = 'COMPRADOR', 'Comprador'
        DUENO_BOTICA = 'DUENO_BOTICA', 'Dueño de botica'

    id = models.BigAutoField(primary_key=True)
    nombre = models.CharField(max_length=100)
    correo = models.CharField(max_length=150, unique=True)
    password_hash = models.CharField(max_length=255)
    telefono = models.CharField(max_length=20, null=True, blank=True)
    direccion = models.CharField(max_length=255, null=True, blank=True)
    rol = models.CharField(max_length=20, choices=Rol.choices)
    correo_verificado = models.BooleanField(default=False)
    activo = models.BooleanField(default=True)
    creado_en = models.DateTimeField()
    actualizado_en = models.DateTimeField()

    class Meta:
        managed = False
        db_table = 'usuario'
        ordering = ['nombre']
        verbose_name = 'usuario'
        verbose_name_plural = 'usuarios'

    def __str__(self):
        return f'{self.nombre} <{self.correo}>'


class Botica(models.Model):
    class Estado(models.TextChoices):
        PENDIENTE = 'PENDIENTE', 'Pendiente'
        APROBADO = 'APROBADO', 'Aprobado'
        RECHAZADO = 'RECHAZADO', 'Rechazado'

    id = models.BigAutoField(primary_key=True)
    # on_delete=DO_NOTHING: el "on delete cascade" lo resuelve la BD
    usuario = models.OneToOneField(
        Usuario,
        on_delete=models.DO_NOTHING,
        db_column='usuario_id',
        related_name='botica',
        verbose_name='dueño',
    )
    nombre_comercial = models.CharField(max_length=150)
    ruc = models.CharField('RUC', max_length=11, unique=True)
    razon_social = models.CharField('razón social', max_length=200)
    direccion = models.CharField('dirección', max_length=255)
    distrito = models.CharField(max_length=100)
    telefono = models.CharField('teléfono', max_length=20, null=True, blank=True)
    latitud = models.DecimalField(max_digits=9, decimal_places=6, null=True, blank=True)
    longitud = models.DecimalField(max_digits=9, decimal_places=6, null=True, blank=True)
    licencia_url = models.TextField('licencia (URL)', null=True, blank=True)
    estado = models.CharField(max_length=20, choices=Estado.choices, default=Estado.PENDIENTE)
    motivo_rechazo = models.TextField('motivo de rechazo', null=True, blank=True)
    creado_en = models.DateTimeField()
    actualizado_en = models.DateTimeField()

    class Meta:
        managed = False
        db_table = 'botica'
        ordering = ['-creado_en']
        verbose_name = 'botica'
        verbose_name_plural = 'boticas'

    def __str__(self):
        return f'{self.nombre_comercial} ({self.ruc})'


class Producto(models.Model):
    id = models.BigAutoField(primary_key=True)
    botica = models.ForeignKey(
        Botica,
        on_delete=models.DO_NOTHING,
        db_column='botica_id',
        related_name='productos',
    )
    nombre_comercial = models.CharField(max_length=150)
    principio_activo = models.CharField(max_length=150)
    presentacion = models.CharField('presentación', max_length=100)
    precio = models.DecimalField(max_digits=10, decimal_places=2)
    stock = models.IntegerField()
    stock_minimo = models.IntegerField('stock mínimo', default=5)
    activo = models.BooleanField(default=True)
    creado_en = models.DateTimeField()
    actualizado_en = models.DateTimeField()

    class Meta:
        managed = False
        db_table = 'producto'
        ordering = ['nombre_comercial']
        verbose_name = 'producto'
        verbose_name_plural = 'productos'

    def __str__(self):
        return f'{self.nombre_comercial} - {self.presentacion}'
