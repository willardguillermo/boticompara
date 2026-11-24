"""
H5/H6/H47: aprobar y rechazar boticas. Los botones de la ficha, la acción masiva y el select "estado"
usan la misma función (boticas/revision.py): mismo historial, mismo correo y mismos permisos.
"""
from itertools import count
from unittest import mock

from django.contrib.auth.models import Permission, User
from django.core import mail
from django.urls import reverse
from django.utils import timezone

from boticas import revision
from boticas.models import Botica, BoticaRevision, Usuario
from reportes.tests.base import TABLAS_DEL_PANEL, ConTablas

_numero = count(1)
LISTA = reverse('admin:boticas_botica_changelist')
MOTIVO = 'La licencia no corresponde al RUC registrado'


def crear_botica(estado=Botica.Estado.PENDIENTE, nombre=None, licencia=True):
    """licencia=False: sin licencia de funcionamiento cargada (S4-08 no deja aprobarla)."""
    n = next(_numero)
    ahora = timezone.now()
    dueno = Usuario.objects.create(nombre=f'Dueño {n}', correo=f'dueno{n}@prueba.pe', password_hash='x',
                                   rol=Usuario.Rol.DUENO_BOTICA, creado_en=ahora, actualizado_en=ahora)
    return Botica.objects.create(usuario=dueno, nombre_comercial=nombre or f'Botica {n}', ruc=f'{20500000000 + n}',
                                 razon_social=f'Razón {n}', direccion='Av. 1', distrito='Lima', estado=estado,
                                 motivo_rechazo=MOTIVO if estado == Botica.Estado.RECHAZADO else None,
                                 licencia_url=f'botica-{n}/licencia.pdf' if licencia else None,
                                 creado_en=ahora, actualizado_en=ahora)


def detalle(botica):
    return reverse('admin:boticas_botica_change', args=[botica.pk])


def url_accion(botica, accion):
    return reverse(f'admin:boticas_botica_{accion}', args=[botica.pk])


class ConAdmin(ConTablas):
    # La ficha muestra los productos en línea: hacen falta las tablas del catálogo
    tablas = (*TABLAS_DEL_PANEL, BoticaRevision)

    @classmethod
    def setUpTestData(cls):
        cls.admin = User.objects.create_superuser('guillermo', 'admin@prueba.pe', 'clave-de-prueba')

    def setUp(self):
        self.client.force_login(self.admin)

    def mensajes(self, respuesta):
        return [str(m) for m in respuesta.context['messages']]

    def post(self, url, datos=None):
        # El correo sale después del commit: se ejecutan esos callbacks como en producción
        with self.captureOnCommitCallbacks(execute=True):
            return self.client.post(url, datos or {}, follow=True)

    def estado(self, botica):
        return Botica.objects.values_list('estado', 'motivo_rechazo').get(pk=botica.pk)


class BotonesDeLaFichaTests(ConAdmin):

    def test_botones_segun_el_estado(self):
        pendiente = crear_botica()
        aprobada = crear_botica(Botica.Estado.APROBADO)
        rechazada = crear_botica(Botica.Estado.RECHAZADO)

        ficha = self.client.get(detalle(pendiente))
        self.assertContains(ficha, url_accion(pendiente, 'aprobar'))
        self.assertContains(ficha, url_accion(pendiente, 'rechazar'))
        # Aprobar es la acción principal en verde; Rechazar va en rojo
        self.assertContains(ficha, 'class="accion-aprobar" style="background: #2e7d32;')
        self.assertContains(ficha, 'class="accion-rechazar" style="background: var(--delete-button-bg);')

        ficha = self.client.get(detalle(aprobada))
        self.assertNotContains(ficha, url_accion(aprobada, 'aprobar'))
        self.assertContains(ficha, url_accion(aprobada, 'rechazar'))

        ficha = self.client.get(detalle(rechazada))
        self.assertNotContains(ficha, url_accion(rechazada, 'aprobar'))
        self.assertNotContains(ficha, url_accion(rechazada, 'rechazar'))


