package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.BusquedaResponse;
import com.boticompara.usuario.service.BusquedaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class BusquedaController {

    private final BusquedaService busqueda;

    @GetMapping("/buscar")
    public List<BusquedaResponse> buscar(@RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "nombre") String tipo,
                                         @RequestParam(defaultValue = "precio_asc") String orden) {
        return busqueda.buscar(q, tipo, orden);
    }
}
