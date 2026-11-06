"""H64: pruebas del panel principal de la portada del admin (/admin/)."""
from datetime import datetime, timedelta
from decimal import Decimal
from itertools import count

from django.contrib import admin
from django.contrib.auth.models import User
from django.urls import reverse
from django.utils import timezone

from boticas.models import Botica, Producto, Usuario
from catalogo.models import ConsultaComparacion, Medicamento, PrincipioActivo
from reportes.consultas import rango_por_defecto
from reportes.models import Busqueda
from reportes.panel import indicadores
from reportes.sitio import SitioAdmin
from reportes.tests.base import ConTablas

URL = reverse('admin:index')
_numero = count(1)


def en_lima(*partes):
    """Fecha y hora de Lima (zona del proyecto), como las ve el administrador."""
    return timezone.make_aware(datetime(*partes))


def crear_usuario(rol=Usuario.Rol.COMPRADOR, activo=True):
    n = next(_numero)
    ahora = timezone.now()
    return Usuario.objects.create(nombre=f'Usuario {n}', correo=f'u{n}@prueba.pe', password_hash='x', rol=rol,
                                  activo=activo, creado_en=ahora, actualizado_en=ahora)


def crear_botica(estado=Botica.Estado.APROBADO, nombre=None):
    n = next(_numero)
    ahora = timezone.now()
    return Botica.objects.create(usuario=crear_usuario(Usuario.Rol.DUENO_BOTICA), nombre_comercial=nombre or f'Botica {n}',
                                 ruc=f'{20000000000 + n}', razon_social=f'Razón {n}', direccion='Av. 1',
                                 distrito='Lima', estado=estado, creado_en=ahora, actualizado_en=ahora)


def crear_producto(botica, nombre='Panadol', presentacion='Caja x 10', activo=True):
    ahora = timezone.now()
    return Producto.objects.create(botica=botica, nombre_comercial=nombre, principio_activo='Paracetamol',
                                   presentacion=presentacion, precio=Decimal('5.50'), stock=10, activo=activo,
                                   creado_en=ahora, actualizado_en=ahora)


def crear_medicamento(oculto=False):
    principio, _ = PrincipioActivo.objects.get_or_create(nombre='Paracetamol')
    return Medicamento.objects.create(nombre_comercial=f'Medicamento {next(_numero)}', presentacion='Caja',
                                      principio_activo=principio, oculto=oculto)


def comparar(producto, hace=timedelta(days=1), fecha=None):
    return ConsultaComparacion.objects.create(producto=producto, fecha=fecha or timezone.now() - hace)


def buscar(texto='paracetamol', hace=timedelta(days=1), fecha=None):
    return Busqueda.objects.create(texto=texto, tipo='nombre', resultados=1, fecha=fecha or timezone.now() - hace)


class IndicadoresTests(ConTablas):
    def test_compradores_activos_no_cuenta_inactivos_ni_duenos(self):
        crear_usuario()
        crear_usuario()
        crear_usuario(activo=False)
        crear_usuario(Usuario.Rol.DUENO_BOTICA)

        self.assertEqual(indicadores(*rango_por_defecto())['compradores_activos'], 2)

    def test_duenos_activos_no_cuenta_inactivos_ni_compradores(self):
        crear_usuario(Usuario.Rol.DUENO_BOTICA)
        crear_usuario(Usuario.Rol.DUENO_BOTICA, activo=False)
        crear_usuario()

        self.assertEqual(indicadores(*rango_por_defecto())['duenos_activos'], 1)

    def test_boticas_por_estado_con_cero_si_no_hay(self):
        crear_botica(Botica.Estado.PENDIENTE)
        crear_botica(Botica.Estado.PENDIENTE)
        crear_botica(Botica.Estado.APROBADO)

        datos = indicadores(*rango_por_defecto())

        self.assertEqual((datos['boticas_pendientes'], datos['boticas_aprobadas'], datos['boticas_rechazadas']),
                         (2, 1, 0))

    def test_productos_activos(self):
        botica = crear_botica()
        crear_producto(botica)
        crear_producto(botica, 'Aspirina')
        crear_producto(botica, 'Nexium', activo=False)

        self.assertEqual(indicadores(*rango_por_defecto())['productos_activos'], 2)

    def test_medicamentos_del_catalogo_incluye_los_ocultos(self):
        crear_medicamento()
        crear_medicamento()
        crear_medicamento(oculto=True)

        datos = indicadores(*rango_por_defecto())

        self.assertEqual((datos['medicamentos'], datos['medicamentos_ocultos']), (3, 1))

    def test_busquedas_y_comparaciones_de_los_ultimos_30_dias(self):
        producto = crear_producto(crear_botica())
        buscar(hace=timedelta(days=29))
        buscar(hace=timedelta(days=31))
        comparar(producto, hace=timedelta(days=2))
        comparar(producto, hace=timedelta(days=3))
        comparar(producto, hace=timedelta(days=45))

        datos = indicadores(*rango_por_defecto())

        self.assertEqual((datos['busquedas'], datos['comparaciones']), (1, 2))

    def test_el_periodo_no_afecta_a_los_totales_actuales(self):
        # Rango en el que no hubo nada: los totales siguen siendo los de hoy
        crear_producto(crear_botica(Botica.Estado.PENDIENTE))
        crear_medicamento()
        buscar()

        datos = indicadores(en_lima(2020, 1, 1), en_lima(2020, 2, 1))

        self.assertEqual((datos['busquedas'], datos['comparaciones']), (0, 0))
        self.assertEqual((datos['boticas_pendientes'], datos['productos_activos'], datos['medicamentos'],
                          datos['duenos_activos']), (1, 1, 1, 1))


