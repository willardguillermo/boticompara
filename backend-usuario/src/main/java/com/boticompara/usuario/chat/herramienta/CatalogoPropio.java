package com.boticompara.usuario.chat.herramienta;

import com.boticompara.usuario.chat.ChatProducto;
import com.boticompara.usuario.chat.ContextoChat;
import com.boticompara.usuario.dto.ProductoResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.service.BoticaService;
import com.boticompara.usuario.service.CatalogoService;
import com.boticompara.usuario.service.CondicionVentaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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
    private final CondicionVentaService condiciones;

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
        // H72: ProductoResponse no trae la condición; se lee del medicamento enlazado, en una sola consulta
        Map<Long, String> conds = condiciones.deMedicamentos(
                primeros.stream().map(ProductoResponse::medicamentoId).toList());
        return ResultadoHerramienta.de(todos.size(),
                primeros.stream().map(p -> new ProductoParaModelo(p.id(), p.nombreComercial(), p.principioActivo(),
                        p.presentacion(), p.precio(), p.stock(), botica.getNombreComercial(), botica.getDistrito(),
                        null, condicion(conds, p))).toList(),
                primeros.stream().map(p -> new ChatProducto(p.id(), p.nombreComercial(), p.principioActivo(),
                        p.presentacion(), p.precio(), p.stock(), p.stockMinimo(), botica.getId(),
                        botica.getNombreComercial(), botica.getDistrito(), null,
                        condicion(conds, p))).toList());
    }

    /** null si el producto no está enlazado al catálogo maestro o su medicamento no tiene la condición. */
    private static String condicion(Map<Long, String> conds, ProductoResponse p) {
        return p.medicamentoId() == null ? null : conds.get(p.medicamentoId());
    }
}
