package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.NombreCorto;
import com.boticompara.usuario.dto.ResenaDtos;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.Resena;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.ReservaRepository;
import com.boticompara.usuario.repository.ResenaRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** H85-H87: reseñas de boticas (API.md, sección 11). */
@Service
@RequiredArgsConstructor
public class ResenaService {

    static final int TAMANO_PAGINA = 10;
    static final int COMENTARIO_MAX = 500;

    private final BoticaRepository boticas;
    private final ReservaRepository reservas;
    private final ResenaRepository resenas;
    private final UsuarioRepository usuarios;

    /** H87: lista pública de reseñas no ocultas. miResena solo si viene un comprador con reseña. */
    @Transactional(readOnly = true)
    public ResenaDtos.Listado listar(Long boticaId, int page, Long usuarioId) {
        if (page < 0) {
            throw ApiException.badRequest("La página no puede ser negativa");
        }
        boticaAprobada(boticaId);

        Object[] res = resenas.resumen(boticaId).get(0);
        long total = ((Number) res[1]).longValue();
        BigDecimal promedio = total == 0 ? null
                : BigDecimal.valueOf(((Number) res[0]).longValue())
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);

        var pagina = resenas.findByBoticaIdAndOcultaFalse(boticaId,
                PageRequest.of(page, TAMANO_PAGINA, Sort.by(Sort.Direction.DESC, "creadoEn")));
        Map<Long, Usuario> autores = usuarios.findAllById(
                        pagina.getContent().stream().map(Resena::getUsuarioId).distinct().toList())
                .stream().collect(Collectors.toMap(Usuario::getId, Function.identity()));

        List<ResenaDtos.Item> items = pagina.getContent().stream()
                .map(r -> {
                    Usuario u = autores.get(r.getUsuarioId());
                    return new ResenaDtos.Item(r.getId(), NombreCorto.de(u == null ? null : u.getNombre()),
                            r.getEstrellas(), r.getComentario(), r.getRespuesta(), r.getRespondidaEn(),
                            r.getCreadoEn(), r.getActualizadoEn());
                })
                .toList();

        ResenaDtos.Propia mia = usuarioId == null ? null
                : resenas.findByUsuarioIdAndBoticaId(usuarioId, boticaId).map(this::propia).orElse(null);

        return new ResenaDtos.Listado(boticaId, promedio, total, page, pagina.getTotalPages(), items, mia);
    }


    public record Guardada(boolean creada, ResenaDtos.Propia resena) {
    }
    /** H85-H86: crea la reseña del comprador o la actualiza. Devuelve true si la creó. */
    @Transactional
    public Guardada guardar(Long usuarioId, Long boticaId, Integer estrellas, String comentarioCrudo) {
        boticaAprobada(boticaId);
        if (estrellas == null || estrellas < 1 || estrellas > 5) {
            throw ApiException.badRequest("Las estrellas deben ser un número de 1 a 5");
        }
        String comentario = comentarioCrudo == null ? null : comentarioCrudo.trim();
        if (comentario != null && comentario.length() > COMENTARIO_MAX) {
            throw ApiException.badRequest("El comentario no puede pasar de 500 caracteres");
        }
        if (comentario != null && comentario.isEmpty()) {
            comentario = null;
        }
        if (!reservas.hayEntregadaDe(usuarioId, boticaId)) {
            throw ApiException.forbidden("Solo puedes reseñar boticas donde ya recogiste una reserva");
        }

        var existente = resenas.findByUsuarioIdAndBoticaId(usuarioId, boticaId);
        Resena r = existente.orElseGet(Resena::new);
        boolean creada = existente.isEmpty();
        if (creada) {
            r.setUsuarioId(usuarioId);
            r.setBoticaId(boticaId);
        }

        r.setEstrellas(estrellas.shortValue());
        r.setComentario(comentario);
        // La respuesta del dueño (H88) se conserva al actualizar; no se toca aquí
        resenas.saveAndFlush(r);   // el flush deja listos el id y actualizadoEn para la respuesta
        return new Guardada(creada, propia(r));
    }

    private void boticaAprobada(Long boticaId) {
        Botica b = boticas.findById(boticaId).orElse(null);
        if (b == null || !"APROBADO".equals(b.getEstado())) {
            throw ApiException.notFound("La botica no existe o no está aprobada");
        }
    }

    private ResenaDtos.Propia propia(Resena r) {
        return new ResenaDtos.Propia(r.getId(), r.getBoticaId(), r.getEstrellas(), r.getComentario(),
                r.getRespuesta(), r.getRespondidaEn(), r.isOculta(), r.getCreadoEn(), r.getActualizadoEn());
    }
}