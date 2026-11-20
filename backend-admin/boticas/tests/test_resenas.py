"""
H89: moderación de reseñas por el administrador.

Las tablas son managed = False: ConTablas las crea en el SQLite de pruebas con los CHECK copiados
de database/v5_sprint4.sql (Meta.constraints de los modelos).
"""
from itertools import count

from django.contrib.admin.models import CHANGE, LogEntry
from django.contrib.auth.models import Permission, User
from django.db import IntegrityError, transaction
from django.test import SimpleTestCase, override_settings
from django.urls import reverse
from django.utils import timezone

from boticas import resenas
from boticas.models import Botica, Resena, Usuario
from reportes.tests.base import ConTablas

_numero = count(1)
LISTA = reverse('admin:boticas_resena_changelist')
MOTIVO = 'Insulta al personal de la botica'


def crear_usuario(rol=Usuario.Rol.COMPRADOR, nombre=None):
    n = next(_numero)
    ahora = timezone.now()
    return Usuario.objects.create(nombre=nombre or f'Usuario {n}', correo=f'r{n}@prueba.pe', password_hash='x',
                                  rol=rol, creado_en=ahora, actualizado_en=ahora)


def crear_botica(nombre=None):
    n = next(_numero)
    ahora = timezone.now()
    return Botica.objects.create(usuario=crear_usuario(Usuario.Rol.DUENO_BOTICA),
                                 nombre_comercial=nombre or f'Botica {n}', ruc=f'{20000000000 + n}',
                                 razon_social=f'Razón {n}', direccion='Av. 1', distrito='Lima',
                                 estado=Botica.Estado.APROBADO, creado_en=ahora, actualizado_en=ahora)


def crear_resena(comentario='Buena atención', estrellas=4, oculta=False, botica=None, usuario=None, **extra):
    datos = {'motivo_ocultamiento': MOTIVO} if oculta else {}
    datos.update(extra)
    return Resena.objects.create(botica=botica or crear_botica(), usuario=usuario or crear_usuario(),
                                 estrellas=estrellas, comentario=comentario, oculta=oculta, **datos)


def detalle(resena):
    return reverse('admin:boticas_resena_change', args=[resena.pk])


def url_accion(resena, accion):
    return reverse(f'admin:boticas_resena_{accion}', args=[resena.pk])


class NormalizarTests(SimpleTestCase):

    def test_minusculas_y_sin_tildes(self):
        self.assertEqual(resenas.normalizar('HUEVÓN Imbécil pingüino'), 'huevon imbecil pinguino')

    @override_settings(PALABRAS_A_REVISAR=['estafa', 'huevón', 'rata'])
    def test_encuentra_palabras_con_y_sin_tildes(self):
        self.assertEqual(resenas.palabras_encontradas('Son unos ESTAFADORES'), ['estafadores'])
        self.assertEqual(resenas.palabras_encontradas('qué huevon el de la caja'), ['huevon'])
        self.assertEqual(resenas.palabras_encontradas('Qué HUEVÓN, una estafa'), ['huevon', 'estafa'])

    @override_settings(PALABRAS_A_REVISAR=['rata'])
    def test_solo_al_inicio_de_una_palabra(self):
        self.assertEqual(resenas.palabras_encontradas('Todo muy barato'), [])
        self.assertEqual(resenas.palabras_encontradas('Son unas ratas'), ['ratas'])
        self.assertEqual(resenas.palabras_encontradas(None), [])


class ConAdmin(ConTablas):
    tablas = (Usuario, Botica, Resena)

    @classmethod
    def setUpTestData(cls):
        cls.admin = User.objects.create_superuser('guillermo', 'admin@prueba.pe', 'clave-de-prueba')

    def setUp(self):
        self.client.force_login(self.admin)

    def mensajes(self, respuesta):
        return [str(m) for m in respuesta.context['messages']]

    def ids_de_la_lista(self, parametros=''):
        respuesta = self.client.get(LISTA + parametros)
        self.assertEqual(respuesta.status_code, 200)
        return {r.pk for r in respuesta.context['cl'].result_list}


class CheckMotivoTests(ConAdmin):
    """chk_resena_motivo_ocultamiento: una reseña oculta necesita su motivo."""

    def test_oculta_sin_motivo_no_se_guarda(self):
        resena = crear_resena()
        with self.assertRaises(IntegrityError), transaction.atomic():
            Resena.objects.filter(pk=resena.pk).update(oculta=True)

    def test_visible_sin_motivo_si_se_guarda(self):
        resena = crear_resena(oculta=True)
        Resena.objects.filter(pk=resena.pk).update(oculta=False, motivo_ocultamiento=None)
        resena.refresh_from_db()
        self.assertFalse(resena.oculta)


