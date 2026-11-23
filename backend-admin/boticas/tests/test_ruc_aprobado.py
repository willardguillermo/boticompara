"""
A5: el RUC solo se reserva al aprobar la botica. Varias cuentas pueden tener solicitudes PENDIENTE con el mismo
RUC; al aprobar una, las demás PENDIENTE pasan a RECHAZADO con el motivo "Otra cuenta acreditó la licencia de
este RUC" (con historial y correo). Si otra botica APROBADO ya tiene el RUC, no se deja aprobar y se dice por qué.
"""
from django.core import mail
from django.db import IntegrityError, transaction

from boticas import revision
from boticas.models import Botica, BoticaRevision
from boticas.tests import test_revision
from boticas.tests.test_revision import LISTA, MOTIVO, ConAdmin, crear_botica, detalle, url_accion

RUC = '20601234565'
OTRO_RUC = '20549871233'


def con_ruc(estado=Botica.Estado.PENDIENTE, ruc=RUC):
    botica = crear_botica(estado)
    Botica.objects.filter(pk=botica.pk).update(ruc=ruc)
    botica.ruc = ruc
    return botica


class RucSoloSeReservaAlAprobarTests(ConAdmin):
    # Lo que envía el formulario de la ficha (sin heredar la clase, para no repetir sus pruebas)
    datos_de_la_ficha = test_revision.MismaLogicaEnLosTresLugaresTests.datos_de_la_ficha

    def test_varias_pendientes_pueden_tener_el_mismo_ruc(self):
        primera, segunda = con_ruc(), con_ruc()

        self.assertEqual(Botica.objects.filter(ruc=RUC, estado=Botica.Estado.PENDIENTE).count(), 2)
        self.assertNotEqual(primera.pk, segunda.pk)

    def test_la_base_no_deja_dos_aprobadas_con_el_mismo_ruc(self):
        con_ruc(Botica.Estado.APROBADO)
        otra = con_ruc()

        with self.assertRaises(IntegrityError), transaction.atomic():
            Botica.objects.filter(pk=otra.pk).update(estado=Botica.Estado.APROBADO)

    def test_aprobar_rechaza_las_otras_pendientes_del_mismo_ruc(self):
        elegida, otra, tercera = con_ruc(), con_ruc(), con_ruc()
        rechazada_antes = con_ruc(Botica.Estado.RECHAZADO)
        de_otro_ruc = con_ruc(ruc=OTRO_RUC)

        respuesta = self.post(url_accion(elegida, 'aprobar'))

        self.assertRedirects(respuesta, detalle(elegida))
        self.assertEqual(self.estado(elegida), (Botica.Estado.APROBADO, None))
        for botica in (otra, tercera):
            self.assertEqual(self.estado(botica), (Botica.Estado.RECHAZADO, revision.MOTIVO_RUC_ACREDITADO))
        # Las ya rechazadas y las de otro RUC no se tocan
        self.assertEqual(self.estado(rechazada_antes), (Botica.Estado.RECHAZADO, MOTIVO))
        self.assertEqual(self.estado(de_otro_ruc), (Botica.Estado.PENDIENTE, None))
        # Historial: la aprobación y un rechazo por cada otra pendiente, del mismo administrador
        self.assertEqual(
            set(BoticaRevision.objects.values_list('botica_id', 'estado', 'motivo', 'revisado_por')),
            {(elegida.pk, 'APROBADO', None, 'guillermo'),
             (otra.pk, 'RECHAZADO', revision.MOTIVO_RUC_ACREDITADO, 'guillermo'),
             (tercera.pk, 'RECHAZADO', revision.MOTIVO_RUC_ACREDITADO, 'guillermo')})
        # Correo como en un rechazo normal, con el motivo
        por_correo = {m.to[0]: m for m in mail.outbox}
        self.assertEqual(set(por_correo), {elegida.usuario.correo, otra.usuario.correo, tercera.usuario.correo})
        self.assertIn('fue aprobada', por_correo[elegida.usuario.correo].subject)
        for botica in (otra, tercera):
            correo = por_correo[botica.usuario.correo]
            self.assertIn('fue observada', correo.subject)
            self.assertIn(f'Motivo: {revision.MOTIVO_RUC_ACREDITADO}', correo.body)
        mensajes = self.mensajes(respuesta)
        self.assertIn(f'Se aprobó la botica {elegida.nombre_comercial}.', mensajes[0])
        self.assertTrue(any('Se rechazaron 2 solicitud(es) pendiente(s) con el mismo RUC' in m for m in mensajes))

    def test_la_confirmacion_avisa_de_las_otras_pendientes(self):
        elegida = con_ruc()
        con_ruc()

        respuesta = self.client.get(url_accion(elegida, 'aprobar'))

        self.assertContains(respuesta, f'Hay 1 solicitud(es) pendiente(s) más con el RUC {RUC}')
        self.assertEqual(self.estado(elegida), (Botica.Estado.PENDIENTE, None))

    def test_no_se_aprueba_si_otra_ya_esta_aprobada_con_ese_ruc(self):
        aprobada = con_ruc(Botica.Estado.APROBADO)
        pendiente = con_ruc()

        for metodo in ('get', 'post'):
            with self.subTest(metodo):
                if metodo == 'get':
                    respuesta = self.client.get(url_accion(pendiente, 'aprobar'), follow=True)
                else:
                    respuesta = self.post(url_accion(pendiente, 'aprobar'))

                self.assertRedirects(respuesta, detalle(pendiente))
                self.assertIn(f'la botica {aprobada.nombre_comercial} ya está aprobada con el RUC {RUC}',
                              self.mensajes(respuesta)[0])
        self.assertEqual(self.estado(pendiente), (Botica.Estado.PENDIENTE, None))
        self.assertFalse(BoticaRevision.objects.exists())
        self.assertEqual(mail.outbox, [])

    def test_revisar_lanza_el_error_y_no_cambia_nada(self):
        con_ruc(Botica.Estado.APROBADO)
        pendiente, otra = con_ruc(), con_ruc()

        with self.assertRaisesMessage(revision.RucYaAprobado, 'ya está aprobada con el RUC'):
            revision.revisar([pendiente.pk], Botica.Estado.APROBADO, 'guillermo')

        self.assertEqual(self.estado(pendiente), (Botica.Estado.PENDIENTE, None))
        self.assertEqual(self.estado(otra), (Botica.Estado.PENDIENTE, None))
        self.assertFalse(BoticaRevision.objects.exists())

    def test_accion_masiva_con_dos_del_mismo_ruc_no_aprueba_ninguna(self):
        primera, segunda = con_ruc(), con_ruc()
        de_otro_ruc = con_ruc(ruc=OTRO_RUC)

        respuesta = self.post(LISTA, {'action': 'aprobar_boticas',
                                      '_selected_action': [primera.pk, segunda.pk, de_otro_ruc.pk]})

        self.assertIn(f'Elegiste más de una botica con el RUC {RUC}', self.mensajes(respuesta)[0])
        for botica in (primera, segunda, de_otro_ruc):
            self.assertEqual(self.estado(botica), (Botica.Estado.PENDIENTE, None))
        self.assertFalse(BoticaRevision.objects.exists())
        self.assertEqual(mail.outbox, [])

    def test_accion_masiva_con_ruc_ya_aprobado_no_aprueba_ninguna(self):
        con_ruc(Botica.Estado.APROBADO)
        pendiente, de_otro_ruc = con_ruc(), con_ruc(ruc=OTRO_RUC)

        respuesta = self.post(LISTA, {'action': 'aprobar_boticas',
                                      '_selected_action': [pendiente.pk, de_otro_ruc.pk]})

        self.assertIn('No se aprobó ninguna botica.', self.mensajes(respuesta)[0])
        self.assertEqual(self.estado(de_otro_ruc), (Botica.Estado.PENDIENTE, None))

    def test_accion_masiva_rechaza_las_otras_pendientes(self):
        elegida, otra = con_ruc(), con_ruc()

        respuesta = self.post(LISTA, {'action': 'aprobar_boticas', '_selected_action': [elegida.pk]})

        self.assertIn('1 botica(s) aprobada(s).', self.mensajes(respuesta))
        self.assertEqual(self.estado(otra), (Botica.Estado.RECHAZADO, revision.MOTIVO_RUC_ACREDITADO))

    def test_select_estado_aprobado_con_ruc_ya_aprobado_no_guarda(self):
        con_ruc(Botica.Estado.APROBADO)
        pendiente = con_ruc()

        respuesta = self.post(detalle(pendiente), self.datos_de_la_ficha(pendiente, estado='APROBADO'))

        self.assertContains(respuesta, 'ya está aprobada con el RUC')
        self.assertEqual(self.estado(pendiente), (Botica.Estado.PENDIENTE, None))
        self.assertFalse(BoticaRevision.objects.exists())

    def test_select_estado_aprobado_rechaza_las_otras_pendientes(self):
        elegida, otra = con_ruc(), con_ruc()

        self.post(detalle(elegida), self.datos_de_la_ficha(elegida, estado='APROBADO'))

        self.assertEqual(self.estado(elegida), (Botica.Estado.APROBADO, None))
        self.assertEqual(self.estado(otra), (Botica.Estado.RECHAZADO, revision.MOTIVO_RUC_ACREDITADO))

    def test_rechazar_la_aprobada_libera_el_ruc(self):
        aprobada = con_ruc(Botica.Estado.APROBADO)
        pendiente = con_ruc()

        revision.revisar([aprobada.pk], Botica.Estado.RECHAZADO, 'guillermo', motivo=MOTIVO)
        revision.revisar([pendiente.pk], Botica.Estado.APROBADO, 'guillermo')

        self.assertEqual(self.estado(pendiente), (Botica.Estado.APROBADO, None))
        self.assertEqual(self.estado(aprobada), (Botica.Estado.RECHAZADO, MOTIVO))
