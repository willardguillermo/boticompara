from django.contrib import admin, messages
from django.contrib.admin.utils import unquote
from django.core.exceptions import PermissionDenied
from django.shortcuts import redirect
from django.template.response import TemplateResponse
from django.urls import path, reverse
from django.utils import timezone
from django.utils.html import format_html
from django.utils.text import Truncator

from . import historial, resenas, revision, usuarios
from .etiquetas import GRIS, NARANJA, ROJO, VERDE, etiqueta
from .forms import (BoticaRevisionForm, DarBajaForm, MostrarResenaForm, OcultarResenaForm, ReactivarForm,
                    RechazarBoticaForm, SuspenderForm)
from .models import Botica, BoticaRevision, Producto, Resena, Usuario, UsuarioAccion
from .storage import StorageError, url_firmada_licencia

admin.site.site_header = 'BotiCompara - Administración'
admin.site.site_title = 'BotiCompara'
admin.site.index_title = 'Panel de administración'


class SoloLecturaAdmin(admin.ModelAdmin):
    """Permite ver los registros pero no crearlos, editarlos ni borrarlos."""

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False


# H94: naranja para la suspensión, verde para la reactivación y rojo para la baja
COLORES_ACCION = {
    UsuarioAccion.Accion.SUSPENDER: NARANJA,
    UsuarioAccion.Accion.REACTIVAR: VERDE,
    UsuarioAccion.Accion.DAR_BAJA: ROJO,
}

# Mismos colores para el estado de la cuenta en la lista y la ficha (A-U1)
COLORES_ESTADO_USUARIO = {
    usuarios.ACTIVO: VERDE,
    usuarios.SUSPENDIDO: NARANJA,
    usuarios.BAJA: ROJO,
}


class ColumnasAccion:
    """H94: columnas del historial que comparten el listado general y el detalle del usuario."""

    @admin.display(description='fecha', ordering='fecha')
    def fecha_accion(self, obj):
        return f'{timezone.localtime(obj.fecha):%d/%m/%Y %H:%M}'

    @admin.display(description='acción', ordering='accion')
    def accion_texto(self, obj):
        return etiqueta(obj.get_accion_display(), COLORES_ACCION.get(obj.accion, GRIS))

    @admin.display(description='motivo')
    def motivo_accion(self, obj):
        return obj.motivo or '—'

    @admin.display(description='realizado por', ordering='realizado_por')
    def realizado_por_texto(self, obj):
        return obj.realizado_por


class UsuarioAccionInline(ColumnasAccion, admin.TabularInline):
    """H94: historial en el detalle del usuario, de la más reciente a la más antigua, de solo lectura."""
    model = UsuarioAccion
    fields = ('fecha_accion', 'accion_texto', 'motivo_accion', 'realizado_por_texto')
    readonly_fields = fields
    ordering = ('-fecha', '-id')
    extra = 0
    can_delete = False
    verbose_name = 'acción'
    verbose_name_plural = 'historial de acciones'
    # Sin la línea con el texto del objeto arriba de cada fila: repetía lo que ya muestran las columnas
    template = 'admin/boticas/historial_inline.html'

    def has_add_permission(self, request, obj=None):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False


class EstadoUsuarioFilter(admin.SimpleListFilter):
    """H90: no hay columna estado; se calcula con activo y suspendido_hasta (boticas/usuarios.py)."""
    title = 'estado'
    parameter_name = 'estado'

    def lookups(self, request, model_admin):
        return usuarios.ESTADOS

    def queryset(self, request, queryset):
        if self.value() in dict(usuarios.ESTADOS):
            return queryset.filter(usuarios.q_estado(self.value()))
        return queryset


