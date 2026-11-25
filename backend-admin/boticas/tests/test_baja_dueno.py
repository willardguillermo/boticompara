"""
S4-02: "Dar de baja" a un dueño hace lo mismo que cuando él elimina su cuenta desde la web: su botica pasa a
RECHAZADO (con historial, así su RUC queda libre por A5) y sus reservas activas se rechazan con un correo a cada
comprador. Todo en la misma transacción de la baja; los correos, después del commit.
"""
from datetime import timedelta
from decimal import Decimal
from unittest import mock

from django.contrib.auth.models import User
from django.core import mail
from django.utils import timezone

from boticas import revision, usuarios
from boticas.models import Botica, BoticaRevision, Reserva, Usuario, UsuarioAccion
from boticas.tests.test_revision import crear_botica
from boticas.tests.test_usuarios import crear_usuario, url_accion
from reportes.tests.base import TABLAS_DEL_PANEL, ConTablas
from reportes.tests.test_panel import crear_producto

MOTIVO = 'Suplantó la identidad de una botica'


def crear_reserva(producto, comprador, estado, codigo):
    return Reserva.objects.create(codigo=codigo, usuario=comprador, producto=producto, cantidad=2,
                                  precio_unitario=Decimal('5.50'), estado=estado,
                                  motivo_rechazo='Sin stock' if estado == Reserva.Estado.RECHAZADA else None,
                                  vence_en=timezone.now() + timedelta(days=1))


class BajaDelDuenoTests(ConTablas):
    tablas = (*TABLAS_DEL_PANEL, BoticaRevision, UsuarioAccion, Reserva)

    def setUp(self):
        self.botica = crear_botica(Botica.Estado.APROBADO, nombre='Botica Rosa')
        Botica.objects.filter(pk=self.botica.pk).update(telefono='014445566')
        self.dueno = self.botica.usuario
        self.producto = crear_producto(self.botica, 'Panadol', 'Caja x 10')
        self.comprador = crear_usuario(nombre='Carlos Pérez')

    def dar_baja(self, usuario=None):
        with self.captureOnCommitCallbacks(execute=True) as callbacks:
            usuarios.dar_baja((usuario or self.dueno).pk, MOTIVO, 'guillermo')
        return callbacks

    def estado(self, modelo, obj):
        return modelo.objects.values_list('estado', 'motivo_rechazo').get(pk=obj.pk)

    def test_la_botica_aprobada_pasa_a_rechazado_con_historial_y_sin_correo_al_dueno(self):
        self.dar_baja()

        self.assertEqual(self.estado(Botica, self.botica), (Botica.Estado.RECHAZADO, usuarios.MOTIVO_BOTICA_BAJA))
        revision_ = BoticaRevision.objects.get()
        self.assertEqual((revision_.botica_id, revision_.estado, revision_.motivo, revision_.revisado_por),
                         (self.botica.pk, 'RECHAZADO', 'Cuenta dada de baja por el administrador', 'guillermo'))
        # Ni el correo de "solicitud observada": ya no puede corregir ni reenviar nada
        self.assertFalse([m for m in mail.outbox if self.dueno.correo in m.to])

    def test_libera_el_ruc_para_otra_cuenta(self):
        self.dar_baja()

        otra = crear_botica()
        Botica.objects.filter(pk=otra.pk).update(ruc=self.botica.ruc)
        aprobadas = revision.revisar([otra.pk], Botica.Estado.APROBADO, 'guillermo')

        self.assertEqual([b.pk for b in aprobadas], [otra.pk])

    def test_una_pendiente_tambien_pasa_a_rechazado(self):
        Botica.objects.filter(pk=self.botica.pk).update(estado=Botica.Estado.PENDIENTE)

        self.dar_baja()

        self.assertEqual(self.estado(Botica, self.botica), (Botica.Estado.RECHAZADO, usuarios.MOTIVO_BOTICA_BAJA))

    def test_una_ya_rechazada_conserva_su_motivo_y_no_suma_historial(self):
        Botica.objects.filter(pk=self.botica.pk).update(estado=Botica.Estado.RECHAZADO,
                                                        motivo_rechazo='La licencia está vencida')

        self.dar_baja()

        self.assertEqual(self.estado(Botica, self.botica), (Botica.Estado.RECHAZADO, 'La licencia está vencida'))
        self.assertFalse(BoticaRevision.objects.exists())

    def test_rechaza_las_reservas_activas_y_avisa_a_cada_comprador(self):
        activas = [crear_reserva(self.producto, self.comprador, estado, f'BC-ACT{n}')
                   for n, estado in enumerate((Reserva.Estado.PENDIENTE, Reserva.Estado.CONFIRMADA,
                                               Reserva.Estado.LISTA))]
        entregada = crear_reserva(self.producto, self.comprador, Reserva.Estado.ENTREGADA, 'BC-ENT1')
        rechazada = crear_reserva(self.producto, self.comprador, Reserva.Estado.RECHAZADA, 'BC-REC1')
        cancelada = crear_reserva(self.producto, self.comprador, Reserva.Estado.CANCELADA, 'BC-CAN1')

        self.dar_baja()

        for reserva in activas:
            self.assertEqual(self.estado(Reserva, reserva),
                             (Reserva.Estado.RECHAZADA, 'La botica dejó de atender en BotiCompara'))
        # Las que ya terminaron no se tocan
        self.assertEqual(self.estado(Reserva, entregada), (Reserva.Estado.ENTREGADA, None))
        self.assertEqual(self.estado(Reserva, rechazada), (Reserva.Estado.RECHAZADA, 'Sin stock'))
        self.assertEqual(self.estado(Reserva, cancelada), (Reserva.Estado.CANCELADA, None))
        self.assertEqual(sorted(m.subject for m in mail.outbox),
                         sorted(f'Tu reserva {r.codigo} fue rechazada' for r in activas))
        self.assertTrue(all(m.to == [self.comprador.correo] for m in mail.outbox))

    def test_el_correo_es_el_de_rechazo_de_la_api(self):
        reserva = crear_reserva(self.producto, self.comprador, Reserva.Estado.PENDIENTE, 'BC-7K2QXM')

        self.dar_baja()

        self.assertEqual(len(mail.outbox), 1)
        self.assertEqual(mail.outbox[0].body,
                         'Hola Carlos Pérez,\n\n'
                         'Lamentablemente la botica no pudo atender tu reserva.\n\n'
                         f'Código: {reserva.codigo}\n'
                         'Producto: Panadol (Caja x 10)\n'
                         'Cantidad: 2\n'
                         'Total: S/ 11.00\n'
                         'Botica: Botica Rosa — Av. 1, Lima\n'
                         'Teléfono: 014445566\n'
                         'Motivo: La botica dejó de atender en BotiCompara\n'
                         '\nBotiCompara\n')

    def test_los_correos_salen_despues_del_commit(self):
        crear_reserva(self.producto, self.comprador, Reserva.Estado.PENDIENTE, 'BC-AAAA01')

        with self.captureOnCommitCallbacks(execute=False) as callbacks:
            usuarios.dar_baja(self.dueno.pk, MOTIVO, 'guillermo')

        # Dentro de la transacción ya está todo guardado, pero ningún correo salió todavía
        self.assertEqual(mail.outbox, [])
        self.assertEqual(Reserva.objects.get().estado, Reserva.Estado.RECHAZADA)
        self.assertEqual(len(callbacks), 1)

    def test_si_algo_falla_no_se_guarda_nada_ni_sale_ningun_correo(self):
        crear_reserva(self.producto, self.comprador, Reserva.Estado.PENDIENTE, 'BC-AAAA02')

        with mock.patch('boticas.usuarios._rechazar_reservas_activas', side_effect=RuntimeError('falla')), \
                self.captureOnCommitCallbacks(execute=True):
            with self.assertRaises(RuntimeError):
                usuarios.dar_baja(self.dueno.pk, MOTIVO, 'guillermo')

        self.dueno.refresh_from_db()
        self.assertTrue(self.dueno.activo)
        self.assertEqual(self.estado(Botica, self.botica), (Botica.Estado.APROBADO, None))
        self.assertFalse(BoticaRevision.objects.exists())
        self.assertFalse(UsuarioAccion.objects.exists())
        self.assertEqual(Reserva.objects.get().estado, Reserva.Estado.PENDIENTE)
        self.assertEqual(mail.outbox, [])

    def test_si_falla_un_correo_la_baja_igual_queda(self):
        crear_reserva(self.producto, self.comprador, Reserva.Estado.PENDIENTE, 'BC-AAAA03')

        with mock.patch('boticas.notificaciones.send_mail', side_effect=OSError('SMTP caído')), \
                self.assertLogs('boticas.notificaciones', 'ERROR'):
            self.dar_baja()

        self.assertEqual(Reserva.objects.get().estado, Reserva.Estado.RECHAZADA)
        self.assertEqual(self.estado(Botica, self.botica)[0], Botica.Estado.RECHAZADO)

    def test_un_comprador_no_toca_boticas_ni_reservas(self):
        reserva = crear_reserva(self.producto, self.comprador, Reserva.Estado.PENDIENTE, 'BC-AAAA04')

        self.dar_baja(self.comprador)

        self.assertEqual(self.estado(Botica, self.botica), (Botica.Estado.APROBADO, None))
        self.assertEqual(self.estado(Reserva, reserva), (Reserva.Estado.PENDIENTE, None))
        self.assertFalse(BoticaRevision.objects.exists())
        self.assertEqual(mail.outbox, [])

    def test_la_pagina_de_confirmacion_lo_advierte(self):
        self.client.force_login(User.objects.create_superuser('guillermo', 'admin@prueba.pe', 'clave-de-prueba'))

        respuesta = self.client.post(url_accion(self.dueno, 'dar_baja'), {'motivo': MOTIVO})

        self.assertContains(respuesta, 'la botica pasa a «Rechazado»')
        self.assertContains(respuesta, 'sus reservas pendientes, confirmadas o listas se rechazan')
        self.assertEqual(Usuario.objects.get(pk=self.dueno.pk).activo, True)
