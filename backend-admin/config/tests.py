"""
Escaneo de seguridad (Sprint 3): DEBUG, SECRET_KEY y ALLOWED_HOSTS salen del entorno sin valores inseguros.

settings.py se evalúa al importarse, así que cada caso lo importa en un proceso aparte con su propio entorno.
Las variables se fijan explícitamente: tienen prioridad sobre el .env de la raíz (load_dotenv no las pisa).
"""
import os
import subprocess
import sys
from pathlib import Path

from django.test import SimpleTestCase

CARPETA = Path(__file__).resolve().parent.parent
LEER = 'import config.settings as s; print(s.DEBUG, ",".join(s.ALLOWED_HOSTS), len(s.SECRET_KEY))'


def importar_settings(**variables):
    entorno = {k: v for k, v in os.environ.items() if not k.startswith('DJANGO_')}
    entorno.update(variables)
    return subprocess.run([sys.executable, '-c', LEER], cwd=CARPETA, env=entorno,
                          capture_output=True, text=True, timeout=60)


class AjustesDeSeguridadTests(SimpleTestCase):

    def test_sin_debug_y_sin_secret_key_no_arranca(self):
        r = importar_settings(DJANGO_DEBUG='False', DJANGO_SECRET_KEY='')
        self.assertNotEqual(r.returncode, 0)
        self.assertIn('Falta DJANGO_SECRET_KEY', r.stderr)

    def test_debug_es_false_si_no_se_indica(self):
        r = importar_settings(DJANGO_DEBUG='', DJANGO_SECRET_KEY='x' * 50)
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertTrue(r.stdout.startswith('False '))

    def test_con_debug_usa_la_clave_de_desarrollo(self):
        r = importar_settings(DJANGO_DEBUG='True', DJANGO_SECRET_KEY='')
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertTrue(r.stdout.startswith('True '))

    def test_allowed_hosts_desde_el_entorno(self):
        r = importar_settings(DJANGO_DEBUG='False', DJANGO_SECRET_KEY='x' * 50,
                              DJANGO_ALLOWED_HOSTS=' admin.boticompara.pe , localhost ')
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertEqual(r.stdout.split()[1], 'admin.boticompara.pe,localhost')
