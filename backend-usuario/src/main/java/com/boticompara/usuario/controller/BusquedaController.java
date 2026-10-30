package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.BusquedaResponse;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.BusquedaService;
import com.boticompara.usuario.service.RegistroBusquedaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.boticompara.usuario.dto.ProductoDetalleResponse;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class BusquedaController {

    private final BusquedaService busqueda;
    private final RegistroBusquedaService registro;

    /**
     * H22-H24, H27. radioKm se recibe como texto para distinguir "no enviado" (sin filtro) de "vacío" (5 km).
     * H62: si la búsqueda pasa las validaciones, se registra con la cantidad de resultados que se devuelven
     * (ya con el filtro de radio). Las búsquedas que responden 400 no llegan a registrarse.
     */
    @GetMapping("/buscar")
    public List<BusquedaResponse> buscar(@RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "nombre") String tipo,
                                         @RequestParam(required = false) String orden,
                                         @RequestParam(required = false) Double lat,
                                         @RequestParam(required = false) Double lng,
                                         @RequestParam(required = false) String radioKm,
                                         @AuthenticationPrincipal UsuarioAutenticado yo) {
        List<BusquedaResponse> resultado = busqueda.buscar(q, tipo, orden, lat, lng, radioKm);
        registro.registrar(q, tipo, resultado.size(), yo == null ? null : yo.id());
        return resultado;
    }

    /** H28: público (ver SecurityConfig). Un id no numérico responde 400 por GlobalExceptionHandler. */
    @GetMapping("/{id}")
    public ProductoDetalleResponse detalle(@PathVariable Long id) {
        return busqueda.detalle(id);
    }
}