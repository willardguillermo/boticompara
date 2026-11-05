from django.contrib import admin
from django.template.response import TemplateResponse
from django.urls import path
from django.utils import timezone

from .consultas import DIAS_POR_DEFECTO, MAXIMO_FILAS, mas_buscados, rango_por_defecto
from .models import Busqueda


@admin.register(Busqueda)
class MasBuscadosAdmin(admin.ModelAdmin):
    """
    H62: reporte de solo lectura. En vez de la lista de filas normal, la página muestra la
    consulta agregada. Aparece en el índice del admin como Reportes › Medicamentos más buscados.
    """

    # Cualquier usuario staff lo ve (no hace falta asignarle permisos); nadie crea, edita ni borra.
    def has_module_permission(self, request):
        return request.user.is_active and request.user.is_staff

    def has_view_permission(self, request, obj=None):
        return request.user.is_active and request.user.is_staff

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False

    def get_urls(self):
        # Solo la página del reporte: sin ficha individual por búsqueda
        info = self.opts.app_label, self.opts.model_name
        return [path('', self.admin_site.admin_view(self.changelist_view), name='%s_%s_changelist' % info)]

    def changelist_view(self, request, extra_context=None):
        desde, hasta = rango_por_defecto()
        filas = mas_buscados(desde, hasta)
        context = {
            **self.admin_site.each_context(request),
            'opts': self.opts,
            'title': 'Medicamentos más buscados',
            'filas': filas,
            'desde': timezone.localtime(desde),
            'hasta': timezone.localtime(hasta),
            'dias': DIAS_POR_DEFECTO,
            'maximo_filas': MAXIMO_FILAS,
            **(extra_context or {}),
        }
        return TemplateResponse(request, 'admin/reportes/busqueda/mas_buscados.html', context)
