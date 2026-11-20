"""
SEC-14: límite de intentos en el login del admin (django-axes con AxesCacheHandler).

5 fallos con el mismo usuario desde la misma IP bloquean 15 minutos; un login correcto reinicia el
contador. El paso del tiempo se simula con un reloj falso en la caché en memoria (no se espera).
"""
import logging
import time
from unittest import mock

from django.contrib.auth.models import User
from django.core.cache import cache
from django.db import connection
from django.db.migrations.loader import MigrationLoader
from django.test import TestCase

LOGIN = '/admin/login/'
CLAVE = 'Clave-Correcta-2026'
IP = '10.0.0.1'


class Reloj:
    """Reemplaza time.time() en la caché en memoria de Django para adelantar el tiempo."""

    def __init__(self):
        self.ahora = time.time()

    def time(self):
        return self.ahora

    def adelantar(self, minutos=0, segundos=0):
        self.ahora += minutos * 60 + segundos


class BloqueoDelLoginTests(TestCase):

    def setUp(self):
        # axes registra cada fallo con logging.warning; en las pruebas solo ensucia la salida
        logging.getLogger('axes').disabled = True
        self.addCleanup(setattr, logging.getLogger('axes'), 'disabled', False)
        cache.clear()
        self.addCleanup(cache.clear)
        self.reloj = Reloj()
        for modulo in ('django.core.cache.backends.base.time', 'django.core.cache.backends.locmem.time'):
            parche = mock.patch(modulo, self.reloj)
            parche.start()
            self.addCleanup(parche.stop)
        User.objects.create_superuser('admin', 'admin@example.com', CLAVE)
        User.objects.create_superuser('otro', 'otro@example.com', CLAVE)

    def entrar(self, usuario='admin', clave=CLAVE, ip=IP):
        return self.client.post(LOGIN, {'username': usuario, 'password': clave, 'next': '/admin/'},
                                REMOTE_ADDR=ip)

    def fallar(self, veces, usuario='admin', ip=IP):
        for _ in range(veces):
            respuesta = self.entrar(usuario, 'clave-incorrecta', ip)
        return respuesta

    def assertBloqueado(self, respuesta):
        self.assertEqual(respuesta.status_code, 429)
        self.assertTemplateUsed(respuesta, 'admin/reportes/bloqueo_login.html')
        self.assertContains(respuesta, 'Demasiados intentos fallidos', status_code=429)
        self.assertContains(respuesta, 'Espera 15 minutos', status_code=429)
        self.assertEqual(respuesta['Retry-After'], '900')

    def assertEntro(self, respuesta):
        self.assertRedirects(respuesta, '/admin/', fetch_redirect_response=False)

    def test_cuatro_fallos_aun_muestran_el_login(self):
        respuesta = self.fallar(4)
        self.assertEqual(respuesta.status_code, 200)
        self.assertTemplateUsed(respuesta, 'admin/login.html')

    def test_el_quinto_fallo_bloquea_incluso_con_la_clave_correcta(self):
        self.assertBloqueado(self.fallar(5))
        self.assertBloqueado(self.entrar())

    def test_la_pagina_es_la_misma_si_el_usuario_no_existe(self):
        existente = self.fallar(5, usuario='admin')
        inexistente = self.fallar(5, usuario='nadie')
        self.assertBloqueado(inexistente)
        self.assertNotContains(inexistente, 'nadie', status_code=429)
        self.assertEqual(existente.content, inexistente.content)

    def test_otro_usuario_desde_la_misma_ip_no_queda_bloqueado(self):
        self.fallar(5, usuario='admin')
        self.assertEntro(self.entrar(usuario='otro'))

    def test_el_mismo_usuario_desde_otra_ip_no_queda_bloqueado(self):
        self.fallar(5, usuario='admin')
        self.assertEntro(self.entrar(ip='10.0.0.2'))

    def test_el_login_correcto_reinicia_el_contador(self):
        self.fallar(4)
        self.assertEntro(self.entrar())
        self.client.logout()
        # Sin el reinicio, el primero de estos fallos sería el quinto y bloquearía
        self.assertEqual(self.fallar(4).status_code, 200)
        self.assertBloqueado(self.fallar(1))

    def test_antes_de_15_minutos_sigue_bloqueado(self):
        self.fallar(5)
        self.reloj.adelantar(minutos=14, segundos=59)
        self.assertBloqueado(self.entrar())

    def test_tras_15_minutos_vuelve_a_dejar_entrar(self):
        self.fallar(5)
        self.reloj.adelantar(minutos=15, segundos=1)
        self.assertEntro(self.entrar())

    def test_un_intento_durante_el_bloqueo_reinicia_la_espera(self):
        self.fallar(5)
        self.reloj.adelantar(minutos=10)
        self.assertBloqueado(self.entrar())
        self.reloj.adelantar(minutos=10)  # 20 desde el bloqueo, pero 10 desde el último intento
        self.assertBloqueado(self.entrar())


class SinTablasDeAxesTests(TestCase):
    """El handler de caché no usa la base: migrate no debe crear tablas de axes en Supabase."""

    def test_axes_no_tiene_migraciones(self):
        loader = MigrationLoader(connection, ignore_no_migrations=True)
        self.assertIn('axes', loader.unmigrated_apps)
        self.assertFalse([nodo for nodo in loader.graph.nodes if nodo[0] == 'axes'])
