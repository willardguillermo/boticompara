package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.PerfilRequest;
import com.boticompara.usuario.dto.PerfilResponse;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuarios/me")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarios;

    @GetMapping
    public PerfilResponse ver(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return usuarios.obtener(yo.id());
    }

    @PutMapping
    public PerfilResponse editar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                 @Valid @RequestBody PerfilRequest request) {
        return usuarios.actualizar(yo.id(), request);
    }

    /** H19: anonimiza la cuenta. El token usado deja de ser válido. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@AuthenticationPrincipal UsuarioAutenticado yo) {
        usuarios.eliminar(yo.id());
    }
}