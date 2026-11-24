package com.boticompara.usuario.chat;

import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * H68: quién puede usar el asistente (API.md, sección 8). La cuota de Gemini es una sola para todo el backend, así
 * que se exige una cuenta comprobada: el dueño, con su botica APROBADO; el comprador, con el correo verificado.
 * Se revisa antes que el cuerpo y que LimitadorChat, así un 403 no cuenta en los límites. Solo lee el estado de la
 * botica y el indicador del correo: el nombre y el correo nunca salen de aquí.
 */
@Component
@RequiredArgsConstructor
public class AccesoChat {

    static final String BOTICA_NO_APROBADA = "El asistente se activa cuando tu botica sea aprobada";
    static final String CORREO_SIN_VERIFICAR = "Verifica tu correo para usar el asistente";

    private final UsuarioRepository usuarios;
    private final BoticaRepository boticas;

    /** @throws ApiException 403 si la cuenta todavía no puede usar el asistente */
    @Transactional(readOnly = true)
    public void exigir(UsuarioAutenticado yo) {
        if ("DUENO_BOTICA".equals(yo.rol())) {
            boolean aprobada = boticas.findByUsuarioId(yo.id())
                    .map(b -> "APROBADO".equals(b.getEstado()))
                    .orElse(false);
            if (!aprobada) {
                throw ApiException.forbidden(BOTICA_NO_APROBADA);
            }
            return;
        }
        boolean verificado = usuarios.findById(yo.id())
                .map(u -> u.isCorreoVerificado())
                .orElse(false);
        if (!verificado) {
            throw ApiException.forbidden(CORREO_SIN_VERIFICAR);
        }
    }
}
