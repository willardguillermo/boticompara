package com.boticompara.usuario.chat.herramienta;

import com.boticompara.usuario.chat.ChatProducto;
import com.boticompara.usuario.chat.ContextoChat;
import com.boticompara.usuario.dto.ProductoResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.service.BoticaService;
import com.boticompara.usuario.service.CatalogoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * H68: catálogo del dueño para ver_stock_bajo y buscar_en_mi_catalogo. La botica sale siempre del usuario del
 * token (contexto.usuarioId): no hay forma de pedir el catálogo de otra botica.
 */
@Component
@RequiredArgsConstructor
class CatalogoPropio {

    static final String SIN_BOTICA = "sin botica registrada";

    private final BoticaService boticas;
    private final CatalogoService catalogo;

    ResultadoHerramienta listar(ContextoChat contexto, String texto, boolean stockBajo) {
        Botica botica;
        try {
            botica = boticas.obtenerMia(contexto.usuarioId());
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                // No es un error del pedido: el asistente le explica al dueño que aún no registró su botica
                return ResultadoHerramienta.error(SIN_BOTICA);
            }
            throw e;
        }
        List<ProductoResponse> todos = catalogo.listar(contexto.usuarioId(), texto, stockBajo);
        List<ProductoResponse> primeros = todos.stream().limit(Herramienta.MAX_RESULTADOS).toList();
        return ResultadoHerramienta.de(todos.size(),
                primeros.stream().map(p -> new ProductoParaModelo(p.id(), p.nombreComercial(), p.principioActivo(),
                        p.presentacion(), p.precio(), p.stock(), botica.getNombreComercial(), botica.getDistrito(),
                        null)).toList(),
                primeros.stream().map(p -> new ChatProducto(p.id(), p.nombreComercial(), p.principioActivo(),
                        p.presentacion(), p.precio(), p.stock(), p.stockMinimo(), botica.getId(),
                        botica.getNombreComercial(), botica.getDistrito(), null)).toList());
    }
}
