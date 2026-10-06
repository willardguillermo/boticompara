from django import forms

from .models import Botica


class BoticaRevisionForm(forms.ModelForm):
    """Formulario de revisión: el motivo es obligatorio al rechazar."""

    class Meta:
        model = Botica
        fields = ('estado', 'motivo_rechazo')
        widgets = {
            'motivo_rechazo': forms.Textarea(attrs={'rows': 3}),
        }
        help_texts = {
            'motivo_rechazo': 'Obligatorio si la botica se rechaza. El dueño lo verá en la web.',
        }

    def clean(self):
        datos = super().clean()
        estado = datos.get('estado')
        motivo = (datos.get('motivo_rechazo') or '').strip()

        if estado == Botica.Estado.RECHAZADO:
            if not motivo:
                self.add_error('motivo_rechazo', 'Indica el motivo del rechazo.')
            else:
                datos['motivo_rechazo'] = motivo
        else:
            # El motivo solo tiene sentido en una botica rechazada
            datos['motivo_rechazo'] = None
        return datos
