"""
Auditoría de UI (A-R1): el formulario Desde/Hasta compartido (rango_fechas.html) muestra el periodo
que se está viendo. Sin fechas, los campos traen los días de los últimos 30 días y no aparece «Limpiar».
"""
from datetime import timedelta

from django.contrib.auth.models import User
from django.contrib.messages.storage.fallback import FallbackStorage
from django.test import RequestFactory, SimpleTestCase
from django.urls import reverse
from django.utils import timezone

from boticas.models import Resena, Reserva
from reportes.periodo import periodo_de_la_peticion
from reportes.tests.base import TABLAS_DEL_PANEL, ConTablas

# Las tres páginas que incluyen admin/reportes/rango_fechas.html
URLS = {
    'panel': reverse('admin:index'),
    'más buscados': reverse('admin:reportes_busqueda_changelist'),
    'ranking': reverse('admin:reportes_ranking'),
}


def dias_por_defecto():
    hoy = timezone.localdate()
    return f'{hoy - timedelta(days=30):%Y-%m-%d}', f'{hoy:%Y-%m-%d}'


class PeriodoDeLaPeticionTests(SimpleTestCase):
    def periodo(self, **parametros):
        request = RequestFactory().get('/', parametros)
        # messages.error necesita un almacén de mensajes en la petición
        request.session = {}
        request._messages = FallbackStorage(request)
        return periodo_de_la_peticion(request)

    def test_sin_fechas_los_campos_traen_los_ultimos_30_dias(self):
        periodo = self.periodo()

        self.assertEqual((periodo['valor_desde'], periodo['valor_hasta']), dias_por_defecto())
        # Lo escrito sigue vacío: así no aparece «Limpiar» y los enlaces no fijan fechas
        self.assertEqual((periodo['rango_desde'], periodo['rango_hasta'], periodo['sufijo_url']), ('', '', ''))

    def test_con_fechas_los_campos_traen_esas_fechas(self):
        periodo = self.periodo(desde='2026-10-01', hasta='2026-10-10')

        self.assertEqual((periodo['valor_desde'], periodo['valor_hasta']), ('2026-10-01', '2026-10-10'))

    def test_dd_mm_aaaa_se_muestra_como_aaaa_mm_dd(self):
        # El <input type="date"> solo entiende aaaa-mm-dd
        periodo = self.periodo(desde='01/10/2026')

        self.assertEqual((periodo['valor_desde'], periodo['valor_hasta']), ('2026-10-01', ''))

    def test_con_un_solo_lado_el_otro_queda_vacio(self):
        # Un lado vacío significa "sin límite": no se rellena con el rango por defecto
        periodo = self.periodo(hasta='2026-10-05')

        self.assertEqual((periodo['valor_desde'], periodo['valor_hasta']), ('', '2026-10-05'))

    def test_con_error_los_campos_traen_el_rango_que_se_muestra(self):
        periodo = self.periodo(desde='2026-10-28', hasta='2026-10-01')

        self.assertEqual((periodo['valor_desde'], periodo['valor_hasta']), dias_por_defecto())
        self.assertEqual(periodo['rango_desde'], '2026-10-28')


class FormularioEnLasPaginasTests(ConTablas):
    tablas = (*TABLAS_DEL_PANEL, Reserva, Resena)

    @classmethod
    def setUpTestData(cls):
        cls.staff = User.objects.create_user('revisora', password='clave-de-prueba', is_staff=True)

    def setUp(self):
        self.client.force_login(self.staff)

    def test_sin_fechas_muestra_los_dias_y_no_limpiar(self):
        desde, hasta = dias_por_defecto()
        for nombre, url in URLS.items():
            with self.subTest(nombre):
                respuesta = self.client.get(url)

                self.assertContains(respuesta, f'<input type="date" id="rango-desde" name="desde" value="{desde}">',
                                    html=True)
                self.assertContains(respuesta, f'<input type="date" id="rango-hasta" name="hasta" value="{hasta}">',
                                    html=True)
                self.assertContains(respuesta, 'Últimos 30 días')
                self.assertNotContains(respuesta, 'id="rango-limpiar"')

    def test_con_fechas_las_muestra_y_limpiar_vuelve_sin_fechas(self):
        for nombre, url in URLS.items():
            with self.subTest(nombre):
                respuesta = self.client.get(url, {'desde': '2026-10-01', 'hasta': '2026-10-10'})

                self.assertContains(respuesta, 'value="2026-10-01"')
                self.assertContains(respuesta, 'value="2026-10-10"')
                # «Limpiar» es ?: la misma página sin desde/hasta, es decir, los últimos 30 días
                self.assertContains(respuesta, '<a href="?" id="rango-limpiar">Limpiar</a>', html=True)

    def test_con_error_muestra_los_dias_del_rango_por_defecto_y_limpiar(self):
        desde, hasta = dias_por_defecto()
        for nombre, url in URLS.items():
            with self.subTest(nombre):
                respuesta = self.client.get(url, {'desde': '2026-10-28', 'hasta': '2026-10-01'})

                self.assertContains(respuesta, f'value="{desde}"')
                self.assertContains(respuesta, f'value="{hasta}"')
                self.assertContains(respuesta, 'id="rango-limpiar"')