@admin.register(Usuario)
class UsuarioAdmin(SoloLecturaAdmin):
    """H90: los datos personales los maneja el propio usuario; aquí nadie los edita ni borra cuentas."""
    list_display = ('nombre', 'correo', 'rol', 'estado', 'fecha_registro')
    list_filter = ('rol', EstadoUsuarioFilter)
    search_fields = ('nombre', 'correo')
    search_help_text = 'Busca por nombre o correo.'
    # Nunca se muestran el hash de la contraseña ni el identificador de Google
    fields = ('nombre', 'correo', 'rol', 'telefono', 'direccion', 'correo_verificado',
              'estado', 'motivo', 'fecha_registro', 'actualizado')
    readonly_fields = fields
    inlines = [UsuarioAccionInline]  # H94

    @admin.display(description='estado')
    def estado(self, obj):
        return etiqueta(usuarios.texto_estado(obj), COLORES_ESTADO_USUARIO[usuarios.estado_de(obj)])

    @admin.display(description='motivo del estado')
    def motivo(self, obj):
        # Una suspensión vencida deja su motivo en la BD, pero la cuenta ya está activa
        if usuarios.estado_de(obj) == usuarios.ACTIVO:
            return '—'
        return obj.motivo_estado or '—'

    # Hora de Lima (TIME_ZONE), no la UTC que guarda la BD; mismo formato que la lista y el historial
    @admin.display(description='fecha de registro', ordering='creado_en')
    def fecha_registro(self, obj):
        return f'{timezone.localtime(obj.creado_en):%d/%m/%Y %H:%M}'

    @admin.display(description='actualizado en', ordering='actualizado_en')
    def actualizado(self, obj):
        return f'{timezone.localtime(obj.actualizado_en):%d/%m/%Y %H:%M}'

    # ---- H91-H93: acciones sobre la cuenta, cada una con su página ----

    def get_urls(self):
        # Antes que las del admin: su ruta "<id>/" atraparía estas URLs
        vista = self.admin_site.admin_view
        nombre = f'{self.opts.app_label}_{self.opts.model_name}'
        return [
            path('<path:object_id>/suspender/', vista(self.suspender_view), name=f'{nombre}_suspender'),
            path('<path:object_id>/reactivar/', vista(self.reactivar_view), name=f'{nombre}_reactivar'),
            path('<path:object_id>/dar-baja/', vista(self.dar_baja_view), name=f'{nombre}_dar_baja'),
            *super().get_urls(),
        ]

    def puede_gestionar(self, request):
        """Editar datos está prohibido para todos; suspender o dar de baja exige el permiso de cambio."""
        return request.user.has_perm(f'{self.opts.app_label}.change_{self.opts.model_name}')

    def url_accion(self, usuario, accion):
        return reverse(f'admin:{self.opts.app_label}_{self.opts.model_name}_{accion}', args=[usuario.pk],
                       current_app=self.admin_site.name)

    def url_detalle(self, usuario):
        return reverse(f'admin:{self.opts.app_label}_{self.opts.model_name}_change', args=[usuario.pk],
                       current_app=self.admin_site.name)

    def acciones_disponibles(self, request, usuario):
        """Botones del detalle según el estado. Una cuenta dada de baja no tiene ninguno."""
        if not self.puede_gestionar(request):
            return []
        estado = usuarios.estado_de(usuario)
        acciones = []
        if estado in usuarios.PUEDE_SUSPENDER:
            acciones.append({'texto': 'Suspender', 'url': self.url_accion(usuario, 'suspender')})
        if estado in usuarios.PUEDE_REACTIVAR:
            acciones.append({'texto': 'Reactivar', 'url': self.url_accion(usuario, 'reactivar')})
        if estado in usuarios.PUEDE_DAR_BAJA:
            acciones.append({'texto': 'Dar de baja', 'url': self.url_accion(usuario, 'dar_baja'), 'peligrosa': True})
        return acciones

    def change_view(self, request, object_id, form_url='', extra_context=None):
        usuario = self.get_object(request, unquote(object_id))
        # El título nativo de una ficha de solo lectura es "Vista usuario"; el subtítulo ya trae nombre y correo
        extra_context = {**(extra_context or {}), 'title': 'Usuario',
                         'acciones_usuario': self.acciones_disponibles(request, usuario) if usuario else []}
        return super().change_view(request, object_id, form_url, extra_context)

    def _cuenta_para(self, request, object_id, estados_validos, accion, requisito):
        """(usuario, None) si la acción corresponde al estado actual; si no, (None, redirección con el error).

        Se comprueba también aquí (y no solo en los botones) por si alguien entra directo por la URL.
        """
        if not self.puede_gestionar(request):
            raise PermissionDenied
        usuario = self.get_object(request, unquote(object_id))
        if usuario is None:
            messages.error(request, 'La cuenta no existe.')
            return None, redirect(reverse(f'admin:{self.opts.app_label}_{self.opts.model_name}_changelist',
                                          current_app=self.admin_site.name))
        if usuarios.estado_de(usuario) not in estados_validos:
            messages.error(request, usuarios.mensaje_rechazo(usuario, accion, requisito))
            return None, redirect(self.url_detalle(usuario))
        return usuario, None

    def _pagina(self, request, usuario, form, titulo, boton, **extra):
        contexto = {
            **self.admin_site.each_context(request),
            'opts': self.opts, 'title': titulo, 'usuario': usuario, 'form': form, 'boton': boton,
            'estado': usuarios.texto_estado(usuario), 'url_detalle': self.url_detalle(usuario),
            **extra,
        }
        return TemplateResponse(request, 'admin/boticas/usuario/accion.html', contexto)

    def _ejecutar(self, request, usuario, accion, *args):
        """Corre la acción; si el estado cambió mientras el formulario estaba abierto, avisa el error."""
        try:
            accion(usuario.pk, *args, request.user.get_username())
        except usuarios.AccionNoPermitida as e:
            messages.error(request, str(e))
            return False
        return True

    def suspender_view(self, request, object_id):
        """H91"""
        usuario, respuesta = self._cuenta_para(request, object_id, usuarios.PUEDE_SUSPENDER,
                                               'suspender', usuarios.REQUISITO_SUSPENDER)
        if respuesta:
            return respuesta
        form = SuspenderForm(request.POST if request.method == 'POST' else None)
        if form.is_valid():
            hasta, motivo = form.cleaned_data['hasta'], form.cleaned_data['motivo']
            if self._ejecutar(request, usuario, usuarios.suspender, hasta, motivo):
                messages.success(request, f'Se suspendió la cuenta de {usuario.nombre} hasta el '
                                          f'{hasta:%d/%m/%Y}.')
            return redirect(self.url_detalle(usuario))
        return self._pagina(request, usuario, form, f'Suspender la cuenta de {usuario.nombre}', 'Suspender cuenta')

    def reactivar_view(self, request, object_id):
        """H92: quita la suspensión antes de que venza."""
        usuario, respuesta = self._cuenta_para(request, object_id, usuarios.PUEDE_REACTIVAR,
                                               'reactivar', usuarios.REQUISITO_REACTIVAR)
        if respuesta:
            return respuesta
        form = ReactivarForm(request.POST if request.method == 'POST' else None)
        if form.is_valid():
            if self._ejecutar(request, usuario, usuarios.reactivar, form.cleaned_data['motivo']):
                messages.success(request, f'Se reactivó la cuenta de {usuario.nombre}.')
            return redirect(self.url_detalle(usuario))
        return self._pagina(request, usuario, form, f'Reactivar la cuenta de {usuario.nombre}', 'Reactivar cuenta')

    ADVERTENCIA_BAJA = ('La baja es definitiva y no se puede revertir: la cuenta no podrá volver a entrar a '
                        'BotiCompara y no existe ninguna acción para reactivarla. Si es dueño de una botica, '
                        'sus productos dejan de aparecer en las búsquedas, la botica pasa a «Rechazado» '
                        f'(«{usuarios.MOTIVO_BOTICA_BAJA}», así su RUC queda libre) y sus reservas pendientes, '
                        'confirmadas o listas se rechazan con un correo a cada comprador.')

    def dar_baja_view(self, request, object_id):
        """H93: primero el motivo, luego una página de confirmación; solo el segundo envío da de baja."""
        usuario, respuesta = self._cuenta_para(request, object_id, usuarios.PUEDE_DAR_BAJA,
                                               'dar de baja', usuarios.REQUISITO_DAR_BAJA)
        if respuesta:
            return respuesta
        form = DarBajaForm(request.POST if request.method == 'POST' else None)
        if form.is_valid():
            if request.POST.get('confirmado') != 'si':
                return self._pagina(request, usuario, form, f'¿Dar de baja la cuenta de {usuario.nombre}?',
                                    'Sí, dar de baja definitivamente', confirmacion=True, peligrosa=True,
                                    advertencia=self.ADVERTENCIA_BAJA)
            if self._ejecutar(request, usuario, usuarios.dar_baja, form.cleaned_data['motivo']):
                messages.success(request, f'Se dio de baja la cuenta de {usuario.nombre}. Esta acción es definitiva.')
            return redirect(self.url_detalle(usuario))
        return self._pagina(request, usuario, form, f'Dar de baja la cuenta de {usuario.nombre}', 'Continuar',
                            advertencia=self.ADVERTENCIA_BAJA)


