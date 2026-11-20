"""
H64: sitio del admin con el panel principal en la portada (/admin/).

Reemplaza a admin.site mediante config.apps.BotiComparaAdminConfig (default_site), así que todos
los @admin.register del proyecto siguen funcionando igual.
"""
from django.contrib import admin
from django.template.response import TemplateResponse
from django.urls import path, reverse

from boticas.models import Botica

from .consultas import mas_buscados
from .exportar import libro_panel, libro_ranking, respuesta_excel
from .panel import TOP_DEL_PANEL, indicadores, mas_comparados
from .periodo import periodo_de_la_peticion
from .ranking import FORMULA, ranking_de_boticas

TITULO_RANKING = 'Ranking de boticas'


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
                # H95: el ranking se abre con las mismas fechas que tiene el panel
                'url_ranking': reverse('admin:reportes_ranking', current_app=self.name) + periodo['sufijo_url'],
            }
        return super().index(request, extra_context)

    def get_urls(self):
        # Antes que las del admin: su última ruta atrapa cualquier URL que no conozca.
        # admin_view exige usuario staff activo; si no, manda al login.
        return [
            path('panel/exportar/', self.admin_view(self.exportar_panel), name='panel_exportar'),
            path('reportes/ranking/', self.admin_view(self.ranking), name='reportes_ranking'),
            path('reportes/ranking/exportar/', self.admin_view(self.exportar_ranking),
                 name='reportes_ranking_exportar'),
            *super().get_urls(),
        ]

    def get_app_list(self, request, app_label=None):
        """H95: el ranking no es un modelo; se agrega a mano a la app Reportes (portada, barra lateral)."""
        apps = super().get_app_list(request, app_label)
        for app in apps:
            if app['app_label'] == 'reportes':
                app['models'].append({
                    'name': TITULO_RANKING,
                    'object_name': 'Ranking',
                    'admin_url': reverse('admin:reportes_ranking', current_app=self.name),
                    'add_url': None,
                    'view_only': True,
                    'perms': {'add': False, 'change': False, 'delete': False, 'view': True},
                })
                app['models'].sort(key=lambda modelo: modelo['name'].casefold())
        return apps

    def ranking(self, request):
        """H95: boticas aprobadas ordenadas por puntaje en el periodo del formulario Desde/Hasta."""
        periodo = periodo_de_la_peticion(request)
        context = {
            **self.each_context(request),
            **periodo,
            'title': TITULO_RANKING,
            'filas': ranking_de_boticas(periodo['desde'], periodo['hasta']),
            'formula': FORMULA,
        }
        return TemplateResponse(request, 'admin/reportes/ranking.html', context)

    def exportar_ranking(self, request):
        """H95: el mismo ranking del periodo que se está viendo, en Excel."""
        periodo = periodo_de_la_peticion(request, avisar=False)
        filas = ranking_de_boticas(periodo['desde'], periodo['hasta'])
        return respuesta_excel(libro_ranking(filas, periodo['rango'], FORMULA), 'ranking-boticas')

    def exportar_panel(self, request):
        """H65: hojas "Indicadores" y "Más comparados" (todas las filas del periodo, no solo 5)."""
        periodo = periodo_de_la_peticion(request, avisar=False)
        desde, hasta = periodo['desde'], periodo['hasta']
        libro = libro_panel(indicadores(desde, hasta), mas_comparados(desde, hasta, limite=None), periodo['rango'])
        return respuesta_excel(libro, 'panel')
