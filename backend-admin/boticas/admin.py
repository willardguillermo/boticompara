from django.contrib import admin

from .models import Botica, Producto, Usuario

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
    list_display = ('nombre_comercial', 'ruc', 'correo_dueno', 'distrito', 'estado', 'creado_en')
    list_filter = ('estado', 'distrito')
    search_fields = ('nombre_comercial', 'ruc', 'usuario__correo')
    list_select_related = ('usuario',)
    inlines = [ProductoInline]

    # Los datos los registra el dueño desde la web: aquí solo se revisan
    readonly_fields = ('usuario', 'correo_dueno', 'nombre_comercial', 'ruc', 'razon_social',
                       'direccion', 'distrito', 'telefono', 'latitud', 'longitud',
                       'licencia_url', 'creado_en', 'actualizado_en')
    fieldsets = (
        ('Datos de la botica', {
            'fields': ('nombre_comercial', 'ruc', 'razon_social', 'direccion', 'distrito',
                       'telefono', 'latitud', 'longitud', 'licencia_url'),
        }),
        ('Dueño', {'fields': ('usuario', 'correo_dueno')}),
        ('Revisión', {'fields': ('estado', 'motivo_rechazo')}),
        ('Fechas', {'fields': ('creado_en', 'actualizado_en')}),
    )

    @admin.display(description='correo del dueño', ordering='usuario__correo')
    def correo_dueno(self, obj):
        return obj.usuario.correo

    def has_add_permission(self, request):
        return False

    def has_delete_permission(self, request, obj=None):
        return False

    def save_model(self, request, obj, form, change):
        # Solo se escribe la revisión, para no pisar datos que el dueño
        # haya cambiado desde la web mientras el admin tenía el formulario abierto
        obj.save(update_fields=['estado', 'motivo_rechazo'])
