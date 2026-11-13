"""
H90-H94: gestión de usuarios por el administrador.

Las tablas son managed = False: ConTablas las crea en el SQLite de pruebas con los CHECK copiados
de database/schema.sql (Meta.constraints de los modelos).
"""
from datetime import datetime, timedelta
from datetime import timezone as dt_timezone
from itertools import count

from django.contrib.auth.models import Permission, User
from django.db import IntegrityError, transaction
from django.test import SimpleTestCase
from django.urls import reverse
from django.utils import timezone

from boticas import usuarios
from boticas.models import Botica, Usuario, UsuarioAccion
from reportes.tests.base import ConTablas

_numero = count(1)
LISTA = reverse('admin:boticas_usuario_changelist')


def crear_usuario(estado=usuarios.ACTIVO, rol=Usuario.Rol.COMPRADOR, nombre=None, **extra):
    n = next(_numero)
    ahora = timezone.now()
    datos = {'activo': True, 'suspendido_hasta': None, 'motivo_estado': None}
    if estado == usuarios.SUSPENDIDO:
        datos.update(suspendido_hasta=ahora + timedelta(days=3), motivo_estado='Reportes de abuso en reseñas')
    elif estado == usuarios.BAJA:
        datos.update(activo=False, motivo_estado='Cuenta fraudulenta confirmada')
    datos.update(extra)
    return Usuario.objects.create(nombre=nombre or f'Usuario {n}', correo=f'u{n}@prueba.pe', password_hash='x',
                                  rol=rol, creado_en=ahora, actualizado_en=ahora, **datos)


def detalle(usuario):
    return reverse('admin:boticas_usuario_change', args=[usuario.pk])


def url_accion(usuario, accion):
    return reverse(f'admin:boticas_usuario_{accion}', args=[usuario.pk])


def en_dias(dias):
    return f'{timezone.localdate() + timedelta(days=dias):%Y-%m-%d}'


class EstadoTests(SimpleTestCase):
    """Reglas del estado sin BD: activo manda y la suspensión vence sola."""

    def setUp(self):
        self.ahora = timezone.now()

    def usuario(self, activo=True, suspendido_hasta=None):
        return Usuario(nombre='Ana', activo=activo, suspendido_hasta=suspendido_hasta)

    def test_activo_sin_suspension(self):
        self.assertEqual(usuarios.estado_de(self.usuario(), self.ahora), usuarios.ACTIVO)
        self.assertEqual(usuarios.texto_estado(self.usuario(), self.ahora), 'Activo')

    def test_suspension_vigente(self):
        hasta = timezone.make_aware(timezone.datetime(2026, 11, 20, 23, 59, 59))
        usuario = self.usuario(suspendido_hasta=hasta)
        self.assertEqual(usuarios.estado_de(usuario, hasta - timedelta(days=1)), usuarios.SUSPENDIDO)
        self.assertEqual(usuarios.texto_estado(usuario, hasta - timedelta(days=1)), 'Suspendido hasta 20/11/2026')

    def test_suspension_vencida_es_activo(self):
        usuario = self.usuario(suspendido_hasta=self.ahora - timedelta(seconds=1))
        self.assertEqual(usuarios.estado_de(usuario, self.ahora), usuarios.ACTIVO)
        # Justo en el límite (suspendido_hasta <= now) ya está activa
        usuario.suspendido_hasta = self.ahora
        self.assertEqual(usuarios.estado_de(usuario, self.ahora), usuarios.ACTIVO)

    def test_baja(self):
        self.assertEqual(usuarios.estado_de(self.usuario(activo=False), self.ahora), usuarios.BAJA)
        self.assertEqual(usuarios.texto_estado(self.usuario(activo=False), self.ahora), 'Baja')


class ConAdmin(ConTablas):
    tablas = (Usuario, Botica, UsuarioAccion)

    @classmethod
    def setUpTestData(cls):
        cls.admin = User.objects.create_superuser('guillermo', 'admin@prueba.pe', 'clave-de-prueba')

    def setUp(self):
        self.client.force_login(self.admin)

    def mensajes(self, respuesta):
        return [str(m) for m in respuesta.context['messages']]


