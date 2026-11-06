from django.contrib import admin
from django.template.response import TemplateResponse
from django.urls import path

from .consultas import MAXIMO_FILAS, mas_buscados
from .models import Busqueda
from .periodo import periodo_de_la_peticion


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
        # H63: periodo del formulario Desde/Hasta; con error, los últimos 30 días
        periodo = periodo_de_la_peticion(request)
        context = {
            **self.admin_site.each_context(request),
            **periodo,
            'opts': self.opts,
            'title': 'Medicamentos más buscados',
            'filas': mas_buscados(periodo['desde'], periodo['hasta']),
            'maximo_filas': MAXIMO_FILAS,
            **(extra_context or {}),
        }
        return TemplateResponse(request, 'admin/reportes/busqueda/mas_buscados.html', context)
