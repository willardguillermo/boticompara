package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.ResultadoCarga;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.CargaMasivaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class CargaMasivaController {

    private final CargaMasivaService carga;

    /** H11: CSV con los productos. Campo multipart 'archivo'. */
    @PostMapping("/carga-masiva")
    public ResultadoCarga cargar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                 @RequestParam("archivo") MultipartFile archivo) {
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("No se pudo leer el archivo");
        }
        return carga.cargar(yo.id(), contenido);
    }
}