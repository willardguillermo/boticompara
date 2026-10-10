"""
Supabase Storage (H4): URL firmada temporal para ver la licencia de una botica.

El bucket de licencias es privado. licencia_url guarda la ruta dentro del bucket
(por ejemplo "botica-3/licencia.pdf"), la misma que escribe backend-usuario.
Se usan los mismos headers que StorageService.java.
"""
import json
import logging
import os
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen

logger = logging.getLogger(__name__)

SEGUNDOS_URL_FIRMADA = 5 * 60


class StorageError(Exception):
    """No se pudo obtener la URL firmada. El mensaje se puede mostrar al admin."""


def _configuracion():
    url = os.getenv('SUPABASE_URL', '').rstrip('/')
    clave = os.getenv('SUPABASE_SECRET_KEY', '')
    bucket = os.getenv('SUPABASE_BUCKET_LICENCIAS', 'licencias') or 'licencias'
    if not url or not clave:
        raise StorageError('El almacenamiento no está configurado (faltan SUPABASE_URL o SUPABASE_SECRET_KEY en el .env).')
    return url, clave, bucket


def url_firmada_licencia(ruta):
    """Devuelve una URL que permite abrir la licencia durante 5 minutos."""
    url, clave, bucket = _configuracion()
    ruta = quote(ruta.lstrip('/'))
    headers = {'apikey': clave, 'Content-Type': 'application/json'}
    # Las claves nuevas (sb_secret_...) van solo en apikey; las antiguas (JWT) también en Authorization
    if clave.startswith('eyJ'):
        headers['Authorization'] = f'Bearer {clave}'

    peticion = Request(
        f'{url}/storage/v1/object/sign/{bucket}/{ruta}',
        data=json.dumps({'expiresIn': SEGUNDOS_URL_FIRMADA}).encode(),
        headers=headers,
        method='POST',
    )
    try:
        with urlopen(peticion, timeout=10) as respuesta:
            datos = json.load(respuesta)
    except HTTPError as e:
        logger.warning('Storage no firmó %s (HTTP %s): %s', ruta, e.code, e.read()[:300])
        if e.code in (400, 404):
            raise StorageError('El archivo de la licencia no se encontró en el almacenamiento.') from e
        raise StorageError(f'El almacenamiento respondió con un error (HTTP {e.code}).') from e
    except (URLError, TimeoutError, OSError, ValueError) as e:
        logger.warning('No se pudo conectar con Supabase Storage: %s', e)
        raise StorageError('No se pudo conectar con el almacenamiento. Intenta recargar la página.') from e

    firmada = datos.get('signedURL') or datos.get('signedUrl')
    if not firmada:
        raise StorageError('El almacenamiento no devolvió la URL firmada.')
    # Supabase responde una ruta relativa a /storage/v1 ("/object/sign/...?token=...")
    return firmada if firmada.startswith('http') else f'{url}/storage/v1{firmada}'
