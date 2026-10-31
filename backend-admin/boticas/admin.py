from django.contrib import admin, messages
from django.db import transaction
from django.urls import reverse
from django.utils import timezone
from django.utils.html import format_html
from django.utils.text import Truncator

from . import historial
from .forms import BoticaRevisionForm
from .models import Botica, BoticaRevision, Producto, Usuario
from .notificaciones import notificar_revision
from .storage import StorageError, url_firmada_licencia

admin.site.site_header = 'BotiCompara - Administración'
admin.site.site_title = 'BotiCompara'
admin.site.index_title = 'Panel de administración'


class SoloLecturaAdmin(admin.ModelAdmin):
    """Permite ver los registros pero no crearlos, editarlos ni borrarlos."""

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False


@admin.register(Usuario)
class UsuarioAdmin(SoloLecturaAdmin):
    list_display = ('nombre', 'correo', 'rol', 'telefono', 'correo_verificado', 'activo', 'creado_en')
    list_filter = ('rol', 'activo', 'correo_verificado')
    search_fields = ('nombre', 'correo')
    # Nunca mostrar el hash de la contraseña
    exclude = ('password_hash',)


@admin.register(Producto)
class ProductoAdmin(SoloLecturaAdmin):
    list_display = ('nombre_comercial', 'principio_activo', 'presentacion', 'precio',
                    'stock', 'stock_minimo', 'botica', 'medicamento', 'activo')
    list_filter = ('activo', 'botica__estado', 'botica')
    search_fields = ('nombre_comercial', 'principio_activo', 'botica__nombre_comercial')
    list_select_related = ('botica', 'medicamento')


class ProductoInline(admin.TabularInline):
    model = Producto
    fields = ('nombre_comercial', 'principio_activo', 'presentacion', 'precio', 'stock', 'activo')
    readonly_fields = fields
    extra = 0
    can_delete = False
    show_change_link = True

    def has_add_permission(self, request, obj=None):
        return False

    def has_change_permission(self, request, obj=None):
        return False


# H47: mismos colores que la columna de vigencia del catálogo
COLORES_REVISION = {
    BoticaRevision.Estado.APROBADO: '#2e7d32',
    BoticaRevision.Estado.RECHAZADO: '#ba2121',
}


class ColumnasRevision:
    """Columnas del historial que comparten la lista y el inline de la ficha de la botica."""

    @admin.display(description='fecha', ordering='fecha')
    def fecha_revision(self, obj):
        # Hora de Lima (TIME_ZONE), no la UTC que guarda la BD
        return f'{timezone.localtime(obj.fecha):%d/%m/%Y %H:%M}'

    @admin.display(description='estado', ordering='estado')
    def estado_revision(self, obj):
        return format_html(
            '<span style="color: #fff; background: {}; padding: 2px 8px; border-radius: 10px;'
            ' white-space: nowrap;">{}</span>',
            COLORES_REVISION.get(obj.estado, '#757575'), obj.get_estado_display(),
        )

    @admin.display(description='revisado por', ordering='revisado_por')
    def revisado_por_texto(self, obj):
        return historial.texto_revisado_por(obj.revisado_por)

    @admin.display(description='motivo')
    def motivo_corto(self, obj):
        return Truncator(obj.motivo).chars(80) if obj.motivo else '—'


class RangoFechasFilter(admin.ListFilter):
    """H47: filtra por fecha de revisión con ?desde=aaaa-mm-dd&hasta=aaaa-mm-dd (días en hora de Lima).

    Es un filtro de la lista (y no un parámetro suelto) para que el changelist conserve
    desde/hasta al combinarlos con el filtro de estado, la búsqueda y la paginación.
    Los campos se dibujan arriba de la lista (boticarevision/change_list.html), no en la barra lateral.
    """
    title = 'fecha de revisión'
    template = 'admin/boticas/boticarevision/filtro_vacio.html'
    parametros = ('desde', 'hasta')

    def __init__(self, request, params, model, model_admin):
        super().__init__(request, params, model, model_admin)
        for nombre in self.parametros:
            if nombre in params:
                self.used_parameters[nombre] = params.pop(nombre)[-1]
        self.desde, self.hasta, self.error = historial.validar_rango(
            self.used_parameters.get('desde'), self.used_parameters.get('hasta'))
        # El changelist puede crear el filtro más de una vez por petición: se avisa una sola vez
        if self.error and not getattr(request, '_aviso_rango_fechas', False):
            request._aviso_rango_fechas = True
            messages.error(request, self.error)

    def has_output(self):
        # True para que el changelist lo aplique; su plantilla no dibuja nada en la barra lateral
        return True

    def choices(self, changelist):
        return []

    def expected_parameters(self):
        return list(self.parametros)

    def queryset(self, request, queryset):
        if self.desde:
            queryset = queryset.filter(fecha__date__gte=self.desde)
        if self.hasta:
            queryset = queryset.filter(fecha__date__lte=self.hasta)
        return queryset


