from django import forms

from .models import Categoria, Medicamento, MedicamentoGrupo, PrincipioActivo


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
