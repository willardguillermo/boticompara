"""H95: pruebas del ranking de las boticas más activas."""
from datetime import timedelta
from decimal import Decimal
from io import BytesIO
from itertools import count

from django.contrib.auth.models import User
from django.urls import reverse
from django.utils import timezone
from openpyxl import load_workbook

from boticas.models import Botica, Producto, Resena, Reserva
from reportes.consultas import rango_por_defecto
from reportes.exportar import TIPO_XLSX
from reportes.ranking import FORMULA, ranking_de_boticas
from reportes.tests.base import TABLAS_DEL_PANEL, ConTablas
from reportes.tests.test_panel import comparar, crear_botica, crear_usuario, en_lima

URL = reverse('admin:reportes_ranking')
URL_EXPORTAR = reverse('admin:reportes_ranking_exportar')
# Fuera de cualquier periodo de las pruebas: así crear un producto no cuenta como "actualizado"
HACE_TIEMPO = en_lima(2020, 1, 1)
_numero = count(1)


def hace(dias):
    return timezone.now() - timedelta(days=dias)


def producto_de(botica, actualizado=HACE_TIEMPO):
    return Producto.objects.create(botica=botica, nombre_comercial=f'Producto {next(_numero)}',
                                   principio_activo='Paracetamol', presentacion='Caja', precio=Decimal('5.50'),
                                   stock=10, creado_en=HACE_TIEMPO, actualizado_en=actualizado)


def entregar(botica, actualizado=None, estado=Reserva.Estado.ENTREGADA):
    actualizado = actualizado or hace(1)
    return Reserva.objects.create(codigo=f'R-{next(_numero)}', usuario=crear_usuario(), producto=producto_de(botica),
                                  cantidad=1, precio_unitario=Decimal('5.50'), estado=estado,
                                  vence_en=actualizado + timedelta(days=1), creado_en=actualizado - timedelta(hours=2),
                                  actualizado_en=actualizado)


def resenar(botica, estrellas=5, respondida=None, oculta=False):
    return Resena.objects.create(botica=botica, usuario=crear_usuario(), estrellas=estrellas, comentario='Bien',
                                 respuesta='Gracias' if respondida else None, respondida_en=respondida,
                                 oculta=oculta, motivo_ocultamiento='Ofensiva' if oculta else None,
                                 creado_en=HACE_TIEMPO, actualizado_en=HACE_TIEMPO)


def ranking():
    return ranking_de_boticas(*rango_por_defecto())


class ConTablasDelRanking(ConTablas):
    tablas = (*TABLAS_DEL_PANEL, Reserva, Resena)