class ListaUsuariosTests(ConAdmin):
    """H90"""

    def ids_de_la_lista(self, parametros=''):
        respuesta = self.client.get(LISTA + parametros)
        self.assertEqual(respuesta.status_code, 200)
        return {u.pk for u in respuesta.context['cl'].result_list}

    def test_columnas_y_estado(self):
        crear_usuario(nombre='Ana Activa')
        suspendido = crear_usuario(usuarios.SUSPENDIDO, nombre='Sergio Suspendido')
        crear_usuario(usuarios.BAJA, nombre='Beatriz Baja')

        respuesta = self.client.get(LISTA)

        for texto in ('Ana Activa', 'Activo', 'Beatriz Baja', 'Baja', 'Fecha de registro', 'Comprador',
                      f'Suspendido hasta {timezone.localtime(suspendido.suspendido_hasta):%d/%m/%Y}'):
            self.assertContains(respuesta, texto)

    def test_filtro_de_estado(self):
        activo = crear_usuario()
        vencido = crear_usuario(suspendido_hasta=timezone.now() - timedelta(hours=1),
                                motivo_estado='Suspensión ya cumplida')
        suspendido = crear_usuario(usuarios.SUSPENDIDO)
        baja = crear_usuario(usuarios.BAJA)

        self.assertEqual(self.ids_de_la_lista('?estado=ACTIVO'), {activo.pk, vencido.pk})
        self.assertEqual(self.ids_de_la_lista('?estado=SUSPENDIDO'), {suspendido.pk})
        self.assertEqual(self.ids_de_la_lista('?estado=BAJA'), {baja.pk})
        self.assertEqual(self.ids_de_la_lista(), {activo.pk, vencido.pk, suspendido.pk, baja.pk})

    def test_filtro_de_estado_combinado_con_rol(self):
        comprador = crear_usuario()
        crear_usuario(rol=Usuario.Rol.DUENO_BOTICA)
        crear_usuario(usuarios.BAJA)

        self.assertEqual(self.ids_de_la_lista('?estado=ACTIVO&rol__exact=COMPRADOR'), {comprador.pk})

    def test_busqueda_por_nombre_y_correo(self):
        ana = crear_usuario(nombre='Ana Torres')
        crear_usuario(nombre='Carlos Ruiz')

        self.assertEqual(self.ids_de_la_lista('?q=torres'), {ana.pk})
        self.assertEqual(self.ids_de_la_lista(f'?q={ana.correo}'), {ana.pk})

    def test_detalle_de_solo_lectura_sin_datos_sensibles(self):
        usuario = crear_usuario(nombre='Ana Torres', google_uid='uid-secreto')

        respuesta = self.client.get(detalle(usuario))

        self.assertEqual(respuesta.status_code, 200)
        self.assertNotContains(respuesta, 'name="nombre"')
        self.assertNotContains(respuesta, 'name="_save"')
        self.assertNotContains(respuesta, 'uid-secreto')
        self.assertNotContains(respuesta, 'password_hash')

    def test_nadie_edita_ni_borra_ni_crea(self):
        usuario = crear_usuario(nombre='Ana Torres')

        editar = self.client.post(detalle(usuario), {'nombre': 'Otro nombre', 'correo': 'otro@prueba.pe'})
        borrar = self.client.post(reverse('admin:boticas_usuario_delete', args=[usuario.pk]), {'post': 'yes'})
        crear = self.client.get(reverse('admin:boticas_usuario_add'))

        self.assertEqual((editar.status_code, borrar.status_code, crear.status_code), (403, 403, 403))
        usuario.refresh_from_db()
        self.assertEqual(usuario.nombre, 'Ana Torres')
        self.assertTrue(Usuario.objects.filter(pk=usuario.pk).exists())

    def test_motivo_de_suspension_vencida_no_se_muestra(self):
        usuario = crear_usuario(suspendido_hasta=timezone.now() - timedelta(hours=1),
                                motivo_estado='Motivo de una suspensión vieja')

        self.assertNotContains(self.client.get(detalle(usuario)), 'Motivo de una suspensión vieja')


