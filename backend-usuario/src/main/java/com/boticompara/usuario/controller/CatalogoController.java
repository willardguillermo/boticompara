package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.ProductoResponse;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.CatalogoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/boticas/mia/productos")
@RequiredArgsConstructor
public class CatalogoController {

    private final CatalogoService catalogo;

    @GetMapping
    public List<ProductoResponse> listar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                         @RequestParam(required = false) String q) {
        return catalogo.listar(yo.id(), q);
    }
}
