"""Auditoría de UI (A-U1, A-U2, A-B2): estados con la misma etiqueta de color en todo el admin."""
from django.test import SimpleTestCase
from django.urls import reverse

from boticas import usuarios
from boticas.admin import COLORES_ACCION
from boticas.etiquetas import NARANJA, ROJO, VERDE, etiqueta
from boticas.models import Botica, UsuarioAccion
from boticas.tests.test_revision import ConAdmin, crear_botica
from boticas.tests.test_usuarios import crear_usuario
from catalogo import vigencia
from catalogo.admin import COLORES_VIGENCIA


def contraste_con_blanco(color):
    def canal(hexa):
        c = int(hexa, 16) / 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (canal(color[i:i + 2]) for i in (1, 3, 5))
    return 1.05 / (0.2126 * r + 0.7152 * g + 0.0722 * b + 0.05)


class EtiquetaTests(SimpleTestCase):
    def test_escapa_el_texto(self):
        self.assertIn('&lt;b&gt;', etiqueta('<b>', VERDE))

    def test_un_solo_naranja_con_contraste_aa(self):
        self.assertEqual(COLORES_ACCION[UsuarioAccion.Accion.SUSPENDER], NARANJA)
        self.assertEqual(COLORES_VIGENCIA[vigencia.POR_VENCER], NARANJA)
        for color in (VERDE, NARANJA, ROJO):
            self.assertGreaterEqual(contraste_con_blanco(color), 4.5, color)


class EstadosEnLasListasTests(ConAdmin):

    def test_lista_de_boticas_con_etiqueta_de_estado(self):
        crear_botica(nombre='Botica Espera')
        crear_botica(Botica.Estado.RECHAZADO, nombre='Botica Observada')

        respuesta = self.client.get(reverse('admin:boticas_botica_changelist'))

        self.assertContains(respuesta, etiqueta('Pendiente', NARANJA), html=True)
        self.assertContains(respuesta, etiqueta('Rechazado', ROJO), html=True)
        # Se sigue pudiendo ordenar por estado
        self.assertContains(self.client.get(reverse('admin:boticas_botica_changelist') + '?o=5'), 'Botica Espera')

    def test_lista_de_usuarios_con_etiqueta_de_estado(self):
        crear_usuario()
        crear_usuario(usuarios.BAJA)

        respuesta = self.client.get(reverse('admin:boticas_usuario_changelist'))

        self.assertContains(respuesta, etiqueta('Activo', VERDE), html=True)
        self.assertContains(respuesta, etiqueta('Baja', ROJO), html=True)