class SuspenderTests(ConAdmin):
    """H91"""
    MOTIVO = 'Publicó reseñas ofensivas'

    def suspender(self, usuario, hasta=None, motivo=MOTIVO):
        return self.client.post(url_accion(usuario, 'suspender'),
                                {'hasta': hasta or en_dias(1), 'motivo': motivo}, follow=True)

    def test_activo_se_suspende_y_queda_en_el_historial(self):
        usuario = crear_usuario()

        respuesta = self.suspender(usuario, hasta=en_dias(7))

        usuario.refresh_from_db()
        ultimo_dia = timezone.localdate() + timedelta(days=7)
        self.assertEqual(usuarios.estado_de(usuario), usuarios.SUSPENDIDO)
        self.assertEqual(timezone.localtime(usuario.suspendido_hasta),
                         usuarios.fin_del_dia(ultimo_dia))
        self.assertEqual(usuario.motivo_estado, self.MOTIVO)
        self.assertRedirects(respuesta, detalle(usuario))
        self.assertIn(f'Se suspendió la cuenta de {usuario.nombre} hasta el {ultimo_dia:%d/%m/%Y}.',
                      self.mensajes(respuesta))
        accion = UsuarioAccion.objects.get(usuario=usuario)
        self.assertEqual((accion.accion, accion.motivo, accion.realizado_por),
                         (UsuarioAccion.Accion.SUSPENDER, self.MOTIVO, 'guillermo'))

    def test_suspension_vencida_se_puede_volver_a_suspender(self):
        usuario = crear_usuario(suspendido_hasta=timezone.now() - timedelta(hours=1), motivo_estado='Anterior')

        self.suspender(usuario)

        usuario.refresh_from_db()
        self.assertEqual(usuarios.estado_de(usuario), usuarios.SUSPENDIDO)
        self.assertEqual(usuario.motivo_estado, self.MOTIVO)

    def test_formulario_solo_si_esta_activo(self):
        self.assertContains(self.client.get(url_accion(crear_usuario(), 'suspender')), 'Suspender cuenta')

    def test_suspendido_o_baja_se_rechaza_tambien_por_url(self):
        for estado in (usuarios.SUSPENDIDO, usuarios.BAJA):
            with self.subTest(estado=estado):
                usuario = crear_usuario(estado)
                antes = (usuario.activo, usuario.suspendido_hasta, usuario.motivo_estado)

                pagina = self.client.get(url_accion(usuario, 'suspender'), follow=True)
                envio = self.suspender(usuario)

                for respuesta in (pagina, envio):
                    self.assertRedirects(respuesta, detalle(usuario))
                    self.assertTrue(any(m.startswith(f'No se puede suspender la cuenta de {usuario.nombre}')
                                        for m in self.mensajes(respuesta)))
                usuario.refresh_from_db()
                self.assertEqual((usuario.activo, usuario.suspendido_hasta, usuario.motivo_estado), antes)
                self.assertFalse(UsuarioAccion.objects.filter(usuario=usuario).exists())

    def test_fecha_hasta_debe_ser_futura_y_maximo_90_dias(self):
        usuario = crear_usuario()
        for hasta, error in ((en_dias(0), 'La fecha debe ser futura'),
                             (en_dias(-3), 'La fecha debe ser futura'),
                             (en_dias(91), 'como máximo 90 días'),
                             ('31/02/2026', 'Escribe una fecha válida'),
                             ('', 'Elige hasta qué día')):
            with self.subTest(hasta=hasta):
                respuesta = self.client.post(url_accion(usuario, 'suspender'), {'hasta': hasta, 'motivo': self.MOTIVO})
                self.assertEqual(respuesta.status_code, 200)
                self.assertContains(respuesta, error)
        usuario.refresh_from_db()
        self.assertIsNone(usuario.suspendido_hasta)
        self.assertFalse(UsuarioAccion.objects.exists())

    def test_limites_validos_mañana_y_90_dias(self):
        for dias in (1, 90):
            with self.subTest(dias=dias):
                usuario = crear_usuario()
                self.suspender(usuario, hasta=en_dias(dias))
                usuario.refresh_from_db()
                self.assertEqual(usuarios.estado_de(usuario), usuarios.SUSPENDIDO)

    def test_motivo_de_10_a_300_caracteres(self):
        usuario = crear_usuario()
        for motivo, error in (('', 'Escribe el motivo.'), ('   corto  ', 'al menos 10 caracteres (tiene 5)'),
                              ('x' * 301, 'como máximo 300 caracteres (tiene 301)')):
            with self.subTest(motivo=motivo[:12]):
                respuesta = self.client.post(url_accion(usuario, 'suspender'), {'hasta': en_dias(1), 'motivo': motivo})
                self.assertContains(respuesta, error)
        self.assertFalse(UsuarioAccion.objects.exists())

    def test_estado_cambiado_por_otro_admin_no_deja_historial(self):
        """Si la cuenta dejó de estar activa entre que se abrió el formulario y se envió."""
        usuario = crear_usuario()
        Usuario.objects.filter(pk=usuario.pk).update(activo=False)

        with self.assertRaises(usuarios.AccionNoPermitida):
            usuarios.suspender(usuario.pk, timezone.localdate() + timedelta(days=1), self.MOTIVO, 'guillermo')
        self.assertFalse(UsuarioAccion.objects.exists())

    def test_sin_permiso_de_cambio_no_puede_suspender(self):
        solo_lectura = User.objects.create_user('practicante', password='clave-de-prueba', is_staff=True)
        solo_lectura.user_permissions.add(Permission.objects.get(codename='view_usuario'))
        self.client.force_login(solo_lectura)
        usuario = crear_usuario()

        self.assertEqual(self.suspender(usuario).status_code, 403)
        self.assertNotContains(self.client.get(detalle(usuario)), url_accion(usuario, 'suspender'))
        usuario.refresh_from_db()
        self.assertIsNone(usuario.suspendido_hasta)


