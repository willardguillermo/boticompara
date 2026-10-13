package com.boticompara.usuario.service;

import com.boticompara.usuario.entity.TokenUsuario;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.repository.TokenUsuarioRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/** H20: verificación del correo con un enlace que llega al registrarse. */
@Slf4j
@Service
public class VerificacionService {

    static final String TIPO = "VERIFICACION";
    static final Duration VIGENCIA = Duration.ofHours(48);

    private final UsuarioRepository usuarios;
    private final TokenUsuarioRepository tokens;
    private final CorreoService correo;
    private final byte[] clave;
    private final String baseUrl;
    private final SecureRandom azar = new SecureRandom();

    public VerificacionService(UsuarioRepository usuarios, TokenUsuarioRepository tokens, CorreoService correo,
                               @Value("${jwt.secret}") String secreto,
                               @Value("${app.base-url:http://localhost:8080/api}") String baseUrl) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.correo = correo;
        this.clave = secreto.getBytes(StandardCharsets.UTF_8);
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Crea el token y envía el enlace. No lanza nunca: el registro ya está guardado y no debe fallar si el
     * correo o el guardado del token fallan. A propósito NO es @Transactional: cada llamada al repositorio
     * usa su propia transacción, así un error aquí no puede deshacer el registro.
     */
    public void enviarAlRegistrarse(Long usuarioId) {
        try {
            Usuario u = usuarios.findById(usuarioId).orElse(null);
            if (u == null) {
                return;
            }
            byte[] bytes = new byte[32];
            azar.nextBytes(bytes);
            String token = HexFormat.of().formatHex(bytes);

            TokenUsuario t = new TokenUsuario();
            t.setUsuarioId(u.getId());
            t.setToken(hash(token));
            t.setTipo(TIPO);
            t.setExpiraEn(Instant.now().plus(VIGENCIA));
            tokens.save(t);

            String enlace = baseUrl + "/auth/verificar?token=" + token;
            correo.enviar(u.getCorreo(), "BotiCompara: confirma tu correo",
                    "Hola " + u.getNombre() + ",\n\n"
                            + "Gracias por registrarte en BotiCompara. Confirma tu correo abriendo este enlace:\n\n"
                            + enlace + "\n\n"
                            + "El enlace vence en 48 horas. Si no creaste esta cuenta, ignora este mensaje.\n\n"
                            + "BotiCompara\n");
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el correo de verificacion al usuario {}: {}", usuarioId, e.getMessage());
        }
    }

    /**
     * Marca el correo como verificado si el token es válido, vigente y no usado.
     * Si la cuenta ya estaba verificada devuelve true (por si se abre el enlace dos veces).
     */
    @Transactional
    public boolean verificar(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        TokenUsuario t = tokens.findByTokenAndTipo(hash(token.trim()), TIPO).orElse(null);
        if (t == null) {
            return false;
        }
        Usuario u = usuarios.findById(t.getUsuarioId()).filter(Usuario::isActivo).orElse(null);
        if (u == null) {
            return false;
        }
        if (u.isCorreoVerificado()) {
            return true;
        }
        if (t.isUsado() || !t.getExpiraEn().isAfter(Instant.now())) {
            return false;
        }
        u.setCorreoVerificado(true);
        usuarios.save(u);
        t.setUsado(true);
        tokens.save(t);
        return true;
    }

    /** HMAC-SHA256 con el JWT_SECRET: en la base de datos nunca queda el token en claro. */
    String hash(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((TIPO + ":" + token).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("No se pudo calcular el hash del token", e);
        }
    }
}