class ListaResenasTests(ConAdmin):

    def test_columnas(self):
        botica = crear_botica('Botica Salud Total')
        autor = crear_usuario(nombre='Carlos Ramírez')
        crear_resena('Me atendieron rápido y tenían todo lo que buscaba en la receta de mi mamá, '
                     'incluso el jarabe que no encontraba en otras boticas del distrito.',
                     estrellas=4, botica=botica, usuario=autor)

        respuesta = self.client.get(LISTA)

        for texto in ('Botica Salud Total', 'Carlos Ramírez', autor.correo, '★★★★☆', 'Visible',
                      'Me atendieron rápido', '…'):
            self.assertContains(respuesta, texto)
        self.assertNotContains(respuesta, 'otras boticas del distrito')

    def test_filtros_de_estado_y_estrellas(self):
        visible = crear_resena(estrellas=5)
        oculta = crear_resena(estrellas=1, oculta=True)

        self.assertEqual(self.ids_de_la_lista('?estado=VISIBLE'), {visible.pk})
        self.assertEqual(self.ids_de_la_lista('?estado=OCULTA'), {oculta.pk})
        self.assertEqual(self.ids_de_la_lista('?estrellas=1'), {oculta.pk})

    def test_filtro_de_botica_y_busqueda(self):
        farmavida = crear_botica('FarmaVida')
        de_farmavida = crear_resena('Precios justos', botica=farmavida)
        otra = crear_resena('Muy limpia')

        self.assertEqual(self.ids_de_la_lista(f'?botica__id__exact={farmavida.pk}'), {de_farmavida.pk})
        self.assertEqual(self.ids_de_la_lista('?q=farmavida'), {de_farmavida.pk})
        self.assertEqual(self.ids_de_la_lista('?q=limpia'), {otra.pk})

    @override_settings(PALABRAS_A_REVISAR=['estafa', 'huevón'])
    def test_filtro_de_palabras_con_y_sin_tildes(self):
        con_tilde = crear_resena('Qué HUEVÓN el que atiende')
        sin_tilde = crear_resena('son unos estafadores')
        normal = crear_resena('Buena atención, volveré')

        self.assertEqual(self.ids_de_la_lista('?revisar=si'), {con_tilde.pk, sin_tilde.pk})
        # Solo las encuentra: siguen visibles
        self.assertFalse(Resena.objects.filter(oculta=True).exists())
        self.assertEqual(self.ids_de_la_lista(), {con_tilde.pk, sin_tilde.pk, normal.pk})

    def test_nadie_edita_ni_borra_ni_crea(self):
        resena = crear_resena()
        editar = self.client.post(detalle(resena), {'comentario': 'Otro texto', 'estrellas': 1})
        borrar = self.client.post(reverse('admin:boticas_resena_delete', args=[resena.pk]), {'post': 'yes'})
        crear = self.client.get(reverse('admin:boticas_resena_add'))

        self.assertEqual((editar.status_code, borrar.status_code, crear.status_code), (403, 403, 403))
        resena.refresh_from_db()
        self.assertEqual((resena.comentario, resena.estrellas), ('Buena atención', 4))


class OcultarTests(ConAdmin):

    def ocultar(self, resena, motivo=MOTIVO):
        return self.client.post(url_accion(resena, 'ocultar'), {'motivo': motivo}, follow=True)

    def test_oculta_con_motivo_y_deja_historial(self):
        resena = crear_resena()

        respuesta = self.ocultar(resena, f'  {MOTIVO}  ')

        resena.refresh_from_db()
        self.assertTrue(resena.oculta)
        self.assertEqual(resena.motivo_ocultamiento, MOTIVO)
        self.assertRedirects(respuesta, detalle(resena))
        self.assertIn('Ya no aparece en la app', ' '.join(self.mensajes(respuesta)))
        registro = LogEntry.objects.get()
        self.assertEqual((registro.user, registro.action_flag, registro.object_id),
                         (self.admin, CHANGE, str(resena.pk)))
        self.assertEqual(registro.change_message, f'Ocultó la reseña. Motivo: {MOTIVO}')

    def test_motivo_de_10_a_300_caracteres(self):
        resena = crear_resena()
        for motivo in ('', 'Corto', 'x' * 301):
            respuesta = self.ocultar(resena, motivo)
            self.assertEqual(respuesta.status_code, 200)
            self.assertTrue(respuesta.context['form'].errors)
        resena.refresh_from_db()
        self.assertFalse(resena.oculta)
        self.assertFalse(LogEntry.objects.exists())

    def test_ya_oculta_no_se_vuelve_a_ocultar(self):
        resena = crear_resena(oculta=True, motivo_ocultamiento='Motivo original del ocultamiento')

        respuesta = self.ocultar(resena, 'Otro motivo distinto')

        self.assertIn('Solo se oculta una reseña visible.', ' '.join(self.mensajes(respuesta)))
        resena.refresh_from_db()
        self.assertEqual(resena.motivo_ocultamiento, 'Motivo original del ocultamiento')
        self.assertFalse(LogEntry.objects.exists())

    def test_ocultada_por_otro_admin_mientras_tanto(self):
        """El UPDATE lleva oculta = false en el WHERE: si ya cambió, no toca nada ni deja historial."""
        resena = crear_resena()
        Resena.objects.filter(pk=resena.pk).update(oculta=True, motivo_ocultamiento='Lo ocultó otro admin')

        with self.assertRaises(resenas.AccionNoPermitida):
            resenas.ocultar(resena.pk, MOTIVO, self.admin)
        resena.refresh_from_db()
        self.assertEqual(resena.motivo_ocultamiento, 'Lo ocultó otro admin')
        self.assertFalse(LogEntry.objects.exists())

    def test_get_muestra_el_formulario(self):
        resena = crear_resena('Son unos estafadores')
        respuesta = self.client.get(url_accion(resena, 'ocultar'))
        self.assertEqual(respuesta.status_code, 200)
        self.assertContains(respuesta, 'Son unos estafadores')
        self.assertContains(respuesta, 'name="motivo"')
        self.assertFalse(Resena.objects.get(pk=resena.pk).oculta)