class ReactivarTests(ConAdmin):
    """H92"""

    def reactivar(self, usuario, motivo=''):
        return self.client.post(url_accion(usuario, 'reactivar'), {'motivo': motivo}, follow=True)

    def test_suspendido_se_reactiva_sin_motivo(self):
        usuario = crear_usuario(usuarios.SUSPENDIDO)

        respuesta = self.reactivar(usuario)

        usuario.refresh_from_db()
        self.assertEqual(usuarios.estado_de(usuario), usuarios.ACTIVO)
        self.assertIsNone(usuario.suspendido_hasta)
        self.assertIsNone(usuario.motivo_estado)
        self.assertRedirects(respuesta, detalle(usuario))
        self.assertIn(f'Se reactivó la cuenta de {usuario.nombre}.', self.mensajes(respuesta))
        accion = UsuarioAccion.objects.get(usuario=usuario)
        self.assertEqual((accion.accion, accion.motivo, accion.realizado_por),
                         (UsuarioAccion.Accion.REACTIVAR, None, 'guillermo'))

    def test_suspendido_se_reactiva_con_motivo(self):
        usuario = crear_usuario(usuarios.SUSPENDIDO)

        self.reactivar(usuario, motivo='  Apelación aceptada  ')

        self.assertEqual(UsuarioAccion.objects.get(usuario=usuario).motivo, 'Apelación aceptada')

    def test_motivo_opcional_pero_de_10_a_300_si_se_escribe(self):
        usuario = crear_usuario(usuarios.SUSPENDIDO)
        for motivo, error in (('corto', 'al menos 10 caracteres'), ('x' * 301, 'como máximo 300')):
            with self.subTest(motivo=motivo[:12]):
                self.assertContains(self.client.post(url_accion(usuario, 'reactivar'), {'motivo': motivo}), error)
        usuario.refresh_from_db()
        self.assertEqual(usuarios.estado_de(usuario), usuarios.SUSPENDIDO)

    def test_activo_suspension_vencida_o_baja_se_rechaza_tambien_por_url(self):
        vencida = timezone.now() - timedelta(hours=1)
        for nombre, usuario in (('activo', crear_usuario()),
                                ('vencida', crear_usuario(suspendido_hasta=vencida, motivo_estado='Ya cumplida')),
                                ('baja', crear_usuario(usuarios.BAJA))):
            with self.subTest(nombre):
                antes = (usuario.activo, usuario.suspendido_hasta, usuario.motivo_estado)

                for respuesta in (self.client.get(url_accion(usuario, 'reactivar'), follow=True),
                                  self.reactivar(usuario, motivo='Intento por la URL')):
                    self.assertRedirects(respuesta, detalle(usuario))
                    self.assertTrue(any(m.startswith(f'No se puede reactivar la cuenta de {usuario.nombre}')
                                        for m in self.mensajes(respuesta)))
                usuario.refresh_from_db()
                self.assertEqual((usuario.activo, usuario.suspendido_hasta, usuario.motivo_estado), antes)
                self.assertFalse(UsuarioAccion.objects.filter(usuario=usuario).exists())