# ---- H89: moderación de reseñas ----

class EstadoResenaFilter(admin.SimpleListFilter):
    title = 'estado'
    parameter_name = 'estado'

    def lookups(self, request, model_admin):
        return resenas.ESTADOS

    def queryset(self, request, queryset):
        if self.value() == resenas.VISIBLE:
            return queryset.filter(oculta=False)
        if self.value() == resenas.OCULTA:
            return queryset.filter(oculta=True)
        return queryset


class EstrellasFilter(admin.SimpleListFilter):
    title = 'estrellas'
    parameter_name = 'estrellas'

    def lookups(self, request, model_admin):
        return [(str(n), '★' * n) for n in range(5, 0, -1)]

    def queryset(self, request, queryset):
        if self.value() in {str(n) for n in range(1, 6)}:
            return queryset.filter(estrellas=int(self.value()))
        return queryset


class PalabrasARevisarFilter(admin.SimpleListFilter):
    """Solo encuentra las reseñas con palabras de settings.PALABRAS_A_REVISAR; no oculta nada."""
    title = 'palabras a revisar'
    parameter_name = 'revisar'

    def lookups(self, request, model_admin):
        return [('si', 'Con palabras a revisar')]

    def queryset(self, request, queryset):
        if self.value() == 'si':
            return queryset.filter(pk__in=resenas.ids_con_palabras(queryset))
        return queryset


COLORES_ESTADO_RESENA = {False: VERDE, True: GRIS}


