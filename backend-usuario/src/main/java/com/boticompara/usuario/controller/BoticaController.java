package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.BoticaCreadaResponse;
import com.boticompara.usuario.dto.BoticaDetalleResponse;
import com.boticompara.usuario.dto.BoticaRequest;
import com.boticompara.usuario.dto.LicenciaResponse;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.BoticaService;
import com.boticompara.usuario.service.LicenciaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/boticas")
@RequiredArgsConstructor
public class BoticaController {

    private final BoticaService boticas;
    private final LicenciaService licencias;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BoticaCreadaResponse registrar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                          @Valid @RequestBody BoticaRequest request) {
        return boticas.registrar(yo.id(), request);
    }

    @GetMapping("/mia")
    public BoticaDetalleResponse mia(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return boticas.detalleMia(yo.id());
    }

    /** H7: solo permitido si la botica está RECHAZADA. */
    @PutMapping("/mia")
    public BoticaDetalleResponse corregir(@AuthenticationPrincipal UsuarioAutenticado yo,
                                          @Valid @RequestBody BoticaRequest request) {
        return boticas.corregir(yo.id(), request);
    }

    /** H4: licencia de funcionamiento (PDF, JPG o PNG, máximo 5 MB). */
    @PostMapping("/mia/licencia")
    public LicenciaResponse subirLicencia(@AuthenticationPrincipal UsuarioAutenticado yo,
                                          @RequestParam("archivo") MultipartFile archivo) {
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("No se pudo leer el archivo");
        }
        return licencias.subir(yo.id(), contenido);
    }
}