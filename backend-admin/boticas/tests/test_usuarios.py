"""
H90-H94: gestión de usuarios por el administrador.

Las tablas son managed = False: ConTablas las crea en el SQLite de pruebas con los CHECK copiados
de database/schema.sql (Meta.constraints de los modelos).
"""
from datetime import timedelta
from itertools import count

from django.contrib.auth.models import Permission, User
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


class BotonesDelDetalleTests(ConAdmin):
    """H91-H93: cada estado muestra solo las acciones que le corresponden."""

    def botones(self, usuario):
        contenido = self.client.get(detalle(usuario)).content.decode()
        return {accion for accion in ('suspender', 'reactivar', 'dar-baja')
                if f'{LISTA}{usuario.pk}/{accion}/' in contenido}

    def test_activo_puede_suspenderse(self):
        self.assertIn('suspender', self.botones(crear_usuario()))

    def test_suspendido_no_puede_suspenderse(self):
        self.assertNotIn('suspender', self.botones(crear_usuario(usuarios.SUSPENDIDO)))

    def test_baja_no_tiene_botones(self):
        self.assertEqual(self.botones(crear_usuario(usuarios.BAJA)), set())
