from django.contrib.admin.apps import AdminConfig


class BotiComparaAdminConfig(AdminConfig):
    """H64: el admin usa el sitio con el panel principal en la portada."""

    default_site = 'reportes.sitio.SitioAdmin'
