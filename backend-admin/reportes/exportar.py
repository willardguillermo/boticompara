"""
H65: exportación de los reportes a Excel (.xlsx) con openpyxl.

Aquí solo se arman los libros a partir de filas ya consultadas; las vistas (reportes/admin.py y
reportes/sitio.py) eligen el periodo, hacen las consultas y devuelven el archivo. Así se prueba solo.

Cada hoja tiene: fila 1 con el título y el periodo (en hora de Lima), fila 2 con los encabezados
en negrita y debajo los datos.
"""
from io import BytesIO

from django.http import HttpResponse
from django.utils import timezone
from openpyxl import Workbook
from openpyxl.styles import Font
from openpyxl.utils import get_column_letter

TIPO_XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
FILA_ENCABEZADOS = 2
ANCHO_MAXIMO = 60

TIPOS_DE_BUSQUEDA = {'nombre': 'Nombre comercial', 'principio': 'Principio activo'}


def _llenar_hoja(hoja, titulo, rango, encabezados, filas):
    hoja.append([f'{titulo} - {rango}'])
    hoja['A1'].font = Font(bold=True, size=12)
    hoja.append(encabezados)
    for celda in hoja[FILA_ENCABEZADOS]:
        celda.font = Font(bold=True)
    for fila in filas:
        hoja.append(fila)
        # Los textos vienen de compradores y dueños: openpyxl guarda como fórmula todo texto que empieza
        # con "=" y Excel la evaluaría al abrir (p. ej. =HYPERLINK(...)). Se fuerzan a texto.
        for celda in hoja[hoja.max_row]:
            if isinstance(celda.value, str) and celda.value.startswith('='):
                celda.data_type = 's'
    # Ancho según el texto más largo de cada columna (sin contar el título, que puede desbordar)
    for indice, columna in enumerate(hoja.iter_cols(min_row=FILA_ENCABEZADOS), start=1):
        largo = max(len(str(celda.value)) for celda in columna if celda.value is not None)
        hoja.column_dimensions[get_column_letter(indice)].width = min(largo + 2, ANCHO_MAXIMO)
    # Encabezados siempre visibles al bajar
    hoja.freeze_panes = f'A{FILA_ENCABEZADOS + 1}'


def libro_mas_buscados(filas, rango):
    """Libro con una hoja "Más buscados": las mismas columnas que la tabla del reporte."""
    libro = Workbook()
    hoja = libro.active
    hoja.title = 'Más buscados'
    _llenar_hoja(
        hoja, 'Medicamentos más buscados', rango,
        ['#', 'Texto buscado', 'Tipo', 'Búsquedas', 'Promedio de resultados', 'Sin resultados'],
        [
            [numero, fila['texto_normalizado'], TIPOS_DE_BUSQUEDA.get(fila['tipo'], fila['tipo']),
             fila['busquedas'], round(float(fila['promedio_resultados'] or 0), 1), fila['sin_resultados']]
            for numero, fila in enumerate(filas, start=1)
        ],
    )
    return libro


def libro_panel(indicadores, comparados, rango):
    """Libro del panel principal: hojas "Indicadores" y "Más comparados"."""
    libro = Workbook()
    hoja = libro.active
    hoja.title = 'Indicadores'
    _llenar_hoja(hoja, 'Panel principal', rango, ['Indicador', 'Valor'], [
        ['Compradores activos', indicadores['compradores_activos']],
        ['Dueños de botica activos', indicadores['duenos_activos']],
        ['Boticas pendientes', indicadores['boticas_pendientes']],
        ['Boticas aprobadas', indicadores['boticas_aprobadas']],
        ['Boticas rechazadas', indicadores['boticas_rechazadas']],
        ['Productos activos', indicadores['productos_activos']],
        ['Medicamentos del catálogo', indicadores['medicamentos']],
        ['Medicamentos ocultos', indicadores['medicamentos_ocultos']],
        # Solo estos dos dependen del periodo; el resto es el total actual
        ['Búsquedas del periodo', indicadores['busquedas']],
        ['Comparaciones del periodo', indicadores['comparaciones']],
    ])
    _llenar_hoja(
        libro.create_sheet('Más comparados'), 'Productos más comparados', rango,
        ['#', 'Nombre comercial', 'Presentación', 'Botica', 'Comparaciones'],
        [
            [numero, fila['nombre_comercial'], fila['presentacion'], fila['botica'], fila['comparaciones']]
            for numero, fila in enumerate(comparados, start=1)
        ],
    )
    return libro


def nombre_de_archivo(reporte, hoy=None):
    """boticompara-<reporte>-AAAA-MM-DD.xlsx con la fecha de hoy en Lima."""
    hoy = hoy or timezone.localdate()
    return f'boticompara-{reporte}-{hoy:%Y-%m-%d}.xlsx'


def respuesta_excel(libro, reporte):
    """Descarga del libro como archivo .xlsx."""
    contenido = BytesIO()
    libro.save(contenido)
    respuesta = HttpResponse(contenido.getvalue(), content_type=TIPO_XLSX)
    respuesta['Content-Disposition'] = f'attachment; filename="{nombre_de_archivo(reporte)}"'
    return respuesta
