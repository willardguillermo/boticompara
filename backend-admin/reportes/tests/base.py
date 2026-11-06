"""
Base de las pruebas de reportes: las tablas son managed = False, así que Django no las crea en la
base de pruebas. Se crean antes de abrir la transacción de TestCase y se borran al terminar.
"""
from django.db import connection
from django.test import TestCase

from boticas.models import Botica, Producto, Usuario
from catalogo.models import Categoria, ConsultaComparacion, Medicamento, PrincipioActivo
from reportes.models import Busqueda

# En orden de dependencias (las claves foráneas apuntan a tablas anteriores)
TABLAS_DEL_PANEL = (Usuario, Botica, PrincipioActivo, Categoria, Medicamento, Producto,
                    ConsultaComparacion, Busqueda)


class ConTablas(TestCase):
    tablas = TABLAS_DEL_PANEL

    @classmethod
    def setUpClass(cls):
        with connection.schema_editor() as editor:
            for modelo in cls.tablas:
                editor.create_model(modelo)
        super().setUpClass()

    @classmethod
    def tearDownClass(cls):
        super().tearDownClass()
        with connection.schema_editor() as editor:
            for modelo in reversed(cls.tablas):
                editor.delete_model(modelo)