class DarBajaTests(ConAdmin):
    """H93"""
    MOTIVO = 'Suplantó la identidad de una botica'

    def confirmar(self, usuario, motivo=MOTIVO):
        return self.client.post(url_accion(usuario, 'dar_baja'), {'motivo': motivo, 'confirmado': 'si'}, follow=True)

    def test_primer_envio_solo_pide_confirmacion(self):
        usuario = crear_usuario()

        formulario = self.client.get(url_accion(usuario, 'dar_baja'))
        respuesta = self.client.post(url_accion(usuario, 'dar_baja'), {'motivo': self.MOTIVO})

        self.assertContains(formulario, 'definitiva y no se puede revertir')
        self.assertContains(respuesta, 'definitiva y no se puede revertir')
        self.assertContains(respuesta, 'Sí, dar de baja definitivamente')
        self.assertContains(respuesta, 'name="confirmado" value="si"')
        usuario.refresh_from_db()
        self.assertTrue(usuario.activo)
        self.assertFalse(UsuarioAccion.objects.exists())

    def test_activo_se_da_de_baja_al_confirmar(self):
        usuario = crear_usuario()

        respuesta = self.confirmar(usuario)

        usuario.refresh_from_db()
        self.assertEqual(usuarios.estado_de(usuario), usuarios.BAJA)
        self.assertEqual(usuario.motivo_estado, self.MOTIVO)
        self.assertRedirects(respuesta, detalle(usuario))
        self.assertIn(f'Se dio de baja la cuenta de {usuario.nombre}. Esta acción es definitiva.',
                      self.mensajes(respuesta))
        accion = UsuarioAccion.objects.get(usuario=usuario)
        self.assertEqual((accion.accion, accion.motivo, accion.realizado_por),
                         (UsuarioAccion.Accion.DAR_BAJA, self.MOTIVO, 'guillermo'))

    def test_suspendido_se_da_de_baja_cumpliendo_el_check(self):
        usuario = crear_usuario(usuarios.SUSPENDIDO)

        self.confirmar(usuario)

        usuario.refresh_from_db()
        self.assertFalse(usuario.activo)
        self.assertIsNone(usuario.suspendido_hasta)
        self.assertEqual(usuario.motivo_estado, self.MOTIVO)
        self.assertEqual(UsuarioAccion.objects.get(usuario=usuario).accion, UsuarioAccion.Accion.DAR_BAJA)

    def test_el_check_impide_una_baja_que_deje_la_suspension(self):
        """Por esto la baja borra suspendido_hasta en el mismo UPDATE."""
        usuario = crear_usuario(usuarios.SUSPENDIDO)

        with self.assertRaises(IntegrityError), transaction.atomic():
            Usuario.objects.filter(pk=usuario.pk).update(activo=False)
        usuario.refresh_from_db()
        self.assertTrue(usuario.activo)

    def test_motivo_obligatorio_de_10_a_300(self):
        usuario = crear_usuario()
        for motivo, error in (('', 'Escribe el motivo.'), ('corto', 'al menos 10 caracteres'),
                              ('x' * 301, 'como máximo 300')):
            with self.subTest(motivo=motivo[:12]):
                respuesta = self.client.post(url_accion(usuario, 'dar_baja'), {'motivo': motivo, 'confirmado': 'si'})
                self.assertContains(respuesta, error)
        usuario.refresh_from_db()
        self.assertTrue(usuario.activo)
        self.assertFalse(UsuarioAccion.objects.exists())

    def test_baja_se_rechaza_tambien_por_url(self):
        usuario = crear_usuario(usuarios.BAJA)

        for respuesta in (self.client.get(url_accion(usuario, 'dar_baja'), follow=True), self.confirmar(usuario)):
            self.assertRedirects(respuesta, detalle(usuario))
            self.assertTrue(any(m.startswith(f'No se puede dar de baja la cuenta de {usuario.nombre}')
                                for m in self.mensajes(respuesta)))
        usuario.refresh_from_db()
        self.assertEqual(usuario.motivo_estado, 'Cuenta fraudulenta confirmada')
        self.assertFalse(UsuarioAccion.objects.exists())

    def test_cuenta_de_baja_no_se_suspende_ni_reactiva(self):
        usuario = crear_usuario()
        self.confirmar(usuario)

        for accion in ('suspender', 'reactivar'):
            with self.subTest(accion):
                respuesta = self.client.get(url_accion(usuario, accion), follow=True)
                self.assertRedirects(respuesta, detalle(usuario))
        self.assertEqual(UsuarioAccion.objects.filter(usuario=usuario).count(), 1)


