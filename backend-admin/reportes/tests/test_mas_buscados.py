"""
H62: pruebas del reporte "Medicamentos más buscados".

La tabla busqueda es managed = False, así que Django no la crea en la base de pruebas: se crea
aquí antes de abrir la transacción de TestCase y se borra al terminar.
"""
from datetime import timedelta

from django.contrib import admin
from django.contrib.auth.models import User
from django.db import connection
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone

from reportes.consultas import MAXIMO_FILAS, mas_buscados, rango_por_defecto
from reportes.models import Busqueda

URL = reverse('admin:reportes_busqueda_changelist')


def buscar(texto, tipo='nombre', resultados=1, hace=timedelta(days=1)):
    return Busqueda.objects.create(texto=texto, tipo=tipo, resultados=resultados, fecha=timezone.now() - hace)


class ConTablaBusqueda(TestCase):
    @classmethod
    def setUpClass(cls):
        with connection.schema_editor() as editor:
            editor.create_model(Busqueda)
        super().setUpClass()

    @classmethod
    def tearDownClass(cls):
        super().tearDownClass()
        with connection.schema_editor() as editor:
            editor.delete_model(Busqueda)


class ConsultaAgregadaTests(ConTablaBusqueda):
    def test_agrupa_por_texto_y_tipo_con_conteos(self):
        buscar('paracetamol', resultados=4)
        buscar('Paracetamol', resultados=0)
        buscar('paracetamol', resultados=2)
        buscar('paracetamol', tipo='principio', resultados=10)

        filas = mas_buscados(*rango_por_defecto())

        self.assertEqual(len(filas), 2)
        self.assertEqual(filas[0]['texto_normalizado'], 'paracetamol')
        self.assertEqual(filas[0]['tipo'], 'nombre')
        self.assertEqual(filas[0]['busquedas'], 3)
        self.assertEqual(filas[0]['promedio_resultados'], 2)
        self.assertEqual(filas[0]['sin_resultados'], 1)
        self.assertEqual((filas[1]['tipo'], filas[1]['busquedas'], filas[1]['sin_resultados']), ('principio', 1, 0))

    def test_ordena_de_mayor_a_menor(self):
        for _ in range(3):
            buscar('ibuprofeno')
        buscar('amoxicilina')
        for _ in range(2):
            buscar('nexium')

        filas = mas_buscados(*rango_por_defecto())

        self.assertEqual([f['texto_normalizado'] for f in filas], ['ibuprofeno', 'nexium', 'amoxicilina'])
        self.assertEqual([f['busquedas'] for f in filas], [3, 2, 1])

    def test_empate_pone_primero_el_que_mas_veces_no_tuvo_resultados(self):
        buscar('aspirina', resultados=3)
        buscar('zinc', resultados=0)

        filas = mas_buscados(*rango_por_defecto())

        self.assertEqual([f['texto_normalizado'] for f in filas], ['zinc', 'aspirina'])

    def test_maximo_20_filas(self):
        for i in range(MAXIMO_FILAS + 5):
            buscar(f'medicamento {i:02d}')

        self.assertEqual(len(mas_buscados(*rango_por_defecto())), 20)


class RangoPorDefectoTests(ConTablaBusqueda):
    def test_son_los_ultimos_30_dias(self):
        ahora = timezone.now()
        desde, hasta = rango_por_defecto(ahora)
        self.assertEqual(hasta, ahora)
        self.assertEqual(hasta - desde, timedelta(days=30))

    def test_deja_fuera_las_busquedas_de_hace_mas_de_30_dias(self):
        buscar('reciente', hace=timedelta(days=29))
        buscar('antigua', hace=timedelta(days=31))

        filas = mas_buscados(*rango_por_defecto())

        self.assertEqual([f['texto_normalizado'] for f in filas], ['reciente'])


class AccesoTests(ConTablaBusqueda):
    @classmethod
    def setUpTestData(cls):
        cls.staff = User.objects.create_user('revisora', password='clave-de-prueba', is_staff=True)
        cls.no_staff = User.objects.create_user('cliente', password='clave-de-prueba')

    def test_staff_sin_permisos_asignados_ve_el_reporte(self):
        buscar('paracetamol', resultados=0)
        self.client.force_login(self.staff)

        respuesta = self.client.get(URL)

        self.assertEqual(respuesta.status_code, 200)
        self.assertTemplateUsed(respuesta, 'admin/reportes/busqueda/mas_buscados.html')
        self.assertContains(respuesta, 'Medicamentos más buscados')
        self.assertContains(respuesta, 'paracetamol')

    def test_enlazado_desde_el_indice_del_admin(self):
        self.client.force_login(self.staff)

        respuesta = self.client.get(reverse('admin:index'))

        self.assertContains(respuesta, f'href="{URL}"')

    def test_usuario_que_no_es_staff_va_al_login(self):
        self.client.force_login(self.no_staff)

        respuesta = self.client.get(URL)

        self.assertEqual(respuesta.status_code, 302)
        self.assertIn(reverse('admin:login'), respuesta['Location'])

    def test_anonimo_va_al_login(self):
        respuesta = self.client.get(URL)

        self.assertEqual(respuesta.status_code, 302)
        self.assertIn(reverse('admin:login'), respuesta['Location'])

    def test_no_se_puede_crear_ni_editar(self):
        modelo_admin = admin.site._registry[Busqueda]
        peticion = type('Peticion', (), {'user': self.staff})()
        self.assertFalse(modelo_admin.has_add_permission(peticion))
        self.assertFalse(modelo_admin.has_change_permission(peticion))
        self.assertFalse(modelo_admin.has_delete_permission(peticion))