@admin.register(Resena)
class ResenaAdmin(SoloLecturaAdmin):
    """H89: el texto y las estrellas son del comprador; aquí solo se ocultan o se vuelven a mostrar."""
    list_display = ('fecha', 'botica', 'autor', 'estrellas_texto', 'comentario_corto', 'respondida',
                    'estado')
    list_display_links = ('fecha',)
    list_filter = (EstadoResenaFilter, PalabrasARevisarFilter, EstrellasFilter, 'botica',
                   ('creado_en', admin.DateFieldListFilter))
    search_fields = ('comentario', 'botica__nombre_comercial')
    search_help_text = 'Busca en el comentario o el nombre de la botica.'
    list_select_related = ('botica', 'usuario')
    fields = ('botica', 'autor', 'estrellas_texto', 'comentario', 'palabras', 'respuesta', 'respondida_en_texto',
              'estado', 'motivo_ocultamiento', 'fecha', 'actualizado')
    readonly_fields = fields

    # Hora de Lima, como en el resto del admin
    @admin.display(description='fecha', ordering='creado_en')
    def fecha(self, obj):
        return f'{timezone.localtime(obj.creado_en):%d/%m/%Y %H:%M}'

    @admin.display(description='actualizado en', ordering='actualizado_en')
    def actualizado(self, obj):
        return f'{timezone.localtime(obj.actualizado_en):%d/%m/%Y %H:%M}'

    @admin.display(description='respondida en')
    def respondida_en_texto(self, obj):
        return f'{timezone.localtime(obj.respondida_en):%d/%m/%Y %H:%M}' if obj.respondida_en else '—'

    # El admin es interno: se ve el nombre completo y el correo (la app muestra "Carlos R.")
    @admin.display(description='autor', ordering='usuario__nombre')
    def autor(self, obj):
        return format_html('{}<br><span class="quiet">{}</span>', obj.usuario.nombre, obj.usuario.correo)

    @admin.display(description='estrellas', ordering='estrellas')
    def estrellas_texto(self, obj):
        return format_html('<span title="{} de 5" style="white-space: nowrap;">{}</span>',
                           obj.estrellas, '★' * obj.estrellas + '☆' * (5 - obj.estrellas))

    @admin.display(description='comentario')
    def comentario_corto(self, obj):
        return Truncator(obj.comentario).chars(80) if obj.comentario else '—'

    @admin.display(description='respondida', boolean=True)
    def respondida(self, obj):
        return obj.respuesta is not None

    @admin.display(description='estado', ordering='oculta')
    def estado(self, obj):
        return etiqueta(resenas.texto_estado(obj), COLORES_ESTADO_RESENA[obj.oculta])

    @admin.display(description='palabras a revisar')
    def palabras(self, obj):
        return ', '.join(resenas.palabras_encontradas(obj.comentario)) or '—'

    # ---- Ocultar y mostrar, cada una con su página ----

    def get_urls(self):
        vista = self.admin_site.admin_view
        nombre = f'{self.opts.app_label}_{self.opts.model_name}'
        return [
            path('<path:object_id>/ocultar/', vista(self.ocultar_view), name=f'{nombre}_ocultar'),
            path('<path:object_id>/mostrar/', vista(self.mostrar_view), name=f'{nombre}_mostrar'),
            *super().get_urls(),
        ]

    def puede_moderar(self, request):
        """Editar está prohibido para todos; ocultar y mostrar exigen "Can change resena"."""
        return request.user.has_perm(f'{self.opts.app_label}.change_{self.opts.model_name}')

    def url_accion(self, resena, accion):
        return reverse(f'admin:{self.opts.app_label}_{self.opts.model_name}_{accion}', args=[resena.pk],
                       current_app=self.admin_site.name)

    def url_detalle(self, resena):
        return reverse(f'admin:{self.opts.app_label}_{self.opts.model_name}_change', args=[resena.pk],
                       current_app=self.admin_site.name)

    def acciones_disponibles(self, request, resena):
        if not self.puede_moderar(request):
            return []
        if resena.oculta:
            return [{'texto': 'Mostrar de nuevo', 'url': self.url_accion(resena, 'mostrar')}]
        return [{'texto': 'Ocultar', 'url': self.url_accion(resena, 'ocultar'), 'peligrosa': True}]

    def change_view(self, request, object_id, form_url='', extra_context=None):
        resena = self.get_object(request, unquote(object_id))
        extra_context = {**(extra_context or {}), 'title': 'Reseña',
                         'acciones_resena': self.acciones_disponibles(request, resena) if resena else []}
        return super().change_view(request, object_id, form_url, extra_context)

    def _resena_para(self, request, object_id, oculta, requisito):
        """(reseña, None) si la acción corresponde a su estado; si no, (None, redirección con el error)."""
        if not self.puede_moderar(request):
            raise PermissionDenied
        resena = self.get_object(request, unquote(object_id))
        if resena is None:
            messages.error(request, 'La reseña no existe.')
            return None, redirect(reverse(f'admin:{self.opts.app_label}_{self.opts.model_name}_changelist',
                                          current_app=self.admin_site.name))
        if resena.oculta != oculta:
            messages.error(request, f'La reseña está «{resenas.texto_estado(resena)}». {requisito}')
            return None, redirect(self.url_detalle(resena))
        return resena, None

    def _pagina(self, request, resena, form, titulo, boton, **extra):
        contexto = {
            **self.admin_site.each_context(request),
            'opts': self.opts, 'title': titulo, 'resena': resena, 'form': form, 'boton': boton,
            'autor': f'{resena.usuario.nombre} <{resena.usuario.correo}>',
            'estrellas': '★' * resena.estrellas + '☆' * (5 - resena.estrellas),
            'estado': resenas.texto_estado(resena), 'url_detalle': self.url_detalle(resena),
            'palabras': ', '.join(resenas.palabras_encontradas(resena.comentario)),
            **extra,
        }
        return TemplateResponse(request, 'admin/boticas/resena/accion.html', contexto)

    def _ejecutar(self, request, accion, *args):
        try:
            accion(*args, request.user)
        except resenas.AccionNoPermitida as e:
            messages.error(request, str(e))
            return False
        return True

    def ocultar_view(self, request, object_id):
        resena, respuesta = self._resena_para(request, object_id, False, resenas.REQUISITO_OCULTAR)
        if respuesta:
            return respuesta
        form = OcultarResenaForm(request.POST if request.method == 'POST' else None)
        if form.is_valid():
            if self._ejecutar(request, resenas.ocultar, resena.pk, form.cleaned_data['motivo']):
                messages.success(request, f'Se ocultó la reseña de {resena.usuario.nombre} sobre '
                                          f'{resena.botica.nombre_comercial}. Ya no aparece en la app.')
            return redirect(self.url_detalle(resena))
        return self._pagina(request, resena, form, 'Ocultar la reseña', 'Ocultar reseña', peligrosa=True)

    def mostrar_view(self, request, object_id):
        resena, respuesta = self._resena_para(request, object_id, True, resenas.REQUISITO_MOSTRAR)
        if respuesta:
            return respuesta
        if request.method == 'POST':
            if self._ejecutar(request, resenas.mostrar, resena.pk):
                messages.success(request, f'La reseña de {resena.usuario.nombre} sobre '
                                          f'{resena.botica.nombre_comercial} vuelve a aparecer en la app.')
            return redirect(self.url_detalle(resena))
        return self._pagina(request, resena, MostrarResenaForm(), '¿Mostrar de nuevo la reseña?',
                            'Sí, mostrarla de nuevo', motivo_actual=resena.motivo_ocultamiento)


