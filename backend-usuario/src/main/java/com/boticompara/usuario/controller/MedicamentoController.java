package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.MedicamentoResponse;
import com.boticompara.usuario.service.MedicamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/medicamentos")
@RequiredArgsConstructor
public class MedicamentoController {

    private final MedicamentoService medicamentos;

    /** H31: selector del catálogo maestro al crear o editar un producto. Requiere sesión (cualquier rol). */
    @GetMapping
    public List<MedicamentoResponse> buscar(@RequestParam(required = false) String q) {
        return medicamentos.buscar(q);
    }
}