class RankingTests(ConTablasDelRanking):
    def test_puntaje_suma_3_por_pedido_2_por_respuesta_y_1_por_producto(self):
        botica = crear_botica(nombre='Botica Rosa')
        entregar(botica)
        entregar(botica)
        resenar(botica, respondida=hace(2))
        producto_de(botica, actualizado=hace(3))
        producto_de(botica, actualizado=hace(4))
        producto_de(botica, actualizado=hace(5))

        fila, = ranking()

        self.assertEqual((fila['pedidos_entregados'], fila['resenas_respondidas'], fila['productos_actualizados']),
                         (2, 1, 3))
        self.assertEqual(fila['puntaje'], 3 * 2 + 2 * 1 + 1 * 3)
        self.assertEqual((fila['puesto'], fila['botica'], fila['distrito']), (1, 'Botica Rosa', 'Lima'))

    def test_ordena_por_puntaje_de_mayor_a_menor(self):
        poca = crear_botica(nombre='Poca')
        media = crear_botica(nombre='Media')
        mucha = crear_botica(nombre='Mucha')
        producto_de(poca, actualizado=hace(1))                       # 1 punto
        resenar(media, respondida=hace(1))                           # 2 puntos
        entregar(mucha)                                              # 3 puntos

        filas = ranking()

        self.assertEqual([(f['puesto'], f['botica'], f['puntaje']) for f in filas],
                         [(1, 'Mucha', 3), (2, 'Media', 2), (3, 'Poca', 1)])

    def test_empate_por_calificacion_y_luego_por_nombre(self):
        # Las cuatro tienen 3 puntos
        sin_resenas = crear_botica(nombre='Andina')
        regular = crear_botica(nombre='Zeta')
        buena_b = crear_botica(nombre='Botica B')
        buena_a = crear_botica(nombre='botica a')
        for botica in (sin_resenas, regular, buena_b, buena_a):
            entregar(botica)
        resenar(regular, estrellas=3)
        resenar(buena_b, estrellas=5)
        resenar(buena_b, estrellas=4)
        resenar(buena_a, estrellas=4)
        resenar(buena_a, estrellas=5)

        filas = ranking()

        # 4.5, 4.5 (por nombre, sin distinguir mayúsculas), 3.0 y la que no tiene reseñas al final
        self.assertEqual([f['botica'] for f in filas], ['botica a', 'Botica B', 'Zeta', 'Andina'])
        self.assertEqual([f['calificacion'] for f in filas], [4.5, 4.5, 3.0, None])

    def test_solo_cuentan_las_boticas_aprobadas(self):
        aprobada = crear_botica(Botica.Estado.APROBADO, 'Aprobada')
        for estado in (Botica.Estado.PENDIENTE, Botica.Estado.RECHAZADO):
            otra = crear_botica(estado, estado.label)
            entregar(otra)
            resenar(otra, respondida=hace(1))
            producto_de(otra, actualizado=hace(1))
        producto_de(aprobada, actualizado=hace(1))

        self.assertEqual([f['botica'] for f in ranking()], ['Aprobada'])

    def test_solo_cuentan_los_pedidos_entregados(self):
        botica = crear_botica()
        entregar(botica)
        for estado in (Reserva.Estado.PENDIENTE, Reserva.Estado.CONFIRMADA, Reserva.Estado.LISTA,
                       Reserva.Estado.RECHAZADA, Reserva.Estado.CANCELADA, Reserva.Estado.VENCIDA):
            entregar(botica, estado=estado)

        self.assertEqual(ranking()[0]['pedidos_entregados'], 1)

    def test_resenas_sin_responder_no_suman(self):
        botica = crear_botica()
        resenar(botica)
        resenar(botica, respondida=hace(1))

        self.assertEqual(ranking()[0]['resenas_respondidas'], 1)

    def test_por_defecto_solo_los_ultimos_30_dias(self):
        botica = crear_botica()
        entregar(botica, actualizado=hace(29))
        entregar(botica, actualizado=hace(31))
        resenar(botica, respondida=hace(29))
        resenar(botica, respondida=hace(31))
        producto_de(botica, actualizado=hace(29))
        producto_de(botica, actualizado=hace(31))
        comparar(producto_de(botica), hace=timedelta(days=29))
        comparar(producto_de(botica), hace=timedelta(days=31))

        fila, = ranking()

        self.assertEqual((fila['pedidos_entregados'], fila['resenas_respondidas'], fila['productos_actualizados'],
                          fila['veces_comparado']), (1, 1, 1, 1))

    def test_con_fechas_cuenta_solo_ese_rango(self):
        botica = crear_botica()
        entregar(botica, actualizado=en_lima(2026, 9, 30, 23, 59))
        entregar(botica, actualizado=en_lima(2026, 10, 1, 0, 0))
        entregar(botica, actualizado=en_lima(2026, 10, 10, 23, 59, 59))
        entregar(botica, actualizado=en_lima(2026, 10, 11, 0, 0))
        resenar(botica, respondida=en_lima(2026, 10, 5, 12, 0))
        resenar(botica, respondida=en_lima(2026, 10, 11, 0, 0))
        producto_de(botica, actualizado=en_lima(2026, 10, 2, 8, 0))

        fila, = ranking_de_boticas(en_lima(2026, 10, 1), en_lima(2026, 10, 11))

        self.assertEqual((fila['pedidos_entregados'], fila['resenas_respondidas'], fila['productos_actualizados']),
                         (2, 1, 1))

    def test_las_resenas_ocultas_no_cuentan_en_el_promedio(self):
        botica = crear_botica()
        entregar(botica)
        resenar(botica, estrellas=5)
        resenar(botica, estrellas=4)
        resenar(botica, estrellas=1, oculta=True)

        self.assertEqual(ranking()[0]['calificacion'], 4.5)

    def test_solo_resenas_ocultas_es_sin_calificacion(self):
        botica = crear_botica()
        entregar(botica)
        resenar(botica, estrellas=1, oculta=True)

        self.assertIsNone(ranking()[0]['calificacion'])

    def test_veces_comparado_no_suma_al_puntaje(self):
        con_actividad = crear_botica(nombre='Activa')
        solo_comparada = crear_botica(nombre='Solo comparada')
        producto = producto_de(con_actividad, actualizado=hace(1))
        for _ in range(5):
            comparar(producto)
            comparar(producto_de(solo_comparada))

        fila, = ranking()

        self.assertEqual((fila['botica'], fila['puntaje'], fila['veces_comparado']), ('Activa', 1, 5))

    def test_sin_actividad_no_hay_filas(self):
        botica = crear_botica()
        entregar(botica, actualizado=hace(60))
        resenar(botica, estrellas=5)

        self.assertEqual(ranking(), [])