@admin.register(Producto)
class ProductoAdmin(SoloLecturaAdmin):
    list_display = ('nombre_comercial', 'principio_activo', 'presentacion', 'precio',
                    'stock', 'stock_minimo', 'botica', 'medicamento', 'activo')
    list_filter = ('activo', 'botica__estado', 'botica')
    search_fields = ('nombre_comercial', 'principio_activo', 'botica__nombre_comercial')
    list_select_related = ('botica', 'medicamento')


class ProductoInline(admin.TabularInline):
    model = Producto
    fields = ('nombre_comercial', 'principio_activo', 'presentacion', 'precio', 'stock', 'activo')
    readonly_fields = fields
    extra = 0
    can_delete = False
    show_change_link = True

    def has_add_permission(self, request, obj=None):
        return False

    def has_change_permission(self, request, obj=None):
        return False


# H47: mismos colores que la columna de vigencia del catálogo
COLORES_REVISION = {
    BoticaRevision.Estado.APROBADO: VERDE,
    BoticaRevision.Estado.RECHAZADO: ROJO,
}

# Estado de la botica en la lista: los mismos colores que el historial, y naranja si espera revisión (A-B2)
COLORES_ESTADO_BOTICA = {
    Botica.Estado.PENDIENTE: NARANJA,
    Botica.Estado.APROBADO: VERDE,
    Botica.Estado.RECHAZADO: ROJO,
}


class ColumnasRevision:
    """Columnas del historial que comparten la lista y el inline de la ficha de la botica."""

    @admin.display(description='fecha', ordering='fecha')
    def fecha_revision(self, obj):
        # Hora de Lima (TIME_ZONE), no la UTC que guarda la BD
        return f'{timezone.localtime(obj.fecha):%d/%m/%Y %H:%M}'

    @admin.display(description='estado', ordering='estado')
    def estado_revision(self, obj):
        return etiqueta(obj.get_estado_display(), COLORES_REVISION.get(obj.estado, GRIS))

    @admin.display(description='revisado por', ordering='revisado_por')
    def revisado_por_texto(self, obj):
        return historial.texto_revisado_por(obj.revisado_por)

    @admin.display(description='motivo')
    def motivo_corto(self, obj):
        return Truncator(obj.motivo).chars(80) if obj.motivo else '—'


class RangoFechasFilter(admin.ListFilter):
    """H47: filtra por fecha de revisión con ?desde=aaaa-mm-dd&hasta=aaaa-mm-dd (días en hora de Lima).

    Es un filtro de la lista (y no un parámetro suelto) para que el changelist conserve
    desde/hasta al combinarlos con el filtro de estado, la búsqueda y la paginación.
    Los campos se dibujan arriba de la lista (boticarevision/change_list.html), no en la barra lateral.
    """
    title = 'fecha de revisión'
    template = 'admin/boticas/boticarevision/filtro_vacio.html'
    parametros = ('desde', 'hasta')

    def __init__(self, request, params, model, model_admin):
        super().__init__(request, params, model, model_admin)
        for nombre in self.parametros:
            if nombre in params:
                self.used_parameters[nombre] = params.pop(nombre)[-1]
        self.desde, self.hasta, self.error = historial.validar_rango(
            self.used_parameters.get('desde'), self.used_parameters.get('hasta'))
        # El changelist puede crear el filtro más de una vez por petición: se avisa una sola vez
        if self.error and not getattr(request, '_aviso_rango_fechas', False):
            request._aviso_rango_fechas = True
            messages.error(request, self.error)

    def has_output(self):
        # True para que el changelist lo aplique; su plantilla no dibuja nada en la barra lateral
        return True

    def choices(self, changelist):
        return []

    def expected_parameters(self):
        return list(self.parametros)

    def queryset(self, request, queryset):
        if self.desde:
            queryset = queryset.filter(fecha__date__gte=self.desde)
        if self.hasta:
            queryset = queryset.filter(fecha__date__lte=self.hasta)
        return queryset


class ConRangoFechas:
    """Lista con los campos Desde/Hasta arriba (RangoFechasFilter va en list_filter)."""
    change_list_template = 'admin/boticas/boticarevision/change_list.html'

    def changelist_view(self, request, extra_context=None):
        # Valores que se muestran en los campos Desde/Hasta de arriba de la lista
        extra_context = {**(extra_context or {}),
                         'rango_desde': request.GET.get('desde', ''),
                         'rango_hasta': request.GET.get('hasta', '')}
        return super().changelist_view(request, extra_context)


@admin.register(BoticaRevision)
class BoticaRevisionAdmin(ConRangoFechas, ColumnasRevision, SoloLecturaAdmin):
    """H47: historial de aprobaciones y rechazos, de solo lectura."""
    list_display = ('fecha_revision', 'botica_enlace', 'ruc', 'estado_revision', 'revisado_por_texto',
                    'motivo_corto')
    list_display_links = ('fecha_revision',)
    list_filter = ('estado', RangoFechasFilter)
    search_fields = ('botica__nombre_comercial', 'botica__ruc', 'revisado_por')
    search_help_text = 'Busca por nombre de la botica, RUC o administrador que la revisó.'
    list_select_related = ('botica',)
    fields = ('fecha_revision', 'botica', 'estado_revision', 'revisado_por_texto', 'motivo')
    readonly_fields = fields

    @admin.display(description='botica', ordering='botica__nombre_comercial')
    def botica_enlace(self, obj):
        return format_html('<a href="{}">{}</a>',
                           reverse('admin:boticas_botica_change', args=[obj.botica_id]),
                           obj.botica.nombre_comercial)

    @admin.display(description='RUC', ordering='botica__ruc')
    def ruc(self, obj):
        return obj.botica.ruc