class BotonesDelDetalleTests(ConAdmin):
    """H91-H93: cada estado muestra solo las acciones que le corresponden."""

    def botones(self, usuario):
        contenido = self.client.get(detalle(usuario)).content.decode()
        return {accion for accion in ('suspender', 'reactivar', 'dar-baja')
                if f'{LISTA}{usuario.pk}/{accion}/' in contenido}

    def test_activo_puede_suspenderse_o_darse_de_baja(self):
        self.assertEqual(self.botones(crear_usuario()), {'suspender', 'dar-baja'})

    def test_suspendido_puede_reactivarse_o_darse_de_baja(self):
        self.assertEqual(self.botones(crear_usuario(usuarios.SUSPENDIDO)), {'reactivar', 'dar-baja'})

    def test_baja_no_tiene_botones(self):
        self.assertEqual(self.botones(crear_usuario(usuarios.BAJA)), set())


class HistorialTests(ConAdmin):
    """H94"""
    LISTA_ACCIONES = reverse('admin:boticas_usuarioaccion_changelist')

    def accion(self, usuario, accion, motivo, hace_dias, por='guillermo'):
        return UsuarioAccion.objects.create(usuario=usuario, accion=accion, motivo=motivo, realizado_por=por,
                                            fecha=timezone.now() - timedelta(days=hace_dias))

    def ids_de_la_lista(self, parametros=''):
        respuesta = self.client.get(self.LISTA_ACCIONES + parametros)
        self.assertEqual(respuesta.status_code, 200)
        return {a.pk for a in respuesta.context['cl'].result_list}

    def test_detalle_muestra_el_historial_del_mas_reciente_al_mas_antiguo(self):
        usuario = crear_usuario()
        otro = crear_usuario()
        self.accion(usuario, UsuarioAccion.Accion.SUSPENDER, 'Primera suspensión', 10, por='ana_admin')
        self.accion(usuario, UsuarioAccion.Accion.REACTIVAR, None, 5)
        self.accion(usuario, UsuarioAccion.Accion.SUSPENDER, 'Segunda suspensión', 1)
        self.accion(otro, UsuarioAccion.Accion.SUSPENDER, 'Acción de otra cuenta', 1)

        contenido = self.client.get(detalle(usuario)).content.decode()

        self.assertIn('Historial de acciones', contenido)
        self.assertIn('ana_admin', contenido)
        self.assertNotIn('Acción de otra cuenta', contenido)
        self.assertLess(contenido.index('Segunda suspensión'), contenido.index('Reactivación'))
        self.assertLess(contenido.index('Reactivación'), contenido.index('Primera suspensión'))

    def test_el_flujo_completo_queda_en_el_historial_con_el_admin(self):
        usuario = crear_usuario()
        motivo = 'Publicó reseñas ofensivas'
        self.client.post(url_accion(usuario, 'suspender'), {'hasta': en_dias(1), 'motivo': motivo})
        self.client.post(url_accion(usuario, 'reactivar'), {'motivo': ''})
        self.client.force_login(User.objects.create_superuser('otro_admin', 'otro@prueba.pe', 'clave-de-prueba'))
        self.client.post(url_accion(usuario, 'dar_baja'), {'motivo': 'Reincidió en reseñas ofensivas',
                                                           'confirmado': 'si'})

        historial = list(UsuarioAccion.objects.filter(usuario=usuario).order_by('id')
                         .values_list('accion', 'motivo', 'realizado_por'))

        self.assertEqual(historial, [('SUSPENDER', motivo, 'guillermo'), ('REACTIVAR', None, 'guillermo'),
                                     ('DAR_BAJA', 'Reincidió en reseñas ofensivas', 'otro_admin')])

    def test_listado_general_filtra_por_accion(self):
        usuario = crear_usuario()
        suspension = self.accion(usuario, UsuarioAccion.Accion.SUSPENDER, 'Motivo de prueba', 3)
        reactivacion = self.accion(usuario, UsuarioAccion.Accion.REACTIVAR, None, 2)

        self.assertEqual(self.ids_de_la_lista('?accion__exact=SUSPENDER'), {suspension.pk})
        self.assertEqual(self.ids_de_la_lista('?accion__exact=REACTIVAR'), {reactivacion.pk})
        self.assertEqual(self.ids_de_la_lista(), {suspension.pk, reactivacion.pk})

    def test_listado_general_filtra_por_fecha(self):
        usuario = crear_usuario()
        vieja = self.accion(usuario, UsuarioAccion.Accion.SUSPENDER, 'Motivo de prueba', 20)
        reciente = self.accion(usuario, UsuarioAccion.Accion.REACTIVAR, None, 2)
        desde = en_dias(-5)

        self.assertEqual(self.ids_de_la_lista(f'?desde={desde}'), {reciente.pk})
        self.assertEqual(self.ids_de_la_lista(f'?hasta={desde}'), {vieja.pk})
        self.assertEqual(self.ids_de_la_lista(f'?desde={desde}&accion__exact=SUSPENDER'), set())

    def test_listado_general_es_de_solo_lectura(self):
        accion = self.accion(crear_usuario(), UsuarioAccion.Accion.SUSPENDER, 'Motivo de prueba', 1)
        cambio = reverse('admin:boticas_usuarioaccion_change', args=[accion.pk])

        self.assertEqual(self.client.get(reverse('admin:boticas_usuarioaccion_add')).status_code, 403)
        self.assertEqual(self.client.post(cambio, {'motivo': 'Otro'}).status_code, 403)
        self.assertEqual(self.client.post(reverse('admin:boticas_usuarioaccion_delete', args=[accion.pk]),
                                          {'post': 'yes'}).status_code, 403)
        self.assertNotContains(self.client.get(cambio), 'name="_save"')
        accion.refresh_from_db()
        self.assertEqual(accion.motivo, 'Motivo de prueba')


