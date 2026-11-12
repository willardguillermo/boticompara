"""
Modelos que mapean las tablas definidas en database/schema.sql.

managed = False: Django NO crea ni altera estas tablas. Cualquier cambio de
estructura se hace solo en database/schema.sql (por Pull Request).
Las columnas actualizado_en las mantiene el trigger set_actualizado_en() de la BD.
"""
from django.db import models
from django.db.models.functions import Now


class Usuario(models.Model):
    class Rol(models.TextChoices):
        COMPRADOR = 'COMPRADOR', 'Comprador'
        DUENO_BOTICA = 'DUENO_BOTICA', 'Dueño de botica'

    id = models.BigAutoField(primary_key=True)
    nombre = models.CharField(max_length=100)
    correo = models.CharField(max_length=150, unique=True)
    # null si la cuenta solo entra con Google (H67). Nunca se muestra en el admin
    password_hash = models.CharField(max_length=255, null=True, blank=True)
    telefono = models.CharField(max_length=20, null=True, blank=True)
    direccion = models.CharField(max_length=255, null=True, blank=True)
    rol = models.CharField(max_length=20, choices=Rol.choices)
    correo_verificado = models.BooleanField(default=False)
    activo = models.BooleanField(default=True)
    creado_en = models.DateTimeField()
    actualizado_en = models.DateTimeField()
    google_uid = models.CharField(max_length=128, unique=True, null=True, blank=True)  # H67
    # H91-H93: no hay columna estado; ver boticas/usuarios.py (estado_de)
    suspendido_hasta = models.DateTimeField('suspendido hasta', null=True, blank=True)
    motivo_estado = models.TextField('motivo del estado', null=True, blank=True)

    class Meta:
        managed = False
        db_table = 'usuario'
        ordering = ['nombre']
        verbose_name = 'usuario'
        verbose_name_plural = 'usuarios'
        # Copia de los CHECK de database/schema.sql. Con managed = False no tocan Supabase;
        # sirven para que las pruebas (SQLite) creen la tabla con las mismas reglas.
        constraints = [
            models.CheckConstraint(
                condition=models.Q(password_hash__isnull=False) | models.Q(google_uid__isnull=False),
                name='chk_usuario_credencial'),
            models.CheckConstraint(
                condition=models.Q(activo=True) | models.Q(suspendido_hasta__isnull=True),
                name='chk_usuario_baja_sin_suspension'),
            models.CheckConstraint(
                condition=models.Q(suspendido_hasta__isnull=True) | models.Q(motivo_estado__isnull=False),
                name='chk_usuario_motivo_suspension'),
        ]

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
    # Enlace opcional al catálogo maestro (H29). on_delete=PROTECT: la BD no deja
    # borrar un medicamento al que apuntan productos
    medicamento = models.ForeignKey(
        'catalogo.Medicamento',
        on_delete=models.PROTECT,
        db_column='medicamento_id',
        related_name='productos',
        null=True,
        blank=True,
    )
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


class BoticaRevision(models.Model):
    """H47: historial de aprobaciones y rechazos. Lo escribe BoticaAdmin; aquí es de solo lectura."""

    class Estado(models.TextChoices):
        APROBADO = 'APROBADO', 'Aprobado'
        RECHAZADO = 'RECHAZADO', 'Rechazado'

    id = models.BigAutoField(primary_key=True)
    # on_delete=DO_NOTHING: el "on delete cascade" lo resuelve la BD
    botica = models.ForeignKey(
        Botica,
        on_delete=models.DO_NOTHING,
        db_column='botica_id',
        related_name='revisiones',
    )
    estado = models.CharField(max_length=20, choices=Estado.choices)
    # Obligatorio si RECHAZADO: check chk_botica_revision_motivo
    motivo = models.TextField(null=True, blank=True)
    # null = revisiones de la carga inicial, anteriores a este historial
    revisado_por = models.CharField('revisado por', max_length=150, null=True, blank=True)
    # La BD pone now() por defecto; db_default hace que Django no la envíe en el insert
    fecha = models.DateTimeField(db_default=Now())

    class Meta:
        managed = False
        db_table = 'botica_revision'
        ordering = ['-fecha']
        verbose_name = 'revisión de botica'
        verbose_name_plural = 'historial de revisiones'

    def __str__(self):
        return f'{self.botica} - {self.get_estado_display()} ({self.fecha:%d/%m/%Y %H:%M})'