class RevisionInline(ColumnasRevision, admin.TabularInline):
    """H47: historial de revisiones en la ficha de la botica, de solo lectura."""
    model = BoticaRevision
    fields = ('fecha_revision', 'estado_revision', 'revisado_por_texto', 'motivo_corto')
    readonly_fields = fields
    extra = 0
    can_delete = False
    verbose_name = 'revisión'
    verbose_name_plural = 'historial de revisiones'
    # Sin la línea con el texto del objeto arriba de cada fila, igual que en el usuario (H94)
    template = 'admin/boticas/historial_inline.html'

    def has_add_permission(self, request, obj=None):
        return False

    def has_change_permission(self, request, obj=None):
        return False


@admin.register(Botica)
class BoticaAdmin(admin.ModelAdmin):
    form = BoticaRevisionForm
    list_display = ('nombre_comercial', 'ruc', 'correo_dueno', 'distrito', 'estado_etiqueta', 'tiene_licencia',
                    'creado_en')
    list_filter = ('estado', 'distrito')
    search_fields = ('nombre_comercial', 'ruc', 'usuario__correo')
    list_select_related = ('usuario',)
    inlines = [RevisionInline, ProductoInline]
    actions = ['aprobar_boticas']

    # Los datos los registra el dueño desde la web: aquí solo se revisan
    readonly_fields = ('usuario', 'correo_dueno', 'nombre_comercial', 'ruc', 'razon_social',
                       'direccion', 'distrito', 'telefono', 'ubicacion', 'licencia',
                       'creado_en', 'actualizado_en')
    fieldsets = (
        ('Datos de la botica', {
            'fields': ('nombre_comercial', 'ruc', 'razon_social', 'direccion', 'distrito', 'telefono'),
        }),
        ('Evidencia para la aprobación', {'fields': ('licencia', 'ubicacion')}),
        ('Dueño', {'fields': ('usuario', 'correo_dueno')}),
        ('Revisión', {'fields': ('estado', 'motivo_rechazo')}),
        ('Fechas', {'fields': ('creado_en', 'actualizado_en')}),
    )

    @admin.display(description='estado', ordering='estado')
    def estado_etiqueta(self, obj):
        return etiqueta(obj.get_estado_display(), COLORES_ESTADO_BOTICA.get(obj.estado, GRIS))

    @admin.display(description='correo del dueño', ordering='usuario__correo')
    def correo_dueno(self, obj):
        return obj.usuario.correo

    @admin.display(description='licencia', boolean=True, ordering='licencia_url')
    def tiene_licencia(self, obj):
        return bool(obj.licencia_url)

    @admin.display(description='licencia de funcionamiento')
    def licencia(self, obj):
        # H4: el bucket es privado, se genera una URL firmada que vence en 5 minutos
        if not obj.licencia_url:
            return 'Sin licencia cargada'
        try:
            url = url_firmada_licencia(obj.licencia_url)
        except StorageError as e:
            return format_html('<span style="color: var(--error-fg, #ba2121);">No se pudo generar el enlace: {}</span>', e)
        return format_html(
            '<a href="{}" target="_blank" rel="noopener noreferrer">Ver licencia</a>'
            ' <span class="help">(el enlace vence en 5 minutos; recarga la página para generar otro)</span>',
            url,
        )

    @admin.display(description='ubicación')
    def ubicacion(self, obj):
        # H3: coordenadas opcionales registradas por el dueño en el mapa de la web
        if obj.latitud is None or obj.longitud is None:
            return 'Sin ubicación registrada'
        lat, lng = float(obj.latitud), float(obj.longitud)
        delta = 0.004
        return format_html(
            '{}, {} &nbsp; <a href="https://www.openstreetmap.org/?mlat={}&amp;mlon={}#map=17/{}/{}"'
            ' target="_blank" rel="noopener noreferrer">Ver en el mapa</a>'
            '<br><iframe title="Ubicación de la botica" width="420" height="260" loading="lazy"'
            ' style="border: 1px solid var(--border-color, #ccc); margin-top: 8px; max-width: 100%;"'
            ' src="https://www.openstreetmap.org/export/embed.html?bbox={},{},{},{}&amp;layer=mapnik&amp;marker={},{}">'
            '</iframe>',
            obj.latitud, obj.longitud, lat, lng, lat, lng,
            round(lng - delta, 6), round(lat - delta, 6), round(lng + delta, 6), round(lat + delta, 6), lat, lng,
        )

    @admin.action(description='Aprobar boticas seleccionadas', permissions=['change'])
    def aprobar_boticas(self, request, queryset):
        # Solo las que realmente cambian de estado reciben el correo (H6) y quedan en el historial (H47).
        # S4-08: como los botones, solo desde PENDIENTE (una RECHAZADO vuelve a PENDIENTE cuando el dueño la corrige)
        elegidas = queryset.count()
        try:
            aprobadas = revision.revisar(queryset.values('pk'), Botica.Estado.APROBADO, request.user.get_username(),
                                         desde=revision.PUEDE_APROBAR,
                                         si_falla_correo=lambda b: self._aviso_correo(request, b))
        except revision.NoSePuedeAprobar as e:
            self.message_user(request, f'No se aprobó ninguna botica. {e}', messages.ERROR)
            return
        self.message_user(request, f'{len(aprobadas)} botica(s) aprobada(s).', messages.SUCCESS)
        if elegidas > len(aprobadas):
            self.message_user(request, f'Se omitieron {elegidas - len(aprobadas)} botica(s) que no estaban '
                                       f'pendientes. {self.REQUISITO_APROBAR}', messages.WARNING)
        self._aviso_rechazadas_por_ruc(request, aprobadas)

    def _aviso_rechazadas_por_ruc(self, request, revisadas):
        """A5: las PENDIENTE con el mismo RUC que se rechazaron solas al aprobar."""
        rechazadas = getattr(revisadas, 'rechazadas_por_ruc', ())
        if rechazadas:
            nombres = ', '.join(f'{b.nombre_comercial} ({b.usuario.correo})' for b in rechazadas)
            self.message_user(
                request,
                f'Se rechazaron {len(rechazadas)} solicitud(es) pendiente(s) con el mismo RUC, con el motivo '
                f'«{revision.MOTIVO_RUC_ACREDITADO}»: {nombres}.',
                messages.INFO,
            )

    def _aviso_correo(self, request, botica):
        self.message_user(
            request,
            f'No se pudo enviar el correo a {botica.usuario.correo} ({botica.nombre_comercial}). '
            'La revisión sí se guardó.',
            messages.WARNING,
        )

    def has_add_permission(self, request):
        return False

    def has_delete_permission(self, request, obj=None):
        return False

    def save_model(self, request, obj, form, change):
        # Aprobar o rechazar con el select "estado" pasa por la misma función que la acción y los botones.
        # Si no hay cambio de estado (o pasa a PENDIENTE), solo se guarda lo del formulario, sin historial.
        if obj.estado in revision.ESTADOS_REVISADOS:
            try:
                revisadas = revision.revisar([obj.pk], obj.estado, request.user.get_username(),
                                             motivo=obj.motivo_rechazo,
                                             si_falla_correo=lambda b: self._aviso_correo(request, b))
            except revision.NoSePuedeAprobar as e:
                # El formulario ya lo revisa; esto es si otro administrador aprobó el mismo RUC a la vez
                self.message_user(request, f'No se guardó el cambio de estado. {e}', messages.ERROR)
                return
            if revisadas:
                self._aviso_rechazadas_por_ruc(request, revisadas)
                return
        # Solo se escribe la revisión, para no pisar datos que el dueño
        # haya cambiado desde la web mientras el admin tenía el formulario abierto
        obj.save(update_fields=['estado', 'motivo_rechazo'])

    # ---- Botones Aprobar y Rechazar de la ficha, cada uno con su página (como las reseñas, H89) ----

    def get_urls(self):
        # Antes que las del admin: su ruta "<id>/" atraparía estas URLs
        vista = self.admin_site.admin_view
        nombre = f'{self.opts.app_label}_{self.opts.model_name}'
        return [
            path('<path:object_id>/aprobar/', vista(self.aprobar_view), name=f'{nombre}_aprobar'),
            path('<path:object_id>/rechazar/', vista(self.rechazar_view), name=f'{nombre}_rechazar'),
            *super().get_urls(),
        ]

    def url_accion(self, botica, accion):
        return reverse(f'admin:{self.opts.app_label}_{self.opts.model_name}_{accion}', args=[botica.pk],
                       current_app=self.admin_site.name)

    def url_detalle(self, botica):
        return reverse(f'admin:{self.opts.app_label}_{self.opts.model_name}_change', args=[botica.pk],
                       current_app=self.admin_site.name)

    def acciones_disponibles(self, request, botica):
        """Aprobar si está pendiente; Rechazar si está pendiente o aprobada. Sin permiso de cambio, ninguno."""
        if not self.has_change_permission(request, botica):
            return []
        acciones = []
        if botica.estado in revision.PUEDE_APROBAR:
            acciones.append({'texto': 'Aprobar', 'url': self.url_accion(botica, 'aprobar'), 'color': VERDE})
        if botica.estado in revision.PUEDE_RECHAZAR:
            acciones.append({'texto': 'Rechazar', 'url': self.url_accion(botica, 'rechazar'), 'peligrosa': True})
        return acciones

    def change_view(self, request, object_id, form_url='', extra_context=None):
        botica = self.get_object(request, unquote(object_id))
        extra_context = {**(extra_context or {}),
                         'acciones_botica': self.acciones_disponibles(request, botica) if botica else []}
        return super().change_view(request, object_id, form_url, extra_context)

    def _botica_para(self, request, object_id, estados_validos, requisito):
        """(botica, None) si la acción corresponde a su estado; si no, (None, redirección con el error).

        Se comprueba también aquí (y no solo en los botones) por si alguien entra directo por la URL.
        """
        botica = self.get_object(request, unquote(object_id))
        if botica is None:
            messages.error(request, 'La botica no existe.')
            return None, redirect(reverse(f'admin:{self.opts.app_label}_{self.opts.model_name}_changelist',
                                          current_app=self.admin_site.name))
        if not self.has_change_permission(request, botica):
            raise PermissionDenied
        if botica.estado not in estados_validos:
            messages.error(request, f'La botica está «{botica.get_estado_display()}». {requisito}')
            return None, redirect(self.url_detalle(botica))
        return botica, None

    def _pagina(self, request, botica, form, titulo, boton, **extra):
        contexto = {
            **self.admin_site.each_context(request),
            'opts': self.opts, 'title': titulo, 'botica': botica, 'form': form, 'boton': boton,
            'url_detalle': self.url_detalle(botica),
            **extra,
        }
        return TemplateResponse(request, 'admin/boticas/botica/accion.html', contexto)

    REQUISITO_APROBAR = 'Solo se aprueba una botica pendiente.'
    REQUISITO_RECHAZAR = 'Solo se rechaza una botica pendiente o aprobada.'

    def _resultado(self, request, botica, cambiadas, exito, requisito):
        if cambiadas:
            messages.success(request, exito)
        else:
            # Otro administrador la revisó mientras esta página estaba abierta
            botica.refresh_from_db(fields=['estado'])
            messages.error(request, f'No se hizo ningún cambio: la botica ya está «{botica.get_estado_display()}». '
                                    f'{requisito}')
        return redirect(self.url_detalle(botica))

    def aprobar_view(self, request, object_id):
        botica, respuesta = self._botica_para(request, object_id, revision.PUEDE_APROBAR, self.REQUISITO_APROBAR)
        if respuesta:
            return respuesta
        # S4-08 y A5: sin licencia, o si otra cuenta ya acreditó este RUC, ni se pide confirmar
        if not revision.tiene_licencia(botica):
            messages.error(request, revision.mensaje_sin_licencia([botica]))
            return redirect(self.url_detalle(botica))
        aprobada = revision.aprobada_con_ruc(botica)
        if aprobada:
            messages.error(request, revision.mensaje_ruc_aprobado(botica.ruc, aprobada))
            return redirect(self.url_detalle(botica))
        if request.method == 'POST':
            try:
                cambiadas = revision.revisar([botica.pk], Botica.Estado.APROBADO, request.user.get_username(),
                                             desde=revision.PUEDE_APROBAR,
                                             si_falla_correo=lambda b: self._aviso_correo(request, b))
            except revision.NoSePuedeAprobar as e:
                messages.error(request, str(e))
                return redirect(self.url_detalle(botica))
            respuesta = self._resultado(request, botica, cambiadas,
                                        f'Se aprobó la botica {botica.nombre_comercial}. Sus productos ya aparecen '
                                        'en las búsquedas.', self.REQUISITO_APROBAR)
            self._aviso_rechazadas_por_ruc(request, cambiadas)
            return respuesta
        otras = Botica.objects.filter(ruc=botica.ruc, estado=Botica.Estado.PENDIENTE).exclude(pk=botica.pk).count()
        aviso = ('Sus productos aparecerán en las búsquedas de la app y el dueño recibirá un '
                 'correo. Revisa antes la licencia y la ubicación en la ficha.')
        if otras:
            aviso += (f' Hay {otras} solicitud(es) pendiente(s) más con el RUC {botica.ruc}: se rechazarán con el '
                      f'motivo «{revision.MOTIVO_RUC_ACREDITADO}» y sus dueños recibirán el correo.')
        return self._pagina(request, botica, None, f'¿Aprobar la botica {botica.nombre_comercial}?',
                            'Sí, aprobar botica', aviso=aviso)

    def rechazar_view(self, request, object_id):
        botica, respuesta = self._botica_para(request, object_id, revision.PUEDE_RECHAZAR, self.REQUISITO_RECHAZAR)
        if respuesta:
            return respuesta
        form = RechazarBoticaForm(request.POST if request.method == 'POST' else None)
        if form.is_valid():
            cambiadas = revision.revisar([botica.pk], Botica.Estado.RECHAZADO, request.user.get_username(),
                                         motivo=form.cleaned_data['motivo'], desde=revision.PUEDE_RECHAZAR,
                                         si_falla_correo=lambda b: self._aviso_correo(request, b))
            return self._resultado(request, botica, cambiadas,
                                   f'Se rechazó la botica {botica.nombre_comercial}. El dueño verá el motivo '
                                   'en la web.', self.REQUISITO_RECHAZAR)
        aviso = 'El dueño recibirá un correo con el motivo y podrá corregir los datos y reenviar la solicitud.'
        if botica.estado == Botica.Estado.APROBADO:
            aviso = 'La botica está aprobada: sus productos dejarán de aparecer en las búsquedas. ' + aviso
        return self._pagina(request, botica, form, f'Rechazar la botica {botica.nombre_comercial}',
                            'Rechazar botica', peligrosa=True, aviso=aviso)


