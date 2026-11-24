package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.RucResponse;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.RucSunatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/boticas")
@RequiredArgsConstructor
public class RucController {

    private final RucSunatService rucSunat;

    /**
     * H2: solo DUENO_BOTICA (cae en /boticas/** de SecurityConfig). Las consultas a SUNAT cuentan en CuotaSunat:
     * pasado el límite del usuario responde 429; pasado el tope global, solo la validación local.
     */
    @GetMapping("/validar-ruc/{ruc}")
    public RucResponse validar(@PathVariable String ruc, @AuthenticationPrincipal UsuarioAutenticado yo) {
        return rucSunat.consultarParaValidar(ruc, yo.id());
    }
}