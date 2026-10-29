"""
Modelos del catálogo maestro (H29-H32), definidos en database/v3_catalogo_maestro.sql
(y en database/schema.sql).

managed = False: Django NO crea ni altera estas tablas. Las reglas que Django no
expresa (nombres únicos sin distinguir mayúsculas, registro sanitario único si
existe, FK compuestas de medicamento_grupo) las garantiza la BD; los formularios
de catalogo/forms.py las validan antes para mostrar un error amigable.
"""
from django.db import models

from boticas.models import Producto, Usuario


class Categoria(models.Model):
    id = models.BigAutoField(primary_key=True)
    # Único sin distinguir mayúsculas: índice uq_categoria_nombre sobre lower(nombre)
    nombre = models.CharField(max_length=100)

    class Meta:
        managed = False
        db_table = 'categoria'
        ordering = ['nombre']
        verbose_name = 'categoría'
        verbose_name_plural = 'categorías'

    def __str__(self):
        return self.nombre


class PrincipioActivo(models.Model):
    id = models.BigAutoField(primary_key=True)
    # Único sin distinguir mayúsculas: índice uq_principio_activo_nombre sobre lower(nombre)
    nombre = models.CharField(max_length=150)
    descripcion = models.TextField('descripción', null=True, blank=True)

    class Meta:
        managed = False
        db_table = 'principio_activo'
        ordering = ['nombre']
        verbose_name = 'principio activo'
        verbose_name_plural = 'principios activos'

    def __str__(self):
        return self.nombre


class Medicamento(models.Model):
    id = models.BigAutoField(primary_key=True)
    nombre_comercial = models.CharField(max_length=150)
    presentacion = models.CharField('presentación', max_length=100)
    concentracion = models.CharField('concentración', max_length=50, null=True, blank=True)
    # on_delete=PROTECT: la BD no deja borrar un principio activo o una categoría en uso
    principio_activo = models.ForeignKey(
        PrincipioActivo,
        on_delete=models.PROTECT,
        db_column='principio_activo_id',
        related_name='medicamentos',
    )
    categoria = models.ForeignKey(
        Categoria,
        on_delete=models.PROTECT,
        db_column='categoria_id',
        related_name='medicamentos',
        null=True,
        blank=True,
        verbose_name='categoría',
    )
    # Único cuando existe: índice parcial uq_medicamento_registro
    registro_sanitario = models.CharField(max_length=50, null=True, blank=True)
    fecha_vencimiento_registro = models.DateField('vencimiento del registro', null=True, blank=True)
    oculto = models.BooleanField(default=False, help_text='Lo retira de la búsqueda sin borrarlo.')
    # H46: datos del catálogo de DIGEMID (los llena la carga masiva)
    forma_farmaceutica = models.CharField('forma farmacéutica', max_length=100, null=True, blank=True)
    titular = models.CharField(max_length=200, null=True, blank=True)
    # No es único: puede repetirse entre presentaciones (índice idx_medicamento_codigo_digemid)
    codigo_digemid = models.CharField('código DIGEMID', max_length=30, null=True, blank=True)

    class Meta:
        managed = False
        db_table = 'medicamento'
        ordering = ['nombre_comercial']
        verbose_name = 'medicamento'
        verbose_name_plural = 'medicamentos'

    def __str__(self):
        partes = [self.nombre_comercial, self.concentracion, self.presentacion]
        return ' - '.join(p for p in partes if p)


class GrupoEquivalencia(models.Model):
    id = models.BigAutoField(primary_key=True)
    nombre = models.CharField(max_length=150)
    principio_activo = models.ForeignKey(
        PrincipioActivo,
        on_delete=models.PROTECT,
        db_column='principio_activo_id',
        related_name='grupos_equivalencia',
    )

    class Meta:
        managed = False
        db_table = 'grupo_equivalencia'
        ordering = ['nombre']
        verbose_name = 'grupo de equivalencia'
        verbose_name_plural = 'grupos de equivalencia'

    def __str__(self):
        return self.nombre


class MedicamentoGrupo(models.Model):
    # La PK sobre medicamento_id impide que un medicamento esté en dos grupos.
    # Las FK compuestas (medicamento_id, principio_activo_id) y
    # (grupo_id, principio_activo_id) no se expresan en Django: las aplica la BD.
    # on_delete=DO_NOTHING: el "on delete cascade" lo resuelve la BD
    medicamento = models.OneToOneField(
        Medicamento,
        on_delete=models.DO_NOTHING,
        primary_key=True,
        db_column='medicamento_id',
        related_name='grupo_asignado',
    )
    grupo = models.ForeignKey(
        GrupoEquivalencia,
        on_delete=models.DO_NOTHING,
        db_column='grupo_id',
        related_name='miembros',
    )
    principio_activo = models.ForeignKey(
        PrincipioActivo,
        on_delete=models.DO_NOTHING,
        db_column='principio_activo_id',
        related_name='+',
    )

    class Meta:
        managed = False
        db_table = 'medicamento_grupo'
        verbose_name = 'medicamento de un grupo'
        verbose_name_plural = 'medicamentos de un grupo'

    def __str__(self):
        return f'{self.medicamento} en {self.grupo}'


class ConsultaComparacion(models.Model):
    id = models.BigAutoField(primary_key=True)
    producto = models.ForeignKey(
        Producto,
        on_delete=models.DO_NOTHING,
        db_column='producto_id',
        related_name='consultas_comparacion',
    )
    # null si la consulta fue anónima; el "on delete set null" lo resuelve la BD
    usuario = models.ForeignKey(
        Usuario,
        on_delete=models.DO_NOTHING,
        db_column='usuario_id',
        related_name='consultas_comparacion',
        null=True,
        blank=True,
    )
    fecha = models.DateTimeField()

    class Meta:
        managed = False
        db_table = 'consulta_comparacion'
        ordering = ['-fecha']
        verbose_name = 'consulta de comparación'
        verbose_name_plural = 'consultas de comparación'

    def __str__(self):
        return f'{self.producto} ({self.fecha:%d/%m/%Y %H:%M})'


class Favorito(models.Model):
    pk = models.CompositePrimaryKey('usuario_id', 'producto_id')
    usuario = models.ForeignKey(
        Usuario,
        on_delete=models.DO_NOTHING,
        db_column='usuario_id',
        related_name='favoritos',
    )
    producto = models.ForeignKey(
        Producto,
        on_delete=models.DO_NOTHING,
        db_column='producto_id',
        related_name='favoritos',
    )
    fecha = models.DateTimeField()

    class Meta:
        managed = False
        db_table = 'favorito'
        ordering = ['-fecha']
        verbose_name = 'favorito'
        verbose_name_plural = 'favoritos'

    def __str__(self):
        return f'{self.usuario} - {self.producto}'
