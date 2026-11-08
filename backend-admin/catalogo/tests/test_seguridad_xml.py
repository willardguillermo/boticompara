"""
Escaneo de seguridad (Sprint 3): el Excel de DIGEMID se lee con openpyxl, que solo se protege de las
"XML bombs" (entidades que se expanden hasta agotar la memoria) si defusedxml está instalado.
"""
from django.test import SimpleTestCase


class DefusedXmlTests(SimpleTestCase):

    def test_openpyxl_usa_defusedxml(self):
        import openpyxl.xml
        self.assertTrue(openpyxl.xml.DEFUSEDXML, 'Instala defusedxml (requirements.txt)')
