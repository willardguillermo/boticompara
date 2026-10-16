from django.contrib import admin
from django.db.models import Count

from .forms import CategoriaForm, MedicamentoForm, PrincipioActivoForm
from .models import Categoria, Medicamento, PrincipioActivo


class ConConteoMedicamentosAdmin(admin.ModelAdmin):
    """Agrega la columna con el número de medicamentos del catálogo que usan el registro."""

    def get_queryset(self, request):
        return super().get_queryset(request).annotate(num_medicamentos=Count('medicamentos'))

    @admin.display(description='medicamentos', ordering='num_medicamentos')
    def numero_medicamentos(self, obj):
        return obj.num_medicamentos


@admin.register(Categoria)
class CategoriaAdmin(ConConteoMedicamentosAdmin):
    form = CategoriaForm
    list_display = ('nombre', 'numero_medicamentos')
    search_fields = ('nombre',)
    ordering = ('nombre',)


@admin.register(PrincipioActivo)
class PrincipioActivoAdmin(ConConteoMedicamentosAdmin):
    form = PrincipioActivoForm
    list_display = ('nombre', 'numero_medicamentos')
    search_fields = ('nombre',)
    ordering = ('nombre',)


@admin.register(Medicamento)
class MedicamentoAdmin(admin.ModelAdmin):
    form = MedicamentoForm
    list_display = ('nombre_comercial', 'presentacion', 'concentracion', 'principio_activo', 'categoria',
                    'registro_sanitario', 'fecha_vencimiento_registro', 'oculto')
    list_filter = ('categoria', 'oculto')
    search_fields = ('nombre_comercial', 'principio_activo__nombre', 'registro_sanitario')
    list_select_related = ('principio_activo', 'categoria')
    autocomplete_fields = ('principio_activo', 'categoria')
