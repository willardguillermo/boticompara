package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.FavoritoRequest;
import com.boticompara.usuario.dto.FavoritoResponse;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.FavoritoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** H43 y H44. Cualquier rol con sesión: SecurityConfig ya exige token en todo lo que no es público. */
@RestController
@RequestMapping("/favoritos")
@RequiredArgsConstructor
public class FavoritoController {

    private final FavoritoService favoritos;

    /** 201 si se guardó, 200 si ya estaba. */
    @PostMapping
    public ResponseEntity<FavoritoResponse> agregar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                    @Valid @RequestBody FavoritoRequest r) {
        FavoritoService.Agregado agregado = favoritos.agregar(yo.id(), r.productoId());
        return ResponseEntity.status(agregado.creado() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(agregado.favorito());
    }

    @GetMapping
    public List<FavoritoResponse> listar(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return favoritos.listar(yo.id());
    }

    @DeleteMapping("/{productoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long productoId) {
        favoritos.quitar(yo.id(), productoId);
    }
}