class AprobarTests(ConAdmin):

    def test_get_solo_pide_confirmar(self):
        botica = crear_botica()

        respuesta = self.client.get(url_accion(botica, 'aprobar'))

        self.assertContains(respuesta, f'¿Aprobar la botica {botica.nombre_comercial}?')
        self.assertContains(respuesta, 'value="Sí, aprobar botica"')
        self.assertEqual(self.estado(botica), (Botica.Estado.PENDIENTE, None))
        self.assertFalse(BoticaRevision.objects.exists())

    def test_aprueba_deja_historial_y_avisa_por_correo(self):
        botica = crear_botica()

        respuesta = self.post(url_accion(botica, 'aprobar'))

        self.assertRedirects(respuesta, detalle(botica))
        self.assertEqual(self.estado(botica), (Botica.Estado.APROBADO, None))
        revision_, = BoticaRevision.objects.all()
        self.assertEqual((revision_.botica_id, revision_.estado, revision_.motivo, revision_.revisado_por),
                         (botica.pk, BoticaRevision.Estado.APROBADO, None, 'guillermo'))
        self.assertEqual(len(mail.outbox), 1)
        self.assertEqual(mail.outbox[0].to, [botica.usuario.correo])
        self.assertIn('fue aprobada', mail.outbox[0].subject)
        self.assertIn(f'Se aprobó la botica {botica.nombre_comercial}.', self.mensajes(respuesta)[0])

    def test_solo_se_aprueba_una_pendiente(self):
        for estado in (Botica.Estado.APROBADO, Botica.Estado.RECHAZADO):
            with self.subTest(estado):
                botica = crear_botica(estado)

                respuesta = self.post(url_accion(botica, 'aprobar'))

                self.assertRedirects(respuesta, detalle(botica))
                self.assertEqual(self.estado(botica)[0], estado)
                self.assertIn('Solo se aprueba una botica pendiente.', self.mensajes(respuesta)[0])
        self.assertFalse(BoticaRevision.objects.exists())
        self.assertEqual(mail.outbox, [])

    def test_aprobada_por_otro_admin_mientras_tanto(self):
        botica = crear_botica()
        # La página se abrió con la botica pendiente; otro administrador la aprueba antes de confirmar
        with mock.patch.object(revision, 'PUEDE_APROBAR', (Botica.Estado.PENDIENTE, Botica.Estado.APROBADO)):
            Botica.objects.filter(pk=botica.pk).update(estado=Botica.Estado.APROBADO)
            respuesta = self.post(url_accion(botica, 'aprobar'))

        self.assertIn('No se hizo ningún cambio: la botica ya está «Aprobado».', self.mensajes(respuesta)[0])
        self.assertFalse(BoticaRevision.objects.exists())
        self.assertEqual(mail.outbox, [])

    def test_si_falla_el_correo_la_aprobacion_igual_se_guarda(self):
        botica = crear_botica()

        si_falla = mock.Mock()

        # Directo a la función: en la prueba los callbacks de on_commit corren después de la petición
        with mock.patch('boticas.revision.notificar_revision', return_value=False), \
                self.captureOnCommitCallbacks(execute=True):
            revision.revisar([botica.pk], Botica.Estado.APROBADO, 'guillermo', si_falla_correo=si_falla)

        self.assertEqual(self.estado(botica)[0], Botica.Estado.APROBADO)
        self.assertEqual(BoticaRevision.objects.count(), 1)
        si_falla.assert_called_once()
        self.assertEqual(si_falla.call_args.args[0].pk, botica.pk)


