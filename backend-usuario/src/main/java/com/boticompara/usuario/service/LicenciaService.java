package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.LicenciaResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.util.TipoArchivo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LicenciaService {

    static final int MAX_BYTES = 5 * 1024 * 1024;

    private final BoticaService boticaService;
    private final BoticaRepository boticas;
    private final StorageService storage;

    /**
     * H4: sube la licencia a Storage y guarda la ruta en licencia_url.
     * Si falla la subida, no se toca la base de datos.
     */
    public LicenciaResponse subir(Long usuarioId, byte[] contenido) {
        Botica botica = boticaService.obtenerMia(usuarioId);

        if (contenido == null || contenido.length == 0) {
            throw ApiException.badRequest("Debes adjuntar el archivo de la licencia");
        }
        if (contenido.length > MAX_BYTES) {
            throw ApiException.badRequest("El archivo supera el máximo de 5 MB");
        }
        TipoArchivo tipo = TipoArchivo.detectar(contenido)
                .orElseThrow(() -> ApiException.badRequest("Formato no permitido. Sube un PDF, JPG o PNG"));

        String ruta = "botica-" + botica.getId() + "/licencia." + tipo.extension();
        String anterior = botica.getLicenciaUrl();

        storage.subir(ruta, contenido, tipo.contentType());
        botica.setLicenciaUrl(ruta);
        boticas.save(botica);

        // Si cambió el formato (PDF -> PNG), se borra el archivo viejo para no dejarlo huérfano
        if (anterior != null && !anterior.isBlank() && !anterior.equals(ruta)) {
            storage.eliminar(anterior);
        }
        return new LicenciaResponse(true, "Licencia cargada correctamente");
    }
}