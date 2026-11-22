package com.boticompara.usuario.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Health check de Render (API.md, sección 13). Público, sin base de datos y sin datos internos. */
@RestController
public class SaludController {

    @GetMapping("/salud")
    public Map<String, String> salud() {
        return Map.of("estado", "OK");
    }
}