@admin.register(BoticaRevision)
class BoticaRevisionAdmin(ColumnasRevision, SoloLecturaAdmin):
    """H47: historial de aprobaciones y rechazos, de solo lectura."""
    change_list_template = 'admin/boticas/boticarevision/change_list.html'
    list_display = ('fecha_revision', 'botica_enlace', 'ruc', 'estado_revision', 'revisado_por_texto',
                    'motivo_corto')
    list_display_links = ('fecha_revision',)
    list_filter = ('estado', RangoFechasFilter)
    search_fields = ('botica__nombre_comercial', 'botica__ruc', 'revisado_por')
    search_help_text = 'Busca por nombre de la botica, RUC o administrador que la revisó.'
    list_select_related = ('botica',)
    fields = ('fecha_revision', 'botica', 'estado_revision', 'revisado_por_texto', 'motivo')
    readonly_fields = fields

    @admin.display(description='botica', ordering='botica__nombre_comercial')
    def botica_enlace(self, obj):
        return format_html('<a href="{}">{}</a>',
                           reverse('admin:boticas_botica_change', args=[obj.botica_id]),
                           obj.botica.nombre_comercial)

    @admin.display(description='RUC', ordering='botica__ruc')
    def ruc(self, obj):
        return obj.botica.ruc

    def changelist_view(self, request, extra_context=None):
        # Valores que se muestran en los campos Desde/Hasta de arriba de la lista
        extra_context = {**(extra_context or {}),
                         'rango_desde': request.GET.get('desde', ''),
                         'rango_hasta': request.GET.get('hasta', '')}
        return super().changelist_view(request, extra_context)


class RevisionInline(ColumnasRevision, admin.TabularInline):
    """H47: historial de revisiones en la ficha de la botica, de solo lectura."""
    model = BoticaRevision
    fields = ('fecha_revision', 'estado_revision', 'revisado_por_texto', 'motivo_corto')
    readonly_fields = fields
    extra = 0
    can_delete = False
    verbose_name = 'revisión'
    verbose_name_plural = 'historial de revisiones'

    def has_add_permission(self, request, obj=None):
        return False

    def has_change_permission(self, request, obj=None):
        return False


