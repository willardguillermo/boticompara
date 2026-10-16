from django.contrib import admin
from django.db.models import Count
from django.forms.models import BaseInlineFormSet

from .forms import (CategoriaForm, GrupoEquivalenciaForm, MedicamentoForm, MiembroGrupoForm,
                    PrincipioActivoForm)
from .models import Categoria, GrupoEquivalencia, Medicamento, MedicamentoGrupo, PrincipioActivo


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
                    'registro_sanitario', 'fecha_vencimiento_registro', 'grupo_equivalencia', 'oculto')
    list_filter = ('categoria', 'oculto')
    search_fields = ('nombre_comercial', 'principio_activo__nombre', 'registro_sanitario')
    list_select_related = ('principio_activo', 'categoria', 'grupo_asignado__grupo')
    autocomplete_fields = ('principio_activo', 'categoria')

    @admin.display(description='grupo de equivalencia', ordering='grupo_asignado__grupo__nombre')
    def grupo_equivalencia(self, obj):
        try:
            return obj.grupo_asignado.grupo
        except MedicamentoGrupo.DoesNotExist:
            return None


class MiembroGrupoFormSet(BaseInlineFormSet):
    """Copia el principio activo del grupo en cada fila: el usuario no lo elige."""

    def get_form_kwargs(self, index):
        # self.instance ya trae los datos enviados del grupo (Django los aplica antes de crear los inlines)
        return {**super().get_form_kwargs(index), 'grupo': self.instance}

    def save_new(self, form, commit=True):
        form.instance.principio_activo_id = self.instance.principio_activo_id
        return super().save_new(form, commit)


class QuitarMiembroFormSet(BaseInlineFormSet):
    def delete_existing(self, obj, commit=True):
        # obj.delete() pondría en None la PK (el medicamento) y el historial del
        # admin ya no podría mostrar qué medicamento se quitó
        if commit:
            MedicamentoGrupo.objects.filter(pk=obj.pk).delete()


class MiembroGrupoInline(admin.TabularInline):
    """Medicamentos que ya están en el grupo: solo se pueden quitar.

    El medicamento es la PK de medicamento_grupo; si se pudiera editar en la fila,
    cambiarlo crearía otra fila en vez de modificar esta.
    """
    model = MedicamentoGrupo
    formset = QuitarMiembroFormSet
    fields = ('medicamento',)
    readonly_fields = ('medicamento',)
    extra = 0
    verbose_name = 'medicamento del grupo'
    verbose_name_plural = 'medicamentos del grupo'

    def get_queryset(self, request):
        return super().get_queryset(request).select_related('medicamento')

    def has_add_permission(self, request, obj=None):
        return False


class AgregarMiembroInline(admin.TabularInline):
    """Filas vacías para agregar medicamentos al grupo."""
    model = MedicamentoGrupo
    form = MiembroGrupoForm
    formset = MiembroGrupoFormSet
    fields = ('medicamento',)
    autocomplete_fields = ('medicamento',)
    extra = 1
    verbose_name = 'medicamento a agregar'
    verbose_name_plural = 'agregar medicamentos'

    def get_queryset(self, request):
        return super().get_queryset(request).none()

    def has_delete_permission(self, request, obj=None):
        return False


@admin.register(GrupoEquivalencia)
class GrupoEquivalenciaAdmin(admin.ModelAdmin):
    form = GrupoEquivalenciaForm
    list_display = ('nombre', 'principio_activo', 'numero_medicamentos')
    list_filter = ('principio_activo',)
    search_fields = ('nombre', 'principio_activo__nombre')
    list_select_related = ('principio_activo',)
    autocomplete_fields = ('principio_activo',)
    inlines = (MiembroGrupoInline, AgregarMiembroInline)

    def get_queryset(self, request):
        return super().get_queryset(request).annotate(num_medicamentos=Count('miembros'))

    @admin.display(description='medicamentos', ordering='num_medicamentos')
    def numero_medicamentos(self, obj):
        return obj.num_medicamentos