class PanelEnLaPortadaTests(ConTablas):
    @classmethod
    def setUpTestData(cls):
        cls.staff = User.objects.create_user('revisora', password='clave-de-prueba', is_staff=True)
        cls.no_staff = User.objects.create_user('cliente', password='clave-de-prueba')
        cls.superusuario = User.objects.create_superuser('admin', password='clave-de-prueba')

    def test_el_admin_usa_el_sitio_con_panel(self):
        self.assertIsInstance(admin.site, SitioAdmin)

    def test_staff_ve_los_indicadores_encima_de_las_apps(self):
        crear_usuario()
        crear_botica(Botica.Estado.PENDIENTE)
        self.client.force_login(self.staff)

        respuesta = self.client.get(URL)

        self.assertEqual(respuesta.status_code, 200)
        self.assertTemplateUsed(respuesta, 'admin/reportes/panel/index.html')
        contenido = respuesta.content.decode()
        self.assertLess(contenido.index('id="panel-principal"'), contenido.index('id="content-main"'))
        self.assertEqual(respuesta.context['panel']['indicadores']['compradores_activos'], 1)
        self.assertEqual(respuesta.context['panel']['indicadores']['boticas_pendientes'], 1)
        self.assertContains(respuesta, 'Dueños de botica activos')
        # La lista de apps sigue debajo
        self.assertContains(respuesta, reverse('admin:boticas_botica_changelist'))

    def test_pendientes_enlazan_a_la_lista_filtrada(self):
        crear_botica(Botica.Estado.PENDIENTE, 'Botica Espera')
        crear_botica(Botica.Estado.APROBADO, 'Botica Lista')
        self.client.force_login(self.superusuario)

        respuesta = self.client.get(URL)

        url = reverse('admin:boticas_botica_changelist') + '?estado__exact=PENDIENTE'
        self.assertContains(respuesta, f'href="{url}"')
        # El enlace abre la lista de boticas filtrada por ese estado
        lista = self.client.get(url)
        self.assertEqual(lista.status_code, 200)
        self.assertContains(lista, 'Botica Espera')
        self.assertNotContains(lista, 'Botica Lista')

    def test_sin_fechas_usa_los_ultimos_30_dias(self):
        buscar(hace=timedelta(days=1))
        buscar(hace=timedelta(days=31))
        self.client.force_login(self.staff)

        respuesta = self.client.get(URL)

        self.assertEqual(respuesta.context['panel']['indicadores']['busquedas'], 1)
        self.assertContains(respuesta, 'Últimos 30 días')
        self.assertNotContains(respuesta, 'id="rango-limpiar"')

    def test_con_fechas_cuenta_solo_ese_rango(self):
        producto = crear_producto(crear_botica())
        buscar(fecha=en_lima(2026, 9, 30, 23, 59))
        buscar(fecha=en_lima(2026, 10, 1, 0, 0))
        buscar(fecha=en_lima(2026, 10, 10, 23, 59, 59))
        comparar(producto, fecha=en_lima(2026, 10, 5, 12, 0))
        comparar(producto, fecha=en_lima(2026, 10, 11, 0, 0))
        self.client.force_login(self.staff)

        respuesta = self.client.get(URL, {'desde': '2026-10-01', 'hasta': '2026-10-10'})

        datos = respuesta.context['panel']['indicadores']
        self.assertEqual((datos['busquedas'], datos['comparaciones']), (2, 1))
        self.assertContains(respuesta, 'Del 01/10/2026 al 10/10/2026')
        self.assertContains(respuesta, 'value="2026-10-01"')
        self.assertContains(respuesta, 'id="rango-limpiar"')

    def test_fecha_invalida_avisa_y_usa_los_ultimos_30_dias(self):
        buscar(hace=timedelta(days=1))
        buscar(hace=timedelta(days=60))
        self.client.force_login(self.staff)

        respuesta = self.client.get(URL, {'desde': '2026-10-28', 'hasta': '2026-10-01'})

        self.assertContains(respuesta, 'La fecha «Desde» (28/10/2026) es posterior a «Hasta» (01/10/2026)')
        self.assertEqual(respuesta.context['panel']['indicadores']['busquedas'], 1)
        self.assertContains(respuesta, 'Últimos 30 días')

    def test_usuario_que_no_es_staff_no_ve_el_panel(self):
        self.client.force_login(self.no_staff)

        respuesta = self.client.get(URL)

        self.assertEqual(respuesta.status_code, 302)
        self.assertIn(reverse('admin:login'), respuesta['Location'])

    def test_anonimo_no_ve_el_panel(self):
        respuesta = self.client.get(URL)

        self.assertEqual(respuesta.status_code, 302)
        self.assertIn(reverse('admin:login'), respuesta['Location'])