@admin.register(Botica)
class BoticaAdmin(admin.ModelAdmin):
    form = BoticaRevisionForm
    list_display = ('nombre_comercial', 'ruc', 'correo_dueno', 'distrito', 'estado', 'tiene_licencia', 'creado_en')
    list_filter = ('estado', 'distrito')
    search_fields = ('nombre_comercial', 'ruc', 'usuario__correo')
    list_select_related = ('usuario',)
    inlines = [RevisionInline, ProductoInline]
    actions = ['aprobar_boticas']

    # Los datos los registra el dueño desde la web: aquí solo se revisan
    readonly_fields = ('usuario', 'correo_dueno', 'nombre_comercial', 'ruc', 'razon_social',
                       'direccion', 'distrito', 'telefono', 'ubicacion', 'licencia',
                       'creado_en', 'actualizado_en')
    fieldsets = (
        ('Datos de la botica', {
            'fields': ('nombre_comercial', 'ruc', 'razon_social', 'direccion', 'distrito', 'telefono'),
        }),
        ('Evidencia para la aprobación', {'fields': ('licencia', 'ubicacion')}),
        ('Dueño', {'fields': ('usuario', 'correo_dueno')}),
        ('Revisión', {'fields': ('estado', 'motivo_rechazo')}),
        ('Fechas', {'fields': ('creado_en', 'actualizado_en')}),
    )

    @admin.display(description='correo del dueño', ordering='usuario__correo')
    def correo_dueno(self, obj):
        return obj.usuario.correo

    @admin.display(description='licencia', boolean=True, ordering='licencia_url')
    def tiene_licencia(self, obj):
        return bool(obj.licencia_url)

    @admin.display(description='licencia de funcionamiento')
    def licencia(self, obj):
        # H4: el bucket es privado, se genera una URL firmada que vence en 5 minutos
        if not obj.licencia_url:
            return 'Sin licencia cargada'
        try:
            url = url_firmada_licencia(obj.licencia_url)
        except StorageError as e:
            return format_html('<span style="color: var(--error-fg, #ba2121);">No se pudo generar el enlace: {}</span>', e)
        return format_html(
            '<a href="{}" target="_blank" rel="noopener noreferrer">Ver licencia</a>'
            ' <span class="help">(el enlace vence en 5 minutos; recarga la página para generar otro)</span>',
            url,
        )

    @admin.display(description='ubicación')
    def ubicacion(self, obj):
        # H3: coordenadas opcionales registradas por el dueño en el mapa de la web
        if obj.latitud is None or obj.longitud is None:
            return 'Sin ubicación registrada'
        lat, lng = float(obj.latitud), float(obj.longitud)
        delta = 0.004
        return format_html(
            '{}, {} &nbsp; <a href="https://www.openstreetmap.org/?mlat={}&amp;mlon={}#map=17/{}/{}"'
            ' target="_blank" rel="noopener noreferrer">Ver en el mapa</a>'
            '<br><iframe title="Ubicación de la botica" width="420" height="260" loading="lazy"'
            ' style="border: 1px solid var(--border-color, #ccc); margin-top: 8px; max-width: 100%;"'
            ' src="https://www.openstreetmap.org/export/embed.html?bbox={},{},{},{}&amp;layer=mapnik&amp;marker={},{}">'
            '</iframe>',
            obj.latitud, obj.longitud, lat, lng, lat, lng,
            round(lng - delta, 6), round(lat - delta, 6), round(lng + delta, 6), round(lat + delta, 6), lat, lng,
        )

    @admin.action(description='Aprobar boticas seleccionadas', permissions=['change'])
    def aprobar_boticas(self, request, queryset):
        # Solo las que realmente cambian de estado reciben el correo (H6)
        # H47: el cambio de estado y su historial se guardan juntos o no se guarda nada
        with transaction.atomic():
            # select_for_update: si otro administrador la aprueba a la vez, no se registra dos veces
            # (se vuelve a consultar por pk: el queryset del changelist puede traer DISTINCT)
            por_aprobar = list(Botica.objects.filter(pk__in=queryset.values('pk'))
                               .exclude(estado=Botica.Estado.APROBADO)
                               .select_related('usuario').select_for_update(of=('self',)))
            Botica.objects.filter(pk__in=[b.pk for b in por_aprobar]).update(
                estado=Botica.Estado.APROBADO, motivo_rechazo=None)
            BoticaRevision.objects.bulk_create([
                BoticaRevision(botica=b, estado=BoticaRevision.Estado.APROBADO,
                               revisado_por=request.user.get_username())
                for b in por_aprobar
            ])
            for botica in por_aprobar:
                botica.estado, botica.motivo_rechazo = Botica.Estado.APROBADO, None
                transaction.on_commit(lambda b=botica: self._notificar(request, b))
        self.message_user(request, f'{len(por_aprobar)} botica(s) aprobada(s).', messages.SUCCESS)

    def _notificar(self, request, botica):
        if not notificar_revision(botica):
            self.message_user(
                request,
                f'No se pudo enviar el correo a {botica.usuario.correo} ({botica.nombre_comercial}). '
                'La revisión sí se guardó.',
                messages.WARNING,
            )

    def has_add_permission(self, request):
        return False

    def has_delete_permission(self, request, obj=None):
        return False

    def save_model(self, request, obj, form, change):
        # Estado guardado en la BD justo antes de escribir, para avisar solo si cambia (H6).
        # select_for_update: el changeform del admin ya corre en una transacción
        anterior = (Botica.objects.select_for_update().filter(pk=obj.pk)
                    .values_list('estado', flat=True).first())
        # Solo se escribe la revisión, para no pisar datos que el dueño
        # haya cambiado desde la web mientras el admin tenía el formulario abierto
        obj.save(update_fields=['estado', 'motivo_rechazo'])
        if obj.estado != anterior and obj.estado in (Botica.Estado.APROBADO, Botica.Estado.RECHAZADO):
            # H47: en la misma transacción que el guardado; si falla, la botica no queda cambiada
            BoticaRevision.objects.create(
                botica=obj,
                estado=obj.estado,
                motivo=obj.motivo_rechazo if obj.estado == Botica.Estado.RECHAZADO else None,
                revisado_por=request.user.get_username(),
            )
            # Después del commit: si algo falla al guardar, no se avisa de un cambio que no ocurrió
            transaction.on_commit(lambda: self._notificar(request, obj))