class FichaDelUsuarioTests(ConAdmin):
    """H90 y H94: fechas en hora de Lima, etiquetas, título y botones de la ficha."""
    # 02:35 en UTC son las 21:35 del día anterior en Lima (UTC-5)
    UTC = datetime(2026, 10, 6, 2, 35, tzinfo=dt_timezone.utc)

    def test_fechas_en_hora_de_lima(self):
        usuario = crear_usuario()
        Usuario.objects.filter(pk=usuario.pk).update(creado_en=self.UTC, actualizado_en=self.UTC + timedelta(hours=1))
        contenido = self.client.get(detalle(usuario)).content.decode()
        self.assertIn('05/10/2026 21:35', contenido)
        self.assertIn('05/10/2026 22:35', contenido)
        self.assertNotIn('de octubre de 2026', contenido)

    def test_str_de_la_accion_en_hora_de_lima(self):
        accion = UsuarioAccion(usuario=crear_usuario(nombre='Carlos'), accion=UsuarioAccion.Accion.REACTIVAR,
                               fecha=self.UTC)
        self.assertTrue(str(accion).endswith('Reactivación (05/10/2026 21:35)'))

    def test_historial_sin_el_texto_del_objeto(self):
        usuario = crear_usuario(nombre='Carlos')
        accion = UsuarioAccion.objects.create(usuario=usuario, accion=UsuarioAccion.Accion.REACTIVAR,
                                              realizado_por='guillermo', fecha=self.UTC)
        respuesta = self.client.get(detalle(usuario))
        self.assertContains(respuesta, 'Reactivación')
        self.assertNotContains(respuesta, str(accion))

    def test_etiquetas_titulo_y_sin_historico(self):
        usuario = crear_usuario(nombre='Carlos')
        respuesta = self.client.get(detalle(usuario))
        self.assertContains(respuesta, 'Teléfono')
        self.assertContains(respuesta, 'Dirección')
        self.assertContains(respuesta, '<h1>Usuario</h1>', html=True)
        self.assertNotContains(respuesta, 'Vista usuario')
        self.assertNotContains(respuesta, reverse('admin:boticas_usuario_history', args=[usuario.pk]))
        self.assertContains(respuesta, url_accion(usuario, 'suspender'))
