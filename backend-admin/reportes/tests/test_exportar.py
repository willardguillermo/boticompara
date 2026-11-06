"""H65: pruebas de la exportación de los reportes a Excel."""
from datetime import date, timedelta
from io import BytesIO

from django.contrib.auth.models import User
from django.test import SimpleTestCase
from django.urls import reverse
from django.utils import timezone
from openpyxl import load_workbook

from boticas.models import Botica
from reportes.consultas import MAXIMO_FILAS, mas_buscados, rango_de_fechas
from reportes.exportar import TIPO_XLSX, libro_mas_buscados, libro_panel, nombre_de_archivo
from reportes.models import Busqueda
from reportes.panel import TOP_DEL_PANEL, indicadores, mas_comparados
from reportes.tests.base import ConTablas
from reportes.tests.test_panel import buscar, comparar, crear_botica, crear_producto, crear_usuario, en_lima

URL_BUSCADOS = reverse('admin:reportes_busqueda_exportar')
URL_PANEL = reverse('admin:panel_exportar')


def abrir(contenido):
    return load_workbook(BytesIO(contenido))


def valores(hoja, desde_fila=1):
    return [list(fila) for fila in hoja.iter_rows(min_row=desde_fila, values_only=True)]


def buscar_con(texto, tipo='nombre', resultados=1, hace=timedelta(days=1)):
    return Busqueda.objects.create(texto=texto, tipo=tipo, resultados=resultados, fecha=timezone.now() - hace)


class LibroMasBuscadosTests(ConTablas):
    def test_titulo_encabezados_en_negrita_y_filas(self):
        buscar_con('paracetamol', resultados=4)
        buscar_con('Paracetamol', resultados=0)
        buscar_con('nexium', tipo='principio', resultados=3)
        desde, hasta, rango = rango_de_fechas(None, None)

        libro = abrir(_guardar(libro_mas_buscados(mas_buscados(desde, hasta, limite=None), rango)))

        self.assertEqual(libro.sheetnames, ['Más buscados'])
        hoja = libro['Más buscados']
        self.assertEqual(hoja['A1'].value, f'Medicamentos más buscados - {rango}')
        self.assertIn('Últimos 30 días', hoja['A1'].value)
        self.assertEqual(valores(hoja, 2), [
            ['#', 'Texto buscado', 'Tipo', 'Búsquedas', 'Promedio de resultados', 'Sin resultados'],
            [1, 'paracetamol', 'Nombre comercial', 2, 2.0, 1],
            [2, 'nexium', 'Principio activo', 1, 3.0, 0],
        ])
        self.assertTrue(all(celda.font.bold for celda in hoja[2]))
        self.assertFalse(hoja['B3'].font.bold)

    def test_ancho_de_columnas_segun_el_texto(self):
        buscar_con('un texto de búsqueda bastante largo')
        desde, hasta, rango = rango_de_fechas(None, None)

        hoja = libro_mas_buscados(mas_buscados(desde, hasta, limite=None), rango).active

        self.assertEqual(hoja.column_dimensions['B'].width, len('un texto de búsqueda bastante largo') + 2)
        self.assertEqual(hoja.column_dimensions['E'].width, len('Promedio de resultados') + 2)

    def test_sin_filas_deja_titulo_y_encabezados(self):
        hoja = libro_mas_buscados([], 'Del 01/01/2025 al 31/01/2025').active

        self.assertEqual(hoja.max_row, 2)
        self.assertEqual(hoja['A1'].value, 'Medicamentos más buscados - Del 01/01/2025 al 31/01/2025')


class LibroPanelTests(ConTablas):
    def test_hojas_indicadores_y_mas_comparados(self):
        crear_usuario()
        crear_botica(Botica.Estado.PENDIENTE)
        producto = crear_producto(crear_botica(nombre='Botica Rosa'), 'Panadol', 'Caja x 100')
        comparar(producto)
        comparar(producto)
        desde, hasta, rango = rango_de_fechas(None, None)

        libro = abrir(_guardar(libro_panel(indicadores(desde, hasta), mas_comparados(desde, hasta, limite=None), rango)))

        self.assertEqual(libro.sheetnames, ['Indicadores', 'Más comparados'])
        hoja = libro['Indicadores']
        self.assertEqual(hoja['A1'].value, f'Panel principal - {rango}')
        filas = valores(hoja, 2)
        self.assertEqual(filas[0], ['Indicador', 'Valor'])
        datos = dict(filas[1:])
        self.assertEqual(len(datos), 10)
        self.assertEqual(datos['Compradores activos'], 1)
        self.assertEqual(datos['Dueños de botica activos'], 2)
        self.assertEqual((datos['Boticas pendientes'], datos['Boticas aprobadas'], datos['Boticas rechazadas']), (1, 1, 0))
        self.assertEqual(datos['Comparaciones del periodo'], 2)
        self.assertEqual(datos['Búsquedas del periodo'], 0)

        hoja = libro['Más comparados']
        self.assertEqual(hoja['A1'].value, f'Productos más comparados - {rango}')
        self.assertEqual(valores(hoja, 2), [
            ['#', 'Nombre comercial', 'Presentación', 'Botica', 'Comparaciones'],
            [1, 'Panadol', 'Caja x 100', 'Botica Rosa', 2],
        ])
        self.assertTrue(all(celda.font.bold for celda in hoja[2]))


