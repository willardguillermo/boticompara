"""
H46: aplica en la BD las filas válidas que devuelve lector.leer().

- Clave: el registro sanitario (sin distinguir mayúsculas). Si ya existe se ACTUALIZA
  el medicamento; si no, se CREA sin categoría (el formato de DIGEMID no la trae).
  Al actualizar no se tocan la categoría ni el campo oculto.
- El principio activo se busca sin distinguir mayúsculas ni tildes y se crea si falta.
- Cada fila va en su propio transaction.atomic(): un error en una fila se informa y
  las demás siguen.
- Con simulacion=True todo corre dentro de una transacción que se deshace al final.
"""
from dataclasses import dataclass, field

from django.db import DatabaseError, transaction

from catalogo.models import Medicamento, MedicamentoGrupo, PrincipioActivo

from . import lector

CAMPOS_ACTUALIZABLES = ('nombre_comercial', 'presentacion', 'concentracion', 'forma_farmaceutica', 'titular',
                        'codigo_digemid', 'fecha_vencimiento_registro', 'principio_activo')


class ErrorFila(Exception):
    """La fila no se puede aplicar; el mensaje es el motivo que ve el administrador."""


@dataclass
class Resultado:
    creados: int = 0
    actualizados: int = 0
    # (número de fila, registro sanitario o '', motivo), ordenados por fila
    errores: list[tuple[int, str, str]] = field(default_factory=list)
    principios_creados: list[str] = field(default_factory=list)
    filas_leidas: int = 0
    simulacion: bool = False

    @property
    def resumen(self):
        texto = (f'{self.creados} creado(s), {self.actualizados} actualizado(s), '
                 f'{len(self.errores)} fila(s) con error, '
                 f'{len(self.principios_creados)} principio(s) activo(s) nuevo(s).')
        if self.simulacion:
            texto += ' SIMULACIÓN: no se guardó nada.'
        return texto


def importar_archivo(archivo, nombre, simulacion=False):
    """Lee y aplica el archivo. Lanza lector.ArchivoInvalido si no se puede leer."""
    return importar(lector.leer(archivo, nombre), simulacion=simulacion)


def importar(lectura, simulacion=False):
    resultado = Resultado(errores=list(lectura.errores), filas_leidas=lectura.filas_leidas,
                          simulacion=simulacion)
    if simulacion:
        with transaction.atomic():
            _Importacion(resultado).aplicar(lectura.filas)
            transaction.set_rollback(True)
    else:
        _Importacion(resultado).aplicar(lectura.filas)
    resultado.errores.sort(key=lambda error: error[0])
    return resultado


class _Importacion:
    def __init__(self, resultado):
        self.resultado = resultado
        # Se cargan una sola vez para no consultar la BD por cada fila
        self.principios = {}
        for principio in PrincipioActivo.objects.order_by('id'):
            self.principios.setdefault(lector.normalizar(principio.nombre), principio)
        self.medicamentos = {}
        for medicamento in Medicamento.objects.filter(registro_sanitario__isnull=False).order_by('id'):
            self.medicamentos.setdefault(medicamento.registro_sanitario.upper(), medicamento)
        self.grupos = dict(MedicamentoGrupo.objects.values_list('medicamento_id', 'grupo__nombre'))

    def aplicar(self, filas):
        for fila in filas:
            principio_nuevo = None
            try:
                with transaction.atomic():
                    principio, principio_nuevo = self._principio(fila)
                    existente = self.medicamentos.get(fila.registro)
                    if existente:
                        self._actualizar(existente, fila, principio)
                    else:
                        self._crear(fila, principio)
            except ErrorFila as exc:
                self._error(fila, str(exc), principio_nuevo)
            except DatabaseError as exc:
                detalle = str(exc).strip().splitlines()[0] if str(exc).strip() else exc.__class__.__name__
                self._error(fila, f'la base de datos rechazó la fila: {detalle}', principio_nuevo)
            else:
                if existente:
                    self.resultado.actualizados += 1
                else:
                    self.resultado.creados += 1
                if principio_nuevo:
                    self.resultado.principios_creados.append(principio_nuevo.nombre)

    def _principio(self, fila):
        """(principio, el principio si se creó en esta fila o None)."""
        clave = lector.normalizar(fila.principio)
        principio = self.principios.get(clave)
        existente = self.medicamentos.get(fila.registro)
        # Las FK compuestas de medicamento_grupo rechazarían el cambio de principio activo
        if existente and existente.pk in self.grupos and (
                principio is None or principio.pk != existente.principio_activo_id):
            raise ErrorFila(f'está en el grupo {self.grupos[existente.pk]} con otro principio activo; '
                            'sácalo del grupo primero')
        if principio:
            return principio, None
        nombre = fila.principio.title() if fila.principio.isupper() else fila.principio
        principio = PrincipioActivo.objects.create(nombre=nombre)
        self.principios[clave] = principio
        return principio, principio

    def _valores(self, fila, principio):
        return {
            'nombre_comercial': fila.nombre,
            'presentacion': fila.presentacion,
            'concentracion': fila.concentracion,
            'forma_farmaceutica': fila.forma,
            'titular': fila.titular,
            'codigo_digemid': fila.codigo,
            'fecha_vencimiento_registro': fila.fecha,
            'principio_activo': principio,
        }

    def _actualizar(self, medicamento, fila, principio):
        for campo, valor in self._valores(fila, principio).items():
            setattr(medicamento, campo, valor)
        medicamento.save(update_fields=CAMPOS_ACTUALIZABLES)

    def _crear(self, fila, principio):
        medicamento = Medicamento.objects.create(registro_sanitario=fila.registro,
                                                 **self._valores(fila, principio))
        self.medicamentos[fila.registro] = medicamento

    def _error(self, fila, motivo, principio_nuevo):
        # El savepoint de la fila deshizo también el principio activo que se había creado
        if principio_nuevo:
            self.principios.pop(lector.normalizar(principio_nuevo.nombre), None)
        self.resultado.errores.append((fila.numero_fila, fila.registro, motivo))