class UsuarioAccion(models.Model):
    """H94: historial de suspensiones, reactivaciones y bajas. Lo escribe UsuarioAdmin (boticas/usuarios.py)."""

    class Accion(models.TextChoices):
        SUSPENDER = 'SUSPENDER', 'Suspensión'
        REACTIVAR = 'REACTIVAR', 'Reactivación'
        DAR_BAJA = 'DAR_BAJA', 'Baja'

    id = models.BigAutoField(primary_key=True)
    usuario = models.ForeignKey(
        Usuario,
        on_delete=models.DO_NOTHING,
        db_column='usuario_id',
        related_name='acciones',
    )
    accion = models.CharField('acción', max_length=20, choices=Accion.choices)
    # Obligatorio salvo al reactivar: check chk_usuario_accion_motivo
    motivo = models.TextField(null=True, blank=True)
    realizado_por = models.CharField('realizado por', max_length=150)
    fecha = models.DateTimeField(db_default=Now())

    class Meta:
        managed = False
        db_table = 'usuario_accion'
        ordering = ['-fecha', '-id']
        verbose_name = 'acción sobre un usuario'
        verbose_name_plural = 'historial de acciones sobre usuarios'
        constraints = [
            models.CheckConstraint(condition=models.Q(accion__in=['SUSPENDER', 'REACTIVAR', 'DAR_BAJA']),
                                   name='chk_usuario_accion_accion'),
            models.CheckConstraint(condition=models.Q(accion='REACTIVAR') | models.Q(motivo__isnull=False),
                                   name='chk_usuario_accion_motivo'),
        ]

    def __str__(self):
        return f'{self.usuario} - {self.get_accion_display()} ({self.fecha:%d/%m/%Y %H:%M})'


class Reserva(models.Model):
    """H72-H84: reserva de un producto por el comprador. La maneja Spring Boot; aquí se usará en H95."""

    class Estado(models.TextChoices):
        PENDIENTE = 'PENDIENTE', 'Pendiente'
        CONFIRMADA = 'CONFIRMADA', 'Confirmada'
        LISTA = 'LISTA', 'Lista para recoger'
        ENTREGADA = 'ENTREGADA', 'Entregada'
        RECHAZADA = 'RECHAZADA', 'Rechazada'
        CANCELADA = 'CANCELADA', 'Cancelada'
        VENCIDA = 'VENCIDA', 'Vencida'

    id = models.BigAutoField(primary_key=True)
    codigo = models.CharField('código', max_length=20, unique=True)
    usuario = models.ForeignKey(Usuario, on_delete=models.DO_NOTHING, db_column='usuario_id',
                                related_name='reservas', verbose_name='comprador')
    # La botica se obtiene con el producto
    producto = models.ForeignKey(Producto, on_delete=models.DO_NOTHING, db_column='producto_id',
                                 related_name='reservas')
    cantidad = models.IntegerField()
    precio_unitario = models.DecimalField('precio unitario', max_digits=10, decimal_places=2)
    estado = models.CharField(max_length=20, choices=Estado.choices, default=Estado.PENDIENTE)
    motivo_rechazo = models.TextField('motivo de rechazo', null=True, blank=True)
    vence_en = models.DateTimeField('vence en')
    recordatorio_enviado = models.BooleanField('recordatorio enviado', default=False)
    creado_en = models.DateTimeField(db_default=Now())
    actualizado_en = models.DateTimeField(db_default=Now())

    class Meta:
        managed = False
        db_table = 'reserva'
        ordering = ['-creado_en']
        verbose_name = 'reserva'
        verbose_name_plural = 'reservas'

    def __str__(self):
        return f'{self.codigo} - {self.get_estado_display()}'


class Resena(models.Model):
    """H85-H89: reseña de una botica. La escribe Spring Boot; la moderación del admin llegará en H89."""

    id = models.BigAutoField(primary_key=True)
    botica = models.ForeignKey(Botica, on_delete=models.DO_NOTHING, db_column='botica_id',
                               related_name='resenas')
    usuario = models.ForeignKey(Usuario, on_delete=models.DO_NOTHING, db_column='usuario_id',
                                related_name='resenas', verbose_name='comprador')
    estrellas = models.SmallIntegerField()
    comentario = models.CharField(max_length=500, null=True, blank=True)
    # respuesta y respondida_en van juntas (check chk_resena_respuesta)
    respuesta = models.CharField('respuesta del dueño', max_length=500, null=True, blank=True)
    respondida_en = models.DateTimeField('respondida en', null=True, blank=True)
    oculta = models.BooleanField(default=False)
    motivo_ocultamiento = models.TextField('motivo del ocultamiento', null=True, blank=True)
    creado_en = models.DateTimeField(db_default=Now())
    actualizado_en = models.DateTimeField(db_default=Now())

    class Meta:
        managed = False
        db_table = 'resena'
        ordering = ['-creado_en']
        verbose_name = 'reseña'
        verbose_name_plural = 'reseñas'
        constraints = [
            models.UniqueConstraint(fields=['usuario', 'botica'], name='uq_resena_usuario_botica'),
        ]

    def __str__(self):
        return f'{self.botica} - {self.estrellas}★'
