package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.RucResponse;
import com.boticompara.usuario.util.RucValidator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/boticas")
public class RucController {

    @GetMapping("/validar-ruc/{ruc}")
    public RucResponse validar(@PathVariable String ruc) {
        return RucValidator.validar(ruc);
    }
}
