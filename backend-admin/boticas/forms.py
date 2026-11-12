from django import forms

from . import usuarios
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


def campo_motivo(requerido, ayuda):
    """H91-H93: motivo de 10 a 300 caracteres (sin contar espacios al inicio y al final)."""
    return forms.CharField(
        label='Motivo', required=requerido, min_length=usuarios.MOTIVO_MIN, max_length=usuarios.MOTIVO_MAX,
        widget=forms.Textarea(attrs={'rows': 4, 'cols': 60}), help_text=ayuda,
        error_messages={
            'required': 'Escribe el motivo.',
            'min_length': 'El motivo debe tener al menos %(limit_value)d caracteres (tiene %(show_value)d).',
            'max_length': 'El motivo puede tener como máximo %(limit_value)d caracteres (tiene %(show_value)d).',
        },
    )


class SuspenderForm(forms.Form):
    """H91: "hasta" es el último día de la suspensión, de mañana a 90 días desde hoy."""
    hasta = forms.DateField(
        label='Hasta',
        # El <input type="date"> envía aaaa-mm-dd; también se acepta dd/mm/aaaa escrito a mano
        input_formats=['%Y-%m-%d', '%d/%m/%Y'],
        widget=forms.DateInput(attrs={'type': 'date'}, format='%Y-%m-%d'),
        error_messages={'required': 'Elige hasta qué día dura la suspensión.',
                        'invalid': 'Escribe una fecha válida (dd/mm/aaaa).'},
    )
    motivo = campo_motivo(True, f'De {usuarios.MOTIVO_MIN} a {usuarios.MOTIVO_MAX} caracteres. '
                                'Queda en el historial de la cuenta.')

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.minimo, self.maximo = usuarios.limites_suspension()
        self.fields['hasta'].widget.attrs.update(min=f'{self.minimo:%Y-%m-%d}', max=f'{self.maximo:%Y-%m-%d}')
        self.fields['hasta'].help_text = (
            f'La cuenta queda suspendida hasta el final de ese día. Del {self.minimo:%d/%m/%Y} '
            f'al {self.maximo:%d/%m/%Y} (máximo {usuarios.MAX_DIAS_SUSPENSION} días).')

    def clean_hasta(self):
        hasta = self.cleaned_data['hasta']
        if hasta < self.minimo:
            raise forms.ValidationError(
                f'La fecha debe ser futura: elige desde el {self.minimo:%d/%m/%Y}.')
        if hasta > self.maximo:
            raise forms.ValidationError(
                f'La suspensión puede durar como máximo {usuarios.MAX_DIAS_SUSPENSION} días: '
                f'elige hasta el {self.maximo:%d/%m/%Y}.')
        return hasta


class ReactivarForm(forms.Form):
    """H92: el motivo es opcional; si se escribe, de 10 a 300 caracteres."""
    motivo = campo_motivo(False, f'Opcional. Si lo escribes, de {usuarios.MOTIVO_MIN} a '
                                 f'{usuarios.MOTIVO_MAX} caracteres. Queda en el historial de la cuenta.')
