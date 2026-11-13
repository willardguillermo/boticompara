"""
H47: pruebas de las reglas del historial de revisiones.

SimpleTestCase (sin BD): los modelos son managed = False y Django no podría crear sus
tablas en una base de pruebas.
"""
from datetime import date, datetime
from datetime import timezone as dt_timezone

from django.test import SimpleTestCase

from boticas.admin import RevisionInline, UsuarioAccionInline
from boticas.historial import SIN_REVISOR, texto_revisado_por, validar_rango
from boticas.models import Botica, BoticaRevision


class ValidarRangoTests(SimpleTestCase):
    def test_vacio_no_filtra(self):
        self.assertEqual(validar_rango('', ''), (None, None, None))
        self.assertEqual(validar_rango(None, None), (None, None, None))
        self.assertEqual(validar_rango('   ', None), (None, None, None))

    def test_rango_valido(self):
        self.assertEqual(validar_rango('2026-10-01', '2026-10-29'), (date(2026, 10, 1), date(2026, 10, 29), None))

    def test_mismo_dia(self):
        self.assertEqual(validar_rango('2026-10-27', '2026-10-27'), (date(2026, 10, 27), date(2026, 10, 27), None))

    def test_solo_un_lado(self):
        self.assertEqual(validar_rango('2026-10-27', ''), (date(2026, 10, 27), None, None))
        self.assertEqual(validar_rango('', '2026-10-27'), (None, date(2026, 10, 27), None))

    def test_acepta_dd_mm_aaaa(self):
        self.assertEqual(validar_rango('01/10/2026', '29/10/2026'), (date(2026, 10, 1), date(2026, 10, 29), None))

    def test_desde_posterior_a_hasta_es_error_y_no_filtra(self):
        desde, hasta, error = validar_rango('2026-10-28', '2026-10-01')
        self.assertEqual((desde, hasta), (None, None))
        self.assertEqual(error, 'La fecha «Desde» (28/10/2026) es posterior a «Hasta» (01/10/2026): '
                                'no se aplicó el filtro de fechas.')

    def test_fecha_invalida_es_error_y_no_filtra(self):
        for desde, hasta, etiqueta in (('2026-02-31', '', 'Desde'), ('ayer', '', 'Desde'),
                                       ('2026-10-01', '31/02/2026', 'Hasta')):
            with self.subTest(desde=desde, hasta=hasta):
                fecha_desde, fecha_hasta, error = validar_rango(desde, hasta)
                self.assertEqual((fecha_desde, fecha_hasta), (None, None))
                self.assertIn(f'La fecha «{etiqueta}»', error)
                self.assertIn('no es válida', error)


class RevisadoPorTests(SimpleTestCase):
    def test_null_es_carga_inicial(self):
        self.assertEqual(SIN_REVISOR, 'Sin registro (carga inicial)')
        self.assertEqual(texto_revisado_por(None), 'Sin registro (carga inicial)')
        self.assertEqual(texto_revisado_por(''), 'Sin registro (carga inicial)')

    def test_con_administrador(self):
        self.assertEqual(texto_revisado_por('guillermo'), 'guillermo')


class TextoDeLaRevisionTests(SimpleTestCase):
    """H47: el texto de la revisión usa la hora de Lima y el historial en línea no lo muestra."""

    def test_str_en_hora_de_lima(self):
        # 02:35 en UTC son las 21:35 del día anterior en Lima (UTC-5)
        revision = BoticaRevision(botica=Botica(nombre_comercial='Botica Rosa'), estado=BoticaRevision.Estado.APROBADO,
                                  fecha=datetime(2026, 10, 6, 2, 35, tzinfo=dt_timezone.utc))
        self.assertTrue(str(revision).endswith('Aprobado (05/10/2026 21:35)'))

    def test_historiales_en_linea_sin_el_texto_del_objeto(self):
        for inline in (RevisionInline, UsuarioAccionInline):
            with self.subTest(inline.__name__):
                self.assertEqual(inline.template, 'admin/boticas/historial_inline.html')
