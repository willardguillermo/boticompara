package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.ProductoRequest;
import com.boticompara.usuario.dto.ProductoResponse;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.ProductoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productos;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponse crear(@AuthenticationPrincipal UsuarioAutenticado yo,
                                  @Valid @RequestBody ProductoRequest request) {
        return productos.crear(yo.id(), request);
    }

    @PutMapping("/{id}")
    public ProductoResponse actualizar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                       @PathVariable Long id,
                                       @Valid @RequestBody ProductoRequest request) {
        return productos.actualizar(yo.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long id) {
        productos.eliminar(yo.id(), id);
    }
}
