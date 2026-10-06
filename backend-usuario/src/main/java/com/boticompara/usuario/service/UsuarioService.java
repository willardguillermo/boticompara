package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.PerfilRequest;
import com.boticompara.usuario.dto.PerfilResponse;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarios;

    @Transactional(readOnly = true)
    public PerfilResponse obtener(Long id) {
        return PerfilResponse.from(buscar(id));
    }

    /** H16 */
    @Transactional
    public PerfilResponse actualizar(Long id, PerfilRequest r) {
        Usuario u = buscar(id);
        u.setNombre(r.nombre().trim());
        u.setTelefono(vacioANulo(r.telefono()));
        u.setDireccion(vacioANulo(r.direccion()));
        return PerfilResponse.from(usuarios.save(u));
    }

    private Usuario buscar(Long id) {
        return usuarios.findById(id)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> ApiException.unauthorized("La sesión ya no es válida"));
    }

    private String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