class MostrarTests(ConAdmin):

    def test_muestra_de_nuevo_y_borra_el_motivo(self):
        resena = crear_resena(oculta=True)

        respuesta = self.client.post(url_accion(resena, 'mostrar'), follow=True)

        resena.refresh_from_db()
        self.assertFalse(resena.oculta)
        self.assertIsNone(resena.motivo_ocultamiento)
        self.assertRedirects(respuesta, detalle(resena))
        registro = LogEntry.objects.get()
        self.assertEqual((registro.user, registro.change_message), (self.admin, 'Volvió a mostrar la reseña.'))

    def test_get_solo_pide_confirmar(self):
        resena = crear_resena(oculta=True)
        respuesta = self.client.get(url_accion(resena, 'mostrar'))
        self.assertContains(respuesta, MOTIVO)
        self.assertTrue(Resena.objects.get(pk=resena.pk).oculta)

    def test_visible_no_se_puede_mostrar(self):
        resena = crear_resena()

        respuesta = self.client.post(url_accion(resena, 'mostrar'), follow=True)

        self.assertIn('Solo se vuelve a mostrar una reseña oculta.', ' '.join(self.mensajes(respuesta)))
        self.assertFalse(LogEntry.objects.exists())
        with self.assertRaises(resenas.AccionNoPermitida):
            resenas.mostrar(resena.pk, self.admin)


class BotonesYPermisosTests(ConAdmin):

    def test_boton_segun_el_estado(self):
        visible, oculta = crear_resena(), crear_resena(oculta=True)

        con_ocultar = self.client.get(detalle(visible))
        con_mostrar = self.client.get(detalle(oculta))

        self.assertContains(con_ocultar, url_accion(visible, 'ocultar'))
        self.assertNotContains(con_ocultar, url_accion(visible, 'mostrar'))
        self.assertContains(con_mostrar, url_accion(oculta, 'mostrar'))
        self.assertNotContains(con_mostrar, url_accion(oculta, 'ocultar'))

    def entrar_con(self, *codenames):
        usuario = User.objects.create_user('practicante', password='clave-de-prueba', is_staff=True)
        usuario.user_permissions.add(*Permission.objects.filter(codename__in=codenames))
        self.client.force_login(usuario)

    def test_solo_ver_no_tiene_botones_y_recibe_403(self):
        self.entrar_con('view_resena')
        visible, oculta = crear_resena(), crear_resena(oculta=True)

        self.assertEqual(self.client.get(detalle(visible)).status_code, 200)
        self.assertNotContains(self.client.get(detalle(visible)), url_accion(visible, 'ocultar'))
        self.assertNotContains(self.client.get(detalle(oculta)), url_accion(oculta, 'mostrar'))
        for respuesta in (self.client.get(url_accion(visible, 'ocultar')),
                          self.client.post(url_accion(visible, 'ocultar'), {'motivo': MOTIVO}),
                          self.client.get(url_accion(oculta, 'mostrar')),
                          self.client.post(url_accion(oculta, 'mostrar'))):
            self.assertEqual(respuesta.status_code, 403)
        self.assertEqual(set(Resena.objects.values_list('oculta', flat=True)), {False, True})
        self.assertFalse(LogEntry.objects.exists())

    def test_con_permiso_de_cambio_puede_moderar(self):
        self.entrar_con('view_resena', 'change_resena')
        resena = crear_resena()

        self.assertContains(self.client.get(detalle(resena)), url_accion(resena, 'ocultar'))
        self.client.post(url_accion(resena, 'ocultar'), {'motivo': MOTIVO})
        self.assertTrue(Resena.objects.get(pk=resena.pk).oculta)
        # Aun con el permiso, el texto no se edita
        self.assertEqual(self.client.post(detalle(resena), {'comentario': 'x'}).status_code, 403)