class RechazarTests(ConAdmin):

    def test_get_pide_el_motivo(self):
        botica = crear_botica()

        respuesta = self.client.get(url_accion(botica, 'rechazar'))

        self.assertContains(respuesta, f'Rechazar la botica {botica.nombre_comercial}')
        self.assertContains(respuesta, 'name="motivo"')
        self.assertEqual(self.estado(botica), (Botica.Estado.PENDIENTE, None))

    def test_sin_motivo_no_rechaza(self):
        botica = crear_botica()

        for motivo in ('', '    '):
            with self.subTest(motivo=motivo):
                respuesta = self.post(url_accion(botica, 'rechazar'), {'motivo': motivo})

                self.assertEqual(respuesta.status_code, 200)
                self.assertContains(respuesta, 'Indica el motivo del rechazo.')
        self.assertEqual(self.estado(botica), (Botica.Estado.PENDIENTE, None))
        self.assertFalse(BoticaRevision.objects.exists())
        self.assertEqual(mail.outbox, [])

    def test_rechaza_con_motivo_deja_historial_y_avisa_por_correo(self):
        for estado in (Botica.Estado.PENDIENTE, Botica.Estado.APROBADO):
            with self.subTest(estado):
                botica = crear_botica(estado)
                mail.outbox = []

                respuesta = self.post(url_accion(botica, 'rechazar'), {'motivo': f'  {MOTIVO}  '})

                self.assertRedirects(respuesta, detalle(botica))
                self.assertEqual(self.estado(botica), (Botica.Estado.RECHAZADO, MOTIVO))
                revision_ = BoticaRevision.objects.get(botica=botica)
                self.assertEqual((revision_.estado, revision_.motivo, revision_.revisado_por),
                                 (BoticaRevision.Estado.RECHAZADO, MOTIVO, 'guillermo'))
                self.assertEqual(len(mail.outbox), 1)
                self.assertIn(f'Motivo: {MOTIVO}', mail.outbox[0].body)

    def test_rechazar_una_aprobada_avisa_que_sale_de_las_busquedas(self):
        botica = crear_botica(Botica.Estado.APROBADO)

        respuesta = self.client.get(url_accion(botica, 'rechazar'))

        self.assertContains(respuesta, 'dejarán de aparecer en las búsquedas')

    def test_una_rechazada_no_se_vuelve_a_rechazar(self):
        botica = crear_botica(Botica.Estado.RECHAZADO)

        respuesta = self.post(url_accion(botica, 'rechazar'), {'motivo': 'Otro motivo distinto'})

        self.assertIn('Solo se rechaza una botica pendiente o aprobada.', self.mensajes(respuesta)[0])
        self.assertEqual(self.estado(botica), (Botica.Estado.RECHAZADO, MOTIVO))
        self.assertFalse(BoticaRevision.objects.exists())


class PermisosTests(ConAdmin):

    def entrar_con(self, *codenames):
        usuario = User.objects.create_user('practicante', password='clave-de-prueba', is_staff=True)
        usuario.user_permissions.add(*Permission.objects.filter(codename__in=codenames))
        self.client.force_login(usuario)

    def test_solo_ver_no_tiene_botones_y_recibe_403(self):
        self.entrar_con('view_botica')
        botica = crear_botica()

        ficha = self.client.get(detalle(botica))
        self.assertEqual(ficha.status_code, 200)
        self.assertNotContains(ficha, url_accion(botica, 'aprobar'))
        self.assertNotContains(ficha, url_accion(botica, 'rechazar'))
        for respuesta in (self.client.get(url_accion(botica, 'aprobar')),
                          self.client.post(url_accion(botica, 'aprobar')),
                          self.client.get(url_accion(botica, 'rechazar')),
                          self.client.post(url_accion(botica, 'rechazar'), {'motivo': MOTIVO})):
            self.assertEqual(respuesta.status_code, 403)
        self.assertEqual(self.estado(botica), (Botica.Estado.PENDIENTE, None))
        self.assertFalse(BoticaRevision.objects.exists())

    def test_con_permiso_de_cambio_puede_revisar(self):
        self.entrar_con('view_botica', 'change_botica')
        botica = crear_botica()

        self.assertContains(self.client.get(detalle(botica)), url_accion(botica, 'aprobar'))
        self.post(url_accion(botica, 'aprobar'))
        self.assertEqual(self.estado(botica)[0], Botica.Estado.APROBADO)
        self.assertEqual(BoticaRevision.objects.get().revisado_por, 'practicante')

    def test_anonimo_va_al_login(self):
        self.client.logout()
        botica = crear_botica()
        for accion in ('aprobar', 'rechazar'):
            respuesta = self.client.get(url_accion(botica, accion))
            self.assertEqual(respuesta.status_code, 302)
            self.assertIn(reverse('admin:login'), respuesta['Location'])


