"""
S4-08: no se aprueba una botica sin licencia de funcionamiento: ni con el botón Aprobar, ni con el select "estado"
de la ficha, ni con la acción masiva. El mensaje explica por qué y qué hacer, igual que la regla de A5.
"""
from django.core import mail

from boticas import revision
from boticas.models import Botica, BoticaRevision
from boticas.tests import test_revision
from boticas.tests.test_revision import LISTA, ConAdmin, crear_botica, detalle, url_accion


def sin_licencia(nombre='Botica Sin Papeles', licencia_url=None):
    botica = crear_botica(nombre=nombre, licencia=False)
    if licencia_url is not None:
        Botica.objects.filter(pk=botica.pk).update(licencia_url=licencia_url)
        botica.licencia_url = licencia_url
    return botica


MENSAJE = ('No se puede aprobar: la botica Botica Sin Papeles no tiene la licencia de funcionamiento cargada. '
           'El dueño debe subirla desde la web, en «Mi botica».')


class SinLicenciaNoSeApruebaTests(ConAdmin):
    datos_de_la_ficha = test_revision.MismaLogicaEnLosTresLugaresTests.datos_de_la_ficha

    def sin_cambios(self, botica):
        self.assertEqual(self.estado(botica), (Botica.Estado.PENDIENTE, None))
        self.assertFalse(BoticaRevision.objects.exists())
        self.assertEqual(mail.outbox, [])

    def test_boton_aprobar_ni_pide_confirmar(self):
        botica = sin_licencia()

        respuesta = self.client.get(url_accion(botica, 'aprobar'), follow=True)

        self.assertRedirects(respuesta, detalle(botica))
        self.assertIn(MENSAJE, self.mensajes(respuesta))
        self.sin_cambios(botica)

    def test_post_directo_al_boton_tampoco_aprueba(self):
        botica = sin_licencia()

        respuesta = self.post(url_accion(botica, 'aprobar'))

        self.assertIn(MENSAJE, self.mensajes(respuesta))
        self.sin_cambios(botica)

    def test_select_estado_aprobado_sin_licencia_no_guarda(self):
        botica = sin_licencia()

        respuesta = self.post(detalle(botica), self.datos_de_la_ficha(botica, estado='APROBADO'))

        self.assertContains(respuesta, MENSAJE)
        self.sin_cambios(botica)

    def test_accion_masiva_no_aprueba_ninguna_si_una_no_tiene_licencia(self):
        con_licencia, sin = crear_botica(), sin_licencia()

        respuesta = self.post(LISTA, {'action': 'aprobar_boticas', '_selected_action': [con_licencia.pk, sin.pk]})

        self.assertIn(f'No se aprobó ninguna botica. {MENSAJE}', self.mensajes(respuesta))
        self.assertEqual(self.estado(con_licencia), (Botica.Estado.PENDIENTE, None))
        self.sin_cambios(sin)

    def test_una_rechazada_sin_licencia_no_bloquea_la_accion_masiva(self):
        # Solo cuentan las que se van a aprobar (las PENDIENTE): la rechazada se omite
        pendiente = crear_botica()
        rechazada = crear_botica(Botica.Estado.RECHAZADO, licencia=False)

        respuesta = self.post(LISTA, {'action': 'aprobar_boticas', '_selected_action': [pendiente.pk, rechazada.pk]})

        self.assertIn('1 botica(s) aprobada(s).', self.mensajes(respuesta))
        self.assertEqual(self.estado(pendiente), (Botica.Estado.APROBADO, None))
        self.assertEqual(self.estado(rechazada)[0], Botica.Estado.RECHAZADO)

    def test_varias_sin_licencia_se_nombran_todas(self):
        primera, segunda = sin_licencia('Botica Uno'), sin_licencia('Botica Dos')

        with self.assertRaises(revision.SinLicencia) as error:
            revision.revisar([primera.pk, segunda.pk], Botica.Estado.APROBADO, 'guillermo')

        self.assertIn('las boticas ', str(error.exception))
        self.assertIn('Botica Uno', str(error.exception))
        self.assertIn('Botica Dos', str(error.exception))
        self.assertIn('no tienen la licencia de funcionamiento cargada', str(error.exception))

    def test_una_ruta_en_blanco_cuenta_como_sin_licencia(self):
        botica = sin_licencia(licencia_url='   ')

        with self.assertRaisesMessage(revision.SinLicencia, MENSAJE):
            revision.revisar([botica.pk], Botica.Estado.APROBADO, 'guillermo', desde=revision.PUEDE_APROBAR)
        self.sin_cambios(botica)

    def test_sin_licencia_si_se_puede_rechazar(self):
        botica = sin_licencia()

        cambiadas = revision.revisar([botica.pk], Botica.Estado.RECHAZADO, 'guillermo',
                                     motivo='Falta la licencia de funcionamiento')

        self.assertEqual(len(cambiadas), 1)
        self.assertEqual(self.estado(botica), (Botica.Estado.RECHAZADO, 'Falta la licencia de funcionamiento'))

    def test_con_licencia_se_aprueba_como_siempre(self):
        botica = crear_botica()

        self.post(url_accion(botica, 'aprobar'))

        self.assertEqual(self.estado(botica), (Botica.Estado.APROBADO, None))
        self.assertEqual(len(mail.outbox), 1)
