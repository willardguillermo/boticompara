package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.NotificacionResponse;
import com.boticompara.usuario.dto.TotalNoLeidasResponse;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** H103. Solo DUENO_BOTICA (regla en SecurityConfig). */
@RestController
@RequestMapping("/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService notificaciones;

    /** De la más reciente a la más antigua, máximo 50. soloNoLeidas se recibe como texto para responder 400 si no es true ni false. */
    @GetMapping
    public List<NotificacionResponse> listar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                             @RequestParam(required = false) String soloNoLeidas) {
        return notificaciones.listar(yo.id(), soloNoLeidas);
    }

    @GetMapping("/no-leidas/total")
    public TotalNoLeidasResponse totalNoLeidas(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return notificaciones.totalNoLeidas(yo.id());
    }

    @PatchMapping("/{id}/leida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarLeida(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long id) {
        notificaciones.marcarLeida(yo.id(), id);
    }

    @PatchMapping("/leidas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarTodasLeidas(@AuthenticationPrincipal UsuarioAutenticado yo) {
        notificaciones.marcarTodasLeidas(yo.id());
    }
}
