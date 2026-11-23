package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.LoginRequest;
import com.boticompara.usuario.dto.LoginResponse;
import com.boticompara.usuario.dto.RegistroRequest;
import com.boticompara.usuario.dto.UsuarioResponse;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.auth.LimitadorLogin;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.security.GoogleIdentidad;
import com.boticompara.usuario.security.JwtService;
import com.boticompara.usuario.security.VerificadorGoogle;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final VerificadorGoogle verificador;
    private final LimitadorLogin limitador;

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

    /** Compatibilidad: sin IP conocida (tests o llamadas internas). */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest r) {
        return login(r, "desconocida");
    }

    /** H18 y SEC-05: cuenta los fallos por correo e IP antes de comparar la clave. */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest r, String ip) {
        String correo = r.correo().trim().toLowerCase(Locale.ROOT);
        limitador.verificarNoBloqueado(correo, ip);

        Usuario u = usuarios.findByCorreoIgnoreCase(correo).orElse(null);
        // Correo inexistente, cuenta sin contraseña y clave incorrecta cuentan igual: mismo 401
        if (u == null || u.getPasswordHash() == null || !encoder.matches(r.password(), u.getPasswordHash())) {
            limitador.registrarFallo(correo, ip);
            throw ApiException.unauthorized("Correo o contraseña incorrectos");
        }
        limitador.limpiar(correo, ip);

        if (!u.isActivo()) {
            throw ApiException.forbidden("Tu cuenta fue dada de baja");
        }
        if (u.estaSuspendido()) {
            throw ApiException.forbidden("Tu cuenta está suspendida hasta " + fechaLima(u.getSuspendidoHasta()));
        }
        return new LoginResponse(jwt.generar(u), "Bearer", UsuarioResponse.from(u));
    }

    /**
     * H67: login o alta con Google (API.md, sección 1).
     * El token se verifica primero. Si la cuenta ya está vinculada por google_uid, se valida su estado.
     * Si no existe, se crea solo si el correo no tiene ninguna cuenta: no se vinculan cuentas existentes.
     */
    @Transactional
    public LoginResponse loginGoogle(String idToken) {
        GoogleIdentidad g = verificador.verificar(idToken);
        Usuario u = usuarios.findByGoogleUid(g.uid())
                .orElseGet(() -> crearDesdeGoogle(g));
        if (!u.isActivo()) {
            throw ApiException.forbidden("Tu cuenta fue dada de baja");
        }
        if (u.estaSuspendido()) {
            throw ApiException.forbidden("Tu cuenta está suspendida hasta " + fechaLima(u.getSuspendidoHasta()));
        }
        return new LoginResponse(jwt.generar(u), "Bearer", UsuarioResponse.from(u));
    }

    private Usuario crearDesdeGoogle(GoogleIdentidad g) {
        String correo = g.correo().trim().toLowerCase(Locale.ROOT);
        if (usuarios.findByCorreoIgnoreCase(correo).isPresent()) {
            throw ApiException.conflict("Ya existe una cuenta con este correo. Inicia sesión con tu contraseña");
        }
        Usuario u = new Usuario();
        u.setNombre(nombreDe(g));
        u.setCorreo(correo);
        u.setPasswordHash(null);
        u.setRol("COMPRADOR");
        u.setGoogleUid(g.uid());
        u.setCorreoVerificado(true);
        return usuarios.save(u);
    }

    /** Nombre del claim name; si no viene, la parte del correo antes de la @ (API.md). */
    static String nombreDe(GoogleIdentidad g) {
        if (g.nombre() != null && !g.nombre().isBlank()) {
            return g.nombre().trim();
        }
        return g.correo().substring(0, g.correo().indexOf('@'));
    }

    /** dd/MM/yyyy en hora de Lima, como pide el contrato. */
    static String fechaLima(Instant instante) {
        return instante.atZone(ZoneId.of("America/Lima")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}