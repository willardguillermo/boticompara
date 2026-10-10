from django.contrib import admin, messages
from django.utils.html import format_html

from .forms import BoticaRevisionForm
from .models import Botica, Producto, Usuario
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
                    'stock', 'stock_minimo', 'botica', 'activo')
    list_filter = ('activo', 'botica__estado', 'botica')
    search_fields = ('nombre_comercial', 'principio_activo', 'botica__nombre_comercial')
    list_select_related = ('botica',)


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


@admin.register(Botica)
class BoticaAdmin(admin.ModelAdmin):
    form = BoticaRevisionForm
    list_display = ('nombre_comercial', 'ruc', 'correo_dueno', 'distrito', 'estado', 'tiene_licencia', 'creado_en')
    list_filter = ('estado', 'distrito')
    search_fields = ('nombre_comercial', 'ruc', 'usuario__correo')
    list_select_related = ('usuario',)
    inlines = [ProductoInline]
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
        actualizadas = (queryset.exclude(estado=Botica.Estado.APROBADO)
                        .update(estado=Botica.Estado.APROBADO, motivo_rechazo=None))
        self.message_user(request, f'{actualizadas} botica(s) aprobada(s).', messages.SUCCESS)

    def has_add_permission(self, request):
        return False

    def has_delete_permission(self, request, obj=None):
        return False

    def save_model(self, request, obj, form, change):
        # Solo se escribe la revisión, para no pisar datos que el dueño
        # haya cambiado desde la web mientras el admin tenía el formulario abierto
        obj.save(update_fields=['estado', 'motivo_rechazo'])
