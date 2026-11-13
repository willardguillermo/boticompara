package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.ReservaRequest;
import com.boticompara.usuario.dto.ReservaResponse;
import com.boticompara.usuario.entity.EstadoReserva;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.ReservaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/** H72-H76. Solo COMPRADOR (regla en SecurityConfig). */
@RestController
@RequestMapping("/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservas;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaResponse crear(@AuthenticationPrincipal UsuarioAutenticado yo,
                                 @Valid @RequestBody ReservaRequest r) {
        return reservas.crear(yo.id(), r.productoId(), r.cantidad());
    }

    @GetMapping("/mias")
    public List<ReservaResponse> mias(@AuthenticationPrincipal UsuarioAutenticado yo,
                                      @RequestParam(required = false) String estado) {
        return reservas.misReservas(yo.id(), parsearEstado(estado));
    }

    @PatchMapping("/{id}/cancelar")
    public ReservaResponse cancelar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                    @PathVariable Long id) {
        return reservas.cancelar(yo.id(), id);
    }

    @GetMapping("/{id}")
    public ReservaResponse detalle(@AuthenticationPrincipal UsuarioAutenticado yo,
                                   @PathVariable Long id) {
        return reservas.detalle(yo.id(), id);
    }

    private EstadoReserva parsearEstado(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return EstadoReserva.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("El estado no es válido");
        }
    }
}