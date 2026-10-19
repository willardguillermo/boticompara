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
     * H2: público. Solo con sesión de dueño de botica se consulta SUNAT; sin sesión (o con otro rol)
     * se responde solo con la validación local, para que nadie pueda gastar la cuota del servicio.
     */
    @GetMapping("/validar-ruc/{ruc}")
    public RucResponse validar(@PathVariable String ruc, @AuthenticationPrincipal UsuarioAutenticado yo) {
        if (yo == null || !"DUENO_BOTICA".equals(yo.rol())) {
            return rucSunat.verificarLocal(ruc);
        }
        return rucSunat.verificar(ruc);
    }
}