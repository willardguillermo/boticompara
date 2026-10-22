package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.AlternativasResponse;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.AlternativasService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class AlternativasController {

    private final AlternativasService alternativas;

    /**
     * H36: público (ver SecurityConfig). Un id no numérico responde 400 por GlobalExceptionHandler.
     * H42: si la petición trae un token válido, la consulta se registra con ese usuario; si no, sin usuario.
     */
    @GetMapping("/{id}/alternativas")
    public AlternativasResponse alternativas(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado yo) {
        return alternativas.alternativas(id, yo == null ? null : yo.id());
    }
}