package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.PerfilRequest;
import com.boticompara.usuario.dto.PerfilResponse;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.ProductoRepository;
import com.boticompara.usuario.repository.TokenUsuarioRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final BoticaRepository boticas;
    private final ProductoRepository productos;
    private final TokenUsuarioRepository tokens;
    private final FavoritoService favoritos;
    private final PasswordEncoder encoder;

    private final SecureRandom azar = new SecureRandom();

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

    /**
     * H19: elimina la cuenta anonimizando los datos (la fila se conserva) y da de baja los productos de su
     * botica. Los códigos y tokens pendientes también quedan sin efecto.
     * D1: también borra sus favoritos (como la fila del usuario no se borra, el on delete cascade no actúa).
     */
    @Transactional
    public void eliminar(Long id) {
        Usuario u = buscar(id);
        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);

        u.setNombre("Usuario eliminado");
        u.setCorreo("eliminado-" + id + "@boticompara.local");
        u.setTelefono(null);
        u.setDireccion(null);
        // Contraseña aleatoria que nadie conoce: la cuenta no vuelve a poder iniciar sesión
        u.setPasswordHash(encoder.encode(HexFormat.of().formatHex(bytes)));
        u.setActivo(false);
        usuarios.save(u);

        boticas.findByUsuarioId(id).ifPresent(b -> productos.desactivarPorBotica(b.getId()));
        tokens.invalidarPendientes(id);
        favoritos.quitarTodos(id);
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