class RangoFechasAccionFilter(RangoFechasFilter):
    title = 'fecha de la acción'


@admin.register(UsuarioAccion)
class UsuarioAccionAdmin(ConRangoFechas, ColumnasAccion, SoloLecturaAdmin):
    """H94: todas las suspensiones, reactivaciones y bajas, de solo lectura."""
    list_display = ('fecha_accion', 'usuario_enlace', 'correo', 'accion_texto', 'motivo_corto',
                    'realizado_por_texto')
    list_display_links = ('fecha_accion',)
    list_filter = ('accion', RangoFechasAccionFilter)
    search_fields = ('usuario__nombre', 'usuario__correo', 'realizado_por')
    search_help_text = 'Busca por nombre o correo del usuario, o por el administrador que hizo la acción.'
    list_select_related = ('usuario',)
    fields = ('fecha_accion', 'usuario_enlace', 'correo', 'accion_texto', 'motivo_accion', 'realizado_por_texto')
    readonly_fields = fields

    @admin.display(description='usuario', ordering='usuario__nombre')
    def usuario_enlace(self, obj):
        return format_html('<a href="{}">{}</a>',
                           reverse('admin:boticas_usuario_change', args=[obj.usuario_id],
                                   current_app=self.admin_site.name),
                           obj.usuario.nombre)

    @admin.display(description='correo', ordering='usuario__correo')
    def correo(self, obj):
        return obj.usuario.correo

    @admin.display(description='motivo')
    def motivo_corto(self, obj):
        return Truncator(obj.motivo).chars(80) if obj.motivo else '—'