class NombreDeArchivoTests(SimpleTestCase):
    def test_formato_con_la_fecha(self):
        self.assertEqual(nombre_de_archivo('panel', date(2026, 11, 5)), 'boticompara-panel-2026-11-05.xlsx')
        self.assertEqual(nombre_de_archivo('mas-buscados', date(2026, 1, 9)),
                         'boticompara-mas-buscados-2026-01-09.xlsx')


class DescargaTests(ConTablas):
    @classmethod
    def setUpTestData(cls):
        cls.staff = User.objects.create_user('revisora', password='clave-de-prueba', is_staff=True)
        cls.no_staff = User.objects.create_user('cliente', password='clave-de-prueba')

    def descargar(self, url, datos=None):
        self.client.force_login(self.staff)
        respuesta = self.client.get(url, datos or {})
        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(respuesta['Content-Type'], TIPO_XLSX)
        return respuesta

    def test_mas_buscados_trae_todas_las_filas_no_solo_20(self):
        for i in range(MAXIMO_FILAS + 5):
            buscar(f'medicamento {i:02d}')

        respuesta = self.descargar(URL_BUSCADOS)

        hoy = timezone.localdate()
        self.assertEqual(respuesta['Content-Disposition'],
                         f'attachment; filename="boticompara-mas-buscados-{hoy:%Y-%m-%d}.xlsx"')
        hoja = abrir(respuesta.content)['Más buscados']
        self.assertEqual(hoja.max_row - 2, MAXIMO_FILAS + 5)

    def test_mas_buscados_respeta_el_rango(self):
        buscar('antes', fecha=en_lima(2026, 9, 30, 23, 59))
        buscar('dentro', fecha=en_lima(2026, 10, 10, 23, 59, 59))
        buscar('despues', fecha=en_lima(2026, 10, 11, 0, 0))

        respuesta = self.descargar(URL_BUSCADOS, {'desde': '2026-10-01', 'hasta': '2026-10-10'})

        hoja = abrir(respuesta.content).active
        self.assertEqual(hoja['A1'].value, 'Medicamentos más buscados - Del 01/10/2026 al 10/10/2026')
        self.assertEqual([fila[1] for fila in valores(hoja, 3)], ['dentro'])

    def test_fecha_invalida_exporta_los_ultimos_30_dias(self):
        buscar('reciente')
        buscar('antigua', hace=timedelta(days=60))

        respuesta = self.descargar(URL_BUSCADOS, {'desde': '2026-02-31'})

        hoja = abrir(respuesta.content).active
        self.assertIn('Últimos 30 días', hoja['A1'].value)
        self.assertEqual([fila[1] for fila in valores(hoja, 3)], ['reciente'])

    def test_panel_trae_todos_los_comparados_no_solo_5(self):
        botica = crear_botica()
        for i in range(TOP_DEL_PANEL + 3):
            comparar(crear_producto(botica, f'Producto {i}'))

        respuesta = self.descargar(URL_PANEL)

        hoy = timezone.localdate()
        self.assertEqual(respuesta['Content-Disposition'],
                         f'attachment; filename="boticompara-panel-{hoy:%Y-%m-%d}.xlsx"')
        libro = abrir(respuesta.content)
        self.assertEqual(libro.sheetnames, ['Indicadores', 'Más comparados'])
        self.assertEqual(libro['Más comparados'].max_row - 2, TOP_DEL_PANEL + 3)

    def test_panel_respeta_el_rango(self):
        producto = crear_producto(crear_botica())
        comparar(producto, fecha=en_lima(2026, 10, 5, 12, 0))
        comparar(producto, fecha=en_lima(2026, 10, 11, 0, 0))
        buscar(fecha=en_lima(2026, 10, 1, 0, 0))

        respuesta = self.descargar(URL_PANEL, {'desde': '2026-10-01', 'hasta': '2026-10-10'})

        libro = abrir(respuesta.content)
        self.assertEqual(libro['Indicadores']['A1'].value, 'Panel principal - Del 01/10/2026 al 10/10/2026')
        datos = dict(valores(libro['Indicadores'], 3))
        self.assertEqual((datos['Búsquedas del periodo'], datos['Comparaciones del periodo']), (1, 1))
        self.assertEqual(valores(libro['Más comparados'], 3)[0][4], 1)

    def test_botones_llevan_el_periodo_que_se_esta_viendo(self):
        self.client.force_login(self.staff)
        fechas = {'desde': '2026-10-01', 'hasta': '2026-10-10'}
        sufijo = '?desde=2026-10-01&amp;hasta=2026-10-10'

        self.assertContains(self.client.get(reverse('admin:reportes_busqueda_changelist'), fechas),
                            f'href="{URL_BUSCADOS}{sufijo}"')
        self.assertContains(self.client.get(reverse('admin:index'), fechas), f'href="{URL_PANEL}{sufijo}"')
        self.assertContains(self.client.get(reverse('admin:index')), f'href="{URL_PANEL}"')

    def test_solo_staff_puede_exportar(self):
        for url in (URL_BUSCADOS, URL_PANEL):
            with self.subTest(url=url, usuario='anónimo'):
                self.client.logout()
                respuesta = self.client.get(url)
                self.assertEqual(respuesta.status_code, 302)
                self.assertIn(reverse('admin:login'), respuesta['Location'])
            with self.subTest(url=url, usuario='no staff'):
                self.client.force_login(self.no_staff)
                respuesta = self.client.get(url)
                self.assertEqual(respuesta.status_code, 302)
                self.assertIn(reverse('admin:login'), respuesta['Location'])


def _guardar(libro):
    contenido = BytesIO()
    libro.save(contenido)
    return contenido.getvalue()
