package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.BoticaCreadaResponse;
import com.boticompara.usuario.dto.BoticaDetalleResponse;
import com.boticompara.usuario.dto.BoticaRequest;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.BoticaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/boticas")
@RequiredArgsConstructor
public class BoticaController {

    private final BoticaService boticas;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BoticaCreadaResponse registrar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                          @Valid @RequestBody BoticaRequest request) {
        return boticas.registrar(yo.id(), request);
    }

    @GetMapping("/mia")
    public BoticaDetalleResponse mia(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return boticas.detalleMia(yo.id());
    }

    /** H7: solo permitido si la botica está RECHAZADA. */
    @PutMapping("/mia")
    public BoticaDetalleResponse corregir(@AuthenticationPrincipal UsuarioAutenticado yo,
                                          @Valid @RequestBody BoticaRequest request) {
        return boticas.corregir(yo.id(), request);
    }
}