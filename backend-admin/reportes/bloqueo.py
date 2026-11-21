"""
SEC-14: página que ve quien llega al límite de intentos en el login del admin (django-axes).

Usa el contexto del sitio del admin (título, cabecera) para verse igual que el login. No dice si el
usuario existe: axes cuenta los fallos por usuario + IP aunque ese usuario no exista.
"""
from django.contrib import admin
from django.shortcuts import render
from django.urls import reverse

from axes.helpers import get_cool_off

PLANTILLA = 'admin/reportes/bloqueo_login.html'


def minutos_de_espera(request):
    espera = get_cool_off(request)
    return max(1, round(espera.total_seconds() / 60)) if espera else None


def respuesta_bloqueo(request, respuesta_original=None, credenciales=None):
    contexto = {
        **admin.site.each_context(request),
        'title': 'Demasiados intentos',
        'minutos': minutos_de_espera(request),
        'url_login': reverse('admin:login'),
    }
    respuesta = render(request, PLANTILLA, contexto, status=429)
    if contexto['minutos']:
        respuesta['Retry-After'] = str(contexto['minutos'] * 60)
    return respuesta