class PaginaDelRankingTests(ConTablasDelRanking):
    @classmethod
    def setUpTestData(cls):
        cls.staff = User.objects.create_user('revisora', password='clave-de-prueba', is_staff=True)
        cls.no_staff = User.objects.create_user('cliente', password='clave-de-prueba')

    def test_staff_ve_la_tabla_y_la_formula(self):
        rosa = crear_botica(nombre='Botica Rosa')
        entregar(rosa)
        resenar(rosa, estrellas=4, respondida=hace(1))
        resenar(rosa, estrellas=5)
        self.client.force_login(self.staff)

        respuesta = self.client.get(URL)

        self.assertEqual(respuesta.status_code, 200)
        self.assertTemplateUsed(respuesta, 'admin/reportes/ranking.html')
        self.assertContains(respuesta, 'Ranking de boticas')
        self.assertContains(respuesta, 'Botica Rosa')
        self.assertContains(respuesta, reverse('admin:boticas_botica_change', args=[rosa.id]))
        self.assertContains(respuesta, '<td class="numero puntaje">5</td>', html=True)
        self.assertContains(respuesta, '<td class="numero">4,5</td>', html=True)
        self.assertContains(respuesta, 'Últimos 30 días')
        self.assertContains(respuesta, 'id="ranking-formula"')
        self.assertContains(respuesta, 'Cada pedido entregado suma 3 puntos')
        self.assertNotContains(respuesta, 'id="ranking-vacio"')

    def test_sin_calificacion_muestra_raya(self):
        entregar(crear_botica())
        self.client.force_login(self.staff)

        respuesta = self.client.get(URL)

        self.assertContains(respuesta, '<td class="numero">—</td>', html=True)

    def test_estado_vacio(self):
        entregar(crear_botica(Botica.Estado.PENDIENTE))
        self.client.force_login(self.staff)

        respuesta = self.client.get(URL)

        self.assertContains(respuesta, 'id="ranking-vacio"')
        self.assertContains(respuesta, 'ninguna botica aprobada tuvo actividad en este periodo')
        self.assertNotContains(respuesta, 'id="result_list"')
        # La fórmula se explica igual
        self.assertContains(respuesta, 'id="ranking-formula"')

    def test_con_fechas_filtra_y_las_conserva_en_el_excel(self):
        botica = crear_botica(nombre='Botica Octubre')
        entregar(botica, actualizado=en_lima(2026, 10, 5, 12, 0))
        self.client.force_login(self.staff)

        respuesta = self.client.get(URL, {'desde': '2026-10-01', 'hasta': '2026-10-10'})

        self.assertContains(respuesta, 'Botica Octubre')
        self.assertContains(respuesta, 'Del 01/10/2026 al 10/10/2026')
        self.assertContains(respuesta, f'href="{URL_EXPORTAR}?desde=2026-10-01&amp;hasta=2026-10-10"')
        self.assertContains(respuesta, 'id="rango-limpiar"')

    def test_fecha_invalida_avisa_y_usa_los_ultimos_30_dias(self):
        self.client.force_login(self.staff)

        respuesta = self.client.get(URL, {'desde': '2026-10-28', 'hasta': '2026-10-01'})

        self.assertContains(respuesta, 'La fecha «Desde» (28/10/2026) es posterior a «Hasta» (01/10/2026)')
        self.assertContains(respuesta, 'Últimos 30 días')

    def test_anonimo_va_al_login(self):
        for url in (URL, URL_EXPORTAR):
            respuesta = self.client.get(url)
            self.assertEqual(respuesta.status_code, 302)
            self.assertIn(reverse('admin:login'), respuesta['Location'])

    def test_usuario_que_no_es_staff_va_al_login(self):
        self.client.force_login(self.no_staff)

        for url in (URL, URL_EXPORTAR):
            respuesta = self.client.get(url)
            self.assertEqual(respuesta.status_code, 302)
            self.assertIn(reverse('admin:login'), respuesta['Location'])

    def test_el_panel_enlaza_al_ranking_con_sus_fechas(self):
        self.client.force_login(self.staff)

        sin_fechas = self.client.get(reverse('admin:index'))
        con_fechas = self.client.get(reverse('admin:index'), {'desde': '2026-10-01', 'hasta': '2026-10-10'})

        self.assertContains(sin_fechas, f'href="{URL}" id="enlace-ranking"')
        self.assertContains(con_fechas, f'href="{URL}?desde=2026-10-01&amp;hasta=2026-10-10" id="enlace-ranking"')

    def test_aparece_en_la_app_reportes(self):
        self.client.force_login(self.staff)

        respuesta = self.client.get(reverse('admin:app_list', args=['reportes']))

        self.assertContains(respuesta, f'<a href="{URL}">Ranking de boticas</a>', html=True)
        self.assertContains(respuesta, 'Medicamentos más buscados')

    def test_exportar_a_excel(self):
        rosa = crear_botica(nombre='Botica Rosa')
        jorge = crear_botica(nombre='Botica Jorge')
        entregar(rosa)
        resenar(rosa, estrellas=4)
        producto_de(jorge, actualizado=hace(1))
        self.client.force_login(self.staff)

        respuesta = self.client.get(URL_EXPORTAR)

        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(respuesta['Content-Type'], TIPO_XLSX)
        self.assertIn('boticompara-ranking-boticas-', respuesta['Content-Disposition'])
        hoja = load_workbook(BytesIO(respuesta.content))['Ranking']
        self.assertIn('Ranking de boticas - Últimos 30 días', hoja['A1'].value)
        filas = [list(fila) for fila in hoja.iter_rows(min_row=2, values_only=True)]
        self.assertEqual(filas[:3], [
            ['Puesto', 'Botica', 'Distrito', 'Pedidos entregados', 'Reseñas respondidas', 'Productos actualizados',
             'Puntaje', 'Calificación promedio', 'Veces comparado'],
            [1, 'Botica Rosa', 'Lima', 1, 0, 0, 3, 4.0, 0],
            [2, 'Botica Jorge', 'Lima', 0, 0, 1, 1, '—', 0],
        ])
        self.assertEqual(filas[-1][0], FORMULA)

    def test_formato_del_excel(self):
        rosa = crear_botica(nombre='Botica Rosa')
        entregar(rosa)
        resenar(rosa, estrellas=5)
        entregar(crear_botica(nombre='Botica Jorge'))
        self.client.force_login(self.staff)

        hoja = load_workbook(BytesIO(self.client.get(URL_EXPORTAR).content))['Ranking']

        # Calificación con 1 decimal (5,0) y la raya alineada a la derecha como los números
        self.assertEqual((hoja['H3'].value, hoja['H3'].number_format), (5, '0.0'))
        self.assertEqual((hoja['H4'].value, hoja['H4'].alignment.horizontal), ('—', 'right'))
        # La fórmula ocupa todas las columnas, con el texto ajustado y alto para más de una línea
        fila_nota = hoja.max_row
        self.assertEqual(hoja.cell(fila_nota, 1).value, FORMULA)
        self.assertIn(f'A{fila_nota}:I{fila_nota}', [str(rango) for rango in hoja.merged_cells.ranges])
        self.assertTrue(hoja.cell(fila_nota, 1).alignment.wrap_text)
        self.assertGreater(hoja.row_dimensions[fila_nota].height, 2 * 15)
