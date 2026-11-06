"""
H64: sitio del admin con el panel principal en la portada (/admin/).

Reemplaza a admin.site mediante config.apps.BotiComparaAdminConfig (default_site), así que todos
los @admin.register del proyecto siguen funcionando igual.
"""
from django.contrib import admin
from django.urls import reverse

from boticas.models import Botica

from .panel import indicadores
from .periodo import periodo_de_la_peticion


class SitioAdmin(admin.AdminSite):
    index_template = 'admin/reportes/panel/index.html'

    def index(self, request, extra_context=None):
        extra_context = extra_context or {}
        # La portada ya exige staff (admin_view); se vuelve a comprobar por si cambia esa regla
        if request.user.is_active and request.user.is_staff:
            periodo = periodo_de_la_peticion(request)
            extra_context['panel'] = {
                **periodo,
                'indicadores': indicadores(periodo['desde'], periodo['hasta']),
                'url_pendientes': (reverse('admin:boticas_botica_changelist', current_app=self.name)
                                   + f'?estado__exact={Botica.Estado.PENDIENTE}'),
            }
        return super().index(request, extra_context)
