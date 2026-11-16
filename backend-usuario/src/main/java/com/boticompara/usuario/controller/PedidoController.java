package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.CambioEstadoPedidoRequest;
import com.boticompara.usuario.dto.PedidoResponse;
import com.boticompara.usuario.dto.RechazoPedidoRequest;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** H77-H81. Solo DUENO_BOTICA (regla en SecurityConfig). */
@RestController
@RequestMapping("/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidos;

    @GetMapping
    public List<PedidoResponse> listar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                       @RequestParam(required = false) String estado) {
        return pedidos.listar(yo.id(), estado);
    }

    @GetMapping("/{id}")
    public PedidoResponse detalle(@AuthenticationPrincipal UsuarioAutenticado yo,
                                  @PathVariable Long id) {
        return pedidos.detalle(yo.id(), id);
    }

    @PatchMapping("/{id}/estado")
    public PedidoResponse cambiarEstado(@AuthenticationPrincipal UsuarioAutenticado yo,
                                        @PathVariable Long id,
                                        @Valid @RequestBody CambioEstadoPedidoRequest r) {
        return pedidos.cambiarEstado(yo.id(), id, r.estado());
    }

    @PatchMapping("/{id}/rechazar")
    public PedidoResponse rechazar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                   @PathVariable Long id,
                                   @RequestBody RechazoPedidoRequest r) {
        return pedidos.rechazar(yo.id(), id, r.motivo());
    }
}