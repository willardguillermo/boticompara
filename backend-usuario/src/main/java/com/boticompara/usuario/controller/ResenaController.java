package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.ResenaDtos;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.ResenaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** H85-H87. El GET es público; el PUT lo restringe SecurityConfig a COMPRADOR. */
@RestController
@RequiredArgsConstructor
public class ResenaController {

    private final ResenaService resenas;

    @GetMapping("/boticas/{id}/resenas")
    public ResenaDtos.Listado listar(@PathVariable Long id,
                                     @RequestParam(defaultValue = "0") int page,
                                     @AuthenticationPrincipal(errorOnInvalidType = false) UsuarioAutenticado yo) {
        return resenas.listar(id, page, yo == null ? null : yo.id());
    }

    @PutMapping("/boticas/{id}/resenas/mia")
    public ResponseEntity<ResenaDtos.Propia> guardar(@PathVariable Long id,
                                                     @AuthenticationPrincipal UsuarioAutenticado yo,
                                                     @Valid @RequestBody ResenaDtos.Guardar r) {
        ResenaService.Guardada g = resenas.guardar(yo.id(), id, r.estrellas(), r.comentario());
        return ResponseEntity.status(g.creada() ? HttpStatus.CREATED : HttpStatus.OK).body(g.resena());
    }
}