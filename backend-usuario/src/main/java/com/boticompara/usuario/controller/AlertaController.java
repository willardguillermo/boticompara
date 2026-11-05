package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.AlertaResponse;
import com.boticompara.usuario.dto.ResumenAlertasResponse;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.AlertaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** H59. Cualquier rol con sesión: SecurityConfig ya exige token en todo lo que no es público. */
@RestController
@RequestMapping("/alertas")
@RequiredArgsConstructor
public class AlertaController {

    private final AlertaService alertas;

    /** De la más reciente a la más antigua, máximo 100. soloNoLeidas se recibe como texto para responder 400 si no es true ni false. */
    @GetMapping
    public List<AlertaResponse> listar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                       @RequestParam(required = false) String soloNoLeidas) {
        return alertas.listar(yo.id(), soloNoLeidas);
    }

    @GetMapping("/resumen")
    public ResumenAlertasResponse resumen(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return alertas.resumen(yo.id());
    }

    @PutMapping("/{id}/leida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarLeida(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long id) {
        alertas.marcarLeida(yo.id(), id);
    }

    @PutMapping("/leidas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarTodasLeidas(@AuthenticationPrincipal UsuarioAutenticado yo) {
        alertas.marcarTodasLeidas(yo.id());
    }
}