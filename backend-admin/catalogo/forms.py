from django import forms

from .models import Categoria, GrupoEquivalencia, Medicamento, MedicamentoGrupo, PrincipioActivo


class NombreUnicoForm(forms.ModelForm):
    """Valida antes que la BD el nombre único sin distinguir mayúsculas (índice sobre lower(nombre))."""

    def clean_nombre(self):
        nombre = self.cleaned_data['nombre'].strip()
        repetido = (self._meta.model.objects.filter(nombre__iexact=nombre)
                    .exclude(pk=self.instance.pk).first())
        if repetido:
            raise forms.ValidationError(
                f'Ya existe un registro con ese nombre: «{repetido.nombre}» '
                '(no se distinguen mayúsculas de minúsculas).'
            )
        return nombre


class CategoriaForm(NombreUnicoForm):
    class Meta:
        model = Categoria
        fields = ('nombre',)


class PrincipioActivoForm(NombreUnicoForm):
    class Meta:
        model = PrincipioActivo
        fields = ('nombre', 'descripcion')
        widgets = {
            'descripcion': forms.Textarea(attrs={'rows': 3}),
        }


class MedicamentoForm(forms.ModelForm):
    """Valida el registro sanitario único y que no se cambie el principio activo de un medicamento agrupado."""

    class Meta:
        model = Medicamento
        fields = ('nombre_comercial', 'presentacion', 'concentracion', 'principio_activo', 'categoria',
                  'registro_sanitario', 'fecha_vencimiento_registro', 'oculto')
        help_texts = {
            'registro_sanitario': 'Opcional. Si se indica, no puede repetirse en otro medicamento.',
        }

    def clean_registro_sanitario(self):
        # Vacío se guarda como null: el índice único de la BD solo aplica cuando existe
        registro = (self.cleaned_data.get('registro_sanitario') or '').strip() or None
        if registro:
            repetido = (Medicamento.objects.filter(registro_sanitario=registro)
                        .exclude(pk=self.instance.pk).first())
            if repetido:
                raise forms.ValidationError(f'El registro sanitario {registro} ya pertenece a «{repetido}».')
        return registro

    def clean_principio_activo(self):
        principio = self.cleaned_data['principio_activo']
        # self.instance aún tiene el valor guardado: Django lo actualiza después de clean()
        if self.instance.pk and principio.pk != self.instance.principio_activo_id:
            # Las FK compuestas de medicamento_grupo rechazarían el cambio en la BD
            membresia = (MedicamentoGrupo.objects.filter(medicamento_id=self.instance.pk)
                         .select_related('grupo').first())
            if membresia:
                raise forms.ValidationError(
                    f'Este medicamento está en el grupo de equivalencia «{membresia.grupo}». '
                    'Primero sácalo del grupo para cambiar su principio activo.'
                )
        return principio


class GrupoEquivalenciaForm(forms.ModelForm):
    """No deja cambiar el principio activo de un grupo que ya tiene medicamentos."""

    class Meta:
        model = GrupoEquivalencia
        fields = ('nombre', 'principio_activo')

    def clean_principio_activo(self):
        principio = self.cleaned_data['principio_activo']
        if self.instance.pk and principio.pk != self.instance.principio_activo_id:
            # La FK compuesta (grupo_id, principio_activo_id) rechazaría el cambio en la BD
            cantidad = MedicamentoGrupo.objects.filter(grupo_id=self.instance.pk).count()
            if cantidad:
                raise forms.ValidationError(
                    f'Este grupo tiene {cantidad} medicamento(s) de {self.instance.principio_activo}. '
                    'Primero quítalos del grupo y guarda; luego cambia el principio activo.'
                )
        return principio


class MiembroGrupoForm(forms.ModelForm):
    """Fila para agregar un medicamento a un grupo. El formset le pasa el grupo que se está editando."""

    class Meta:
        model = MedicamentoGrupo
        fields = ('medicamento',)

    def __init__(self, *args, grupo=None, **kwargs):
        super().__init__(*args, **kwargs)
        self.grupo = grupo

    def clean_medicamento(self):
        medicamento = self.cleaned_data['medicamento']
        # Si el formulario del grupo no es válido puede no haber principio activo con qué comparar
        if self.grupo is not None and self.grupo.principio_activo_id:
            if medicamento.principio_activo_id != self.grupo.principio_activo_id:
                raise forms.ValidationError(
                    f'«{medicamento}» tiene principio activo {medicamento.principio_activo}; '
                    f'este grupo es de {self.grupo.principio_activo}.'
                )
        membresia = (MedicamentoGrupo.objects.filter(medicamento=medicamento)
                     .select_related('grupo').first())
        if membresia:
            if self.grupo is not None and membresia.grupo_id == self.grupo.pk:
                raise forms.ValidationError(f'«{medicamento}» ya está en este grupo.')
            raise forms.ValidationError(
                f'«{medicamento}» ya está en el grupo «{membresia.grupo}». '
                'Un medicamento solo puede estar en un grupo: primero sácalo de ese.'
            )
        return medicamento
