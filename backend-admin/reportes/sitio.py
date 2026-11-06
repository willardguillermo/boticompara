"""
H64: sitio del admin con el panel principal en la portada (/admin/).

Reemplaza a admin.site mediante config.apps.BotiComparaAdminConfig (default_site), así que todos
los @admin.register del proyecto siguen funcionando igual.
"""
from django.contrib import admin
from django.urls import reverse
from django.utils.http import urlencode

from boticas.models import Botica

from .consultas import mas_buscados
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
            # El reporte completo se abre con las mismas fechas que tiene el panel
            fechas = {campo: periodo[f'rango_{campo}'] for campo in ('desde', 'hasta') if periodo[f'rango_{campo}']}
            url_reporte = reverse('admin:reportes_busqueda_changelist', current_app=self.name)
            extra_context['panel'] = {
                **periodo,
                'indicadores': indicadores(desde, hasta),
                'mas_buscados': mas_buscados(desde, hasta, limite=TOP_DEL_PANEL),
                'mas_comparados': mas_comparados(desde, hasta),
                'top': TOP_DEL_PANEL,
                'url_pendientes': (reverse('admin:boticas_botica_changelist', current_app=self.name)
                                   + f'?estado__exact={Botica.Estado.PENDIENTE}'),
                'url_reporte_buscados': f'{url_reporte}?{urlencode(fechas)}' if fechas else url_reporte,
            }
        return super().index(request, extra_context)
