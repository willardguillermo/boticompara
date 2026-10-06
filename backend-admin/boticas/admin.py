from django.contrib import admin

from .models import Producto, Usuario

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
