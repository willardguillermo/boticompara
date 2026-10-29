"""
H46: pruebas del lector del formato DIGEMID con los archivos de catalogo/muestras/.

SimpleTestCase (sin BD): los modelos son managed = False y Django no podría crear sus
tablas en una base de pruebas; el lector no usa la BD.
"""
import io
from datetime import date
from pathlib import Path

from django.test import SimpleTestCase

from catalogo.digemid.lector import ArchivoInvalido, leer, normalizar

MUESTRAS = Path(__file__).resolve().parent.parent / 'muestras'
XLSX = MUESTRAS / 'catalogo-digemid-muestra.xlsx'
CSV = MUESTRAS / 'catalogo-digemid-muestra.csv'

ENCABEZADO = 'Código;Nombre Producto;Principio Activo;Concentración;Forma Farmacéutica;Presentación;' \
             'Reg Sanitario;Fec Expiración;Titular'


def leer_csv(texto, codificacion='utf-8'):
    return leer(io.BytesIO(texto.encode(codificacion)), 'catalogo.csv')


class LectorXlsxTests(SimpleTestCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.lectura = leer(XLSX, XLSX.name)
        cls.por_fila = {f.numero_fila: f for f in cls.lectura.filas}

    def test_cuenta_filas_validas_y_errores(self):
        self.assertEqual(self.lectura.filas_leidas, 50)
        self.assertEqual(len(self.lectura.filas), 44)
        self.assertEqual(len(self.lectura.errores), 6)

    def test_errores_con_fila_y_motivo(self):
        errores = {fila: (registro, motivo) for fila, registro, motivo in self.lectura.errores}
        self.assertEqual(list(errores), [13, 21, 29, 36, 46, 51])
        self.assertEqual(errores[13], ('EN-05012', 'registro sanitario EN-05012 repetido (ya está en la fila 6)'))
        self.assertEqual(errores[21], ('EN-03981', 'fecha de expiración inválida (31/02/2029 no existe)'))
        self.assertEqual(errores[29], ('EN-05400', 'falta el Nombre Producto (obligatorio)'))
        self.assertEqual(errores[36], ('EN-04612', 'falta el Principio Activo (obligatorio)'))
        self.assertEqual(errores[46], ('EN-03760', 'falta la Presentación (obligatoria)'))
        self.assertEqual(errores[51], ('', 'falta el Reg Sanitario (obligatorio)'))

    def test_codigo_se_lee_como_texto(self):
        self.assertEqual(self.por_fila[2].codigo, '00101')
        # Fila 28: sin Código (es opcional)
        self.assertIsNone(self.por_fila[28].codigo)

    def test_fecha_de_excel_y_fecha_en_texto(self):
        self.assertEqual(self.por_fila[2].fecha, date(2028, 12, 31))
        # Fila 18: la fecha viene escrita como texto dd/mm/aaaa
        self.assertEqual(self.por_fila[18].fecha, date(2030, 8, 31))

    def test_la_primera_fila_de_un_registro_repetido_vale(self):
        self.assertEqual(self.por_fila[6].registro, 'EN-05012')
        self.assertEqual(self.por_fila[6].nombre, 'Ibuprofeno Farmindustria')

    def test_titular_y_forma(self):
        self.assertEqual(self.por_fila[2].titular, 'Haleon Perú S.R.L.')
        self.assertEqual(self.por_fila[2].forma, 'Tableta recubierta')


class LectorCsvTests(SimpleTestCase):
    def test_muestra_cp1252_con_punto_y_coma(self):
        lectura = leer(CSV, CSV.name)
        self.assertEqual(lectura.filas_leidas, 10)
        self.assertEqual(len(lectura.filas), 9)
        self.assertEqual(lectura.errores, [(7, 'EN-03981', 'fecha de expiración inválida (31/02/2029 no existe)')])
        nombres = [f.nombre for f in lectura.filas]
        self.assertIn('Losartán Farmindustria', nombres)
        self.assertEqual(lectura.filas[0].codigo, '04521')
        self.assertEqual(lectura.filas[0].fecha, date(2030, 8, 15))

    def test_encabezados_en_mayusculas_sin_tildes_y_con_puntos(self):
        texto = ('CODIGO,NOMBRE  PRODUCTO,principio activo,CONCENTRACION,FORMA FARMACEUTICA,PRESENTACION,'
                 'Reg. Sanitario,Fec. Expiración,TITULAR\n'
                 '00200,  Panadol   Forte ,Paracetamol,1 g,Tableta,Caja x 10,en-00200,2029-01-15,Haleon\n')
        lectura = leer_csv(texto)
        self.assertEqual(lectura.errores, [])
        fila = lectura.filas[0]
        self.assertEqual(fila.numero_fila, 2)
        self.assertEqual(fila.nombre, 'Panadol Forte')
        self.assertEqual(fila.registro, 'EN-00200')
        self.assertEqual(fila.fecha, date(2029, 1, 15))

    def test_falta_columna_obligatoria_no_importa_nada(self):
        with self.assertRaisesMessage(ArchivoInvalido, 'Presentación'):
            leer_csv('Nombre Producto;Principio Activo;Reg Sanitario;Fec Expiración\nA;B;C;\n')

    def test_codigo_y_titular_pueden_faltar_como_columnas(self):
        lectura = leer_csv('Nombre Producto;Principio Activo;Concentración;Forma Farmacéutica;Presentación;'
                           'Reg Sanitario;Fec Expiración\nA;B;;;Caja;R-1;\n')
        self.assertEqual(lectura.errores, [])
        self.assertIsNone(lectura.filas[0].codigo)
        self.assertIsNone(lectura.filas[0].fecha)

    def test_filas_vacias_se_ignoran(self):
        lectura = leer_csv(ENCABEZADO + '\n;;;;;;;;\n\n001;A;B;;;Caja;R-1;;\n')
        self.assertEqual(lectura.filas_leidas, 1)
        self.assertEqual(lectura.filas[0].numero_fila, 4)

    def test_longitud_maxima(self):
        lectura = leer_csv(ENCABEZADO + '\n;' + 'x' * 151 + ';B;;;Caja;R-1;;\n')
        self.assertEqual(lectura.errores, [(2, 'R-1', 'Nombre Producto tiene 151 caracteres (máximo 150)')])

    def test_fecha_con_formato_desconocido(self):
        lectura = leer_csv(ENCABEZADO + '\n;A;B;;;Caja;R-1;mañana;\n')
        self.assertEqual(lectura.errores,
                         [(2, 'R-1', 'Fec Expiración «mañana» no tiene el formato dd/mm/aaaa ni aaaa-mm-dd')])

    def test_extension_no_soportada(self):
        with self.assertRaises(ArchivoInvalido):
            leer(io.BytesIO(b'x'), 'catalogo.pdf')


class NormalizarTests(SimpleTestCase):
    def test_sin_mayusculas_ni_tildes(self):
        self.assertEqual(normalizar('LOSARTAN'), normalizar('Losartán'))
        self.assertEqual(normalizar('ACIDO ACETILSALICILICO'), normalizar('Ácido Acetilsalicílico'))
        self.assertEqual(normalizar('  Metamizol   Sódico '), normalizar('metamizol sodico'))
        self.assertNotEqual(normalizar('Paracetamol'), normalizar('Paracetamol + Cafeína'))
