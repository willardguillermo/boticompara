package com.boticompara.usuario.service;

import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.Producto;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.ProductoRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertaStockService {

    private final BoticaRepository boticas;
    private final UsuarioRepository usuarios;
    private final ProductoRepository productos;
    private final CorreoService correo;

    /**
     * H12: envía un correo a cada dueño de botica APROBADA que tenga productos activos con stock <= stockMinimo.
     * Una botica sin productos bajos no recibe nada. Si un correo falla, se sigue con las demás.
     *
     * @return cantidad de correos enviados con éxito
     */
    @Transactional(readOnly = true)
    public int enviarAlertas() {
        int enviados = 0;
        for (Botica botica : boticas.findByEstado("APROBADO")) {
            Usuario dueno = usuarios.findById(botica.getUsuarioId()).filter(Usuario::isActivo).orElse(null);
            if (dueno == null) {
                continue;
            }
            List<Producto> bajos = productos.findByBoticaIdAndActivoTrueOrderByNombreComercialAsc(botica.getId())
                    .stream()
                    .filter(p -> p.getStock() <= p.getStockMinimo())
                    .toList();
            if (bajos.isEmpty()) {
                continue;
            }
            if (correo.enviar(dueno.getCorreo(), asunto(botica, bajos.size()), cuerpo(dueno, botica, bajos))) {
                enviados++;
            }
        }
        log.info("Alerta de stock bajo: {} correo(s) enviado(s)", enviados);
        return enviados;
    }

    private String asunto(Botica botica, int cantidad) {
        String que = cantidad == 1 ? "1 producto con stock bajo" : cantidad + " productos con stock bajo";
        return "BotiCompara: " + que + " en " + botica.getNombreComercial();
    }

    private String cuerpo(Usuario dueno, Botica botica, List<Producto> bajos) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hola ").append(dueno.getNombre()).append(",\n\n");
        sb.append("Estos productos de ").append(botica.getNombreComercial())
                .append(" tienen el stock en o por debajo de su mínimo:\n\n");
        for (Producto p : bajos) {
            sb.append("- ").append(p.getNombreComercial()).append(" (").append(p.getPresentacion())
                    .append("): stock ").append(p.getStock())
                    .append(", mínimo ").append(p.getStockMinimo()).append("\n");
        }
        sb.append("\nReabastécelos a tiempo para no perder ventas.\n\nBotiCompara\n");
        return sb.toString();
    }
}