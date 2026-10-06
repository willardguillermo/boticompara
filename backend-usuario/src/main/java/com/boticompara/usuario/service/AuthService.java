package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.LoginRequest;
import com.boticompara.usuario.dto.LoginResponse;
import com.boticompara.usuario.dto.RegistroRequest;
import com.boticompara.usuario.dto.UsuarioResponse;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    /** H1, H15: la contraseña se guarda siempre con BCrypt. */
    @Transactional
    public UsuarioResponse registrar(RegistroRequest r) {
        String correo = r.correo().trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByCorreoIgnoreCase(correo)) {
            throw ApiException.conflict("El correo ya está registrado");
        }
        Usuario u = new Usuario();
        u.setNombre(r.nombre().trim());
        u.setCorreo(correo);
        u.setPasswordHash(encoder.encode(r.password()));
        u.setTelefono(r.telefono() == null || r.telefono().isBlank() ? null : r.telefono().trim());
        u.setRol(r.rol());
        return UsuarioResponse.from(usuarios.save(u));
    }

    /** H18: devuelve un JWT que lleva el rol del usuario. */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest r) {
        Usuario u = usuarios.findByCorreoIgnoreCase(r.correo().trim())
                .filter(Usuario::isActivo)
                .orElseThrow(() -> ApiException.unauthorized("Correo o contraseña incorrectos"));
        if (!encoder.matches(r.password(), u.getPasswordHash())) {
            throw ApiException.unauthorized("Correo o contraseña incorrectos");
        }
        return new LoginResponse(jwt.generar(u), "Bearer", UsuarioResponse.from(u));
    }
}