class MismaLogicaEnLosTresLugaresTests(ConAdmin):
    """La acción masiva y el select "estado" siguen funcionando igual tras extraer revision.revisar."""

    def datos_de_la_ficha(self, botica, **cambios):
        # Lo que envía el formulario: estado, motivo y los formularios de gestión de los inlines
        respuesta = self.client.get(detalle(botica))
        datos = {'estado': botica.estado, 'motivo_rechazo': botica.motivo_rechazo or ''}
        for inline in respuesta.context['inline_admin_formsets']:
            gestion = inline.formset.management_form
            datos.update({gestion.add_prefix(campo): valor for campo, valor in gestion.initial.items()})
        datos.update(cambios)
        return datos

    def test_accion_masiva_aprueba_solo_las_pendientes(self):
        # S4-08: como el botón Aprobar, solo desde PENDIENTE. La rechazada no cambia y se avisa que se omitió
        pendiente, rechazada = crear_botica(), crear_botica(Botica.Estado.RECHAZADO)
        aprobada = crear_botica(Botica.Estado.APROBADO)

        respuesta = self.post(LISTA, {'action': 'aprobar_boticas',
                                      '_selected_action': [pendiente.pk, rechazada.pk, aprobada.pk]})

        self.assertIn('1 botica(s) aprobada(s).', self.mensajes(respuesta))
        self.assertIn('Se omitieron 2 botica(s) que no estaban pendientes. Solo se aprueba una botica pendiente.',
                      self.mensajes(respuesta))
        self.assertEqual(self.estado(pendiente), (Botica.Estado.APROBADO, None))
        self.assertEqual(self.estado(rechazada), (Botica.Estado.RECHAZADO, MOTIVO))
        self.assertEqual(self.estado(aprobada), (Botica.Estado.APROBADO, None))
        self.assertEqual(set(BoticaRevision.objects.values_list('botica_id', flat=True)), {pendiente.pk})
        self.assertEqual(len(mail.outbox), 1)

    def test_select_estado_rechaza_con_historial_y_correo(self):
        botica = crear_botica()

        self.post(detalle(botica), self.datos_de_la_ficha(botica, estado='RECHAZADO', motivo_rechazo=MOTIVO))

        self.assertEqual(self.estado(botica), (Botica.Estado.RECHAZADO, MOTIVO))
        revision_ = BoticaRevision.objects.get()
        self.assertEqual((revision_.estado, revision_.motivo, revision_.revisado_por),
                         (BoticaRevision.Estado.RECHAZADO, MOTIVO, 'guillermo'))
        self.assertEqual(len(mail.outbox), 1)

    def test_select_sin_cambio_de_estado_solo_guarda_el_motivo(self):
        botica = crear_botica(Botica.Estado.RECHAZADO)

        self.post(detalle(botica), self.datos_de_la_ficha(botica, motivo_rechazo='Motivo corregido por el admin'))

        self.assertEqual(self.estado(botica), (Botica.Estado.RECHAZADO, 'Motivo corregido por el admin'))
        self.assertFalse(BoticaRevision.objects.exists())
        self.assertEqual(mail.outbox, [])

    def test_select_rechazado_sin_motivo_no_guarda(self):
        botica = crear_botica()

        respuesta = self.post(detalle(botica), self.datos_de_la_ficha(botica, estado='RECHAZADO'))

        self.assertContains(respuesta, 'Indica el motivo del rechazo.')
        self.assertEqual(self.estado(botica), (Botica.Estado.PENDIENTE, None))
        self.assertFalse(BoticaRevision.objects.exists())

    def test_revisar_sin_motivo_al_rechazar_es_error(self):
        botica = crear_botica()

        with self.assertRaisesMessage(ValueError, 'Indica el motivo del rechazo.'):
            revision.revisar([botica.pk], Botica.Estado.RECHAZADO, 'guillermo', motivo='  ')
        self.assertEqual(self.estado(botica), (Botica.Estado.PENDIENTE, None))
