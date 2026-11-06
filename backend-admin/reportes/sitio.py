"""
H64: sitio del admin con el panel principal en la portada (/admin/).

Reemplaza a admin.site mediante config.apps.BotiComparaAdminConfig (default_site), así que todos
los @admin.register del proyecto siguen funcionando igual.
"""
from django.contrib import admin
from django.urls import path, reverse

from boticas.models import Botica

from .consultas import mas_buscados
from .exportar import libro_panel, respuesta_excel
from .panel import TOP_DEL_PANEL, indicadores, mas_comparados
from .periodo import periodo_de_la_peticion


class SitioAdmin(admin.AdminSite):
    index_template = 'admin/reportes/panel/index.html'

    def index(self, request, extra_context=None):
        extra_context = extra_context or {}
        # La portada ya exige staff (admin_view); se vuelve a comprobar por si cambia esa regla
        if request.user.is_active and request.user.is_staff:
            periodo = periodo_de_la_peticion(request)
            desde, hasta = periodo['desde'], periodo['hasta']
            extra_context['panel'] = {
                **periodo,
                'indicadores': indicadores(desde, hasta),
                'mas_buscados': mas_buscados(desde, hasta, limite=TOP_DEL_PANEL),
                'mas_comparados': mas_comparados(desde, hasta),
                'top': TOP_DEL_PANEL,
                'url_pendientes': (reverse('admin:boticas_botica_changelist', current_app=self.name)
                                   + f'?estado__exact={Botica.Estado.PENDIENTE}'),
                # El reporte completo y el Excel se abren con las mismas fechas que tiene el panel
                'url_reporte_buscados': (reverse('admin:reportes_busqueda_changelist', current_app=self.name)
                                         + periodo['sufijo_url']),
                'url_exportar': reverse('admin:panel_exportar', current_app=self.name) + periodo['sufijo_url'],
            }
        return super().index(request, extra_context)

    def get_urls(self):
        # Antes que las del admin: su última ruta atrapa cualquier URL que no conozca.
        # admin_view exige usuario staff activo; si no, manda al login.
        return [
            path('panel/exportar/', self.admin_view(self.exportar_panel), name='panel_exportar'),
            *super().get_urls(),
        ]

    def exportar_panel(self, request):
        """H65: hojas "Indicadores" y "Más comparados" (todas las filas del periodo, no solo 5)."""
        periodo = periodo_de_la_peticion(request, avisar=False)
        desde, hasta = periodo['desde'], periodo['hasta']
        libro = libro_panel(indicadores(desde, hasta), mas_comparados(desde, hasta, limite=None), periodo['rango'])
        return respuesta_excel(libro, 'panel')
