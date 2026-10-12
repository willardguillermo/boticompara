package com.boticompara.usuario.service;

import com.boticompara.usuario.entity.TokenUsuario;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.TokenUsuarioRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/** H17: recuperación de contraseña con un código de 6 dígitos enviado por correo. */
@Service
public class RecuperacionService {

    static final String TIPO = "RECUPERACION";
    static final Duration VIGENCIA = Duration.ofMinutes(15);
    static final Duration ESPERA = Duration.ofMinutes(1);
    static final int MAX_INTENTOS = 5;

    private final UsuarioRepository usuarios;
    private final TokenUsuarioRepository tokens;
    private final PasswordEncoder encoder;
    private final CorreoService correo;
    private final byte[] clave;
    private final SecureRandom azar = new SecureRandom();

    public RecuperacionService(UsuarioRepository usuarios, TokenUsuarioRepository tokens, PasswordEncoder encoder,
                               CorreoService correo, @Value("${jwt.secret}") String secreto) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.encoder = encoder;
        this.correo = correo;
        this.clave = secreto.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Genera un código y lo envía por correo. No devuelve nada ni falla si el correo no existe, para no revelar
     * qué correos están registrados. Si ya se pidió uno hace menos de un minuto, no envía otro.
     */
    @Transactional
    public void solicitar(String correoIngresado) {
        Usuario u = usuarios.findByCorreoIgnoreCase(correoIngresado.trim()).filter(Usuario::isActivo).orElse(null);
        if (u == null) {
            return;
        }
        Instant ahora = Instant.now();
        List<TokenUsuario> pendientes = tokens.findByUsuarioIdAndTipoAndUsadoFalse(u.getId(), TIPO);
        boolean reciente = pendientes.stream()
                .anyMatch(t -> t.getExpiraEn().minus(VIGENCIA).isAfter(ahora.minus(ESPERA)));
        if (reciente) {
            return;
        }
        pendientes.forEach(t -> t.setUsado(true));
        tokens.saveAll(pendientes);

        String codigo = String.format("%06d", azar.nextInt(1_000_000));
        TokenUsuario nuevo = new TokenUsuario();
        nuevo.setUsuarioId(u.getId());
        nuevo.setToken(hash(u.getId(), codigo));
        nuevo.setTipo(TIPO);
        nuevo.setExpiraEn(ahora.plus(VIGENCIA));
        tokens.save(nuevo);

        correo.enviar(u.getCorreo(), "BotiCompara: tu código para restablecer la contraseña",
                "Hola " + u.getNombre() + ",\n\n"
                        + "Tu código para restablecer la contraseña es: " + codigo + "\n\n"
                        + "Vence en 15 minutos y solo se puede usar una vez. "
                        + "Si no lo pediste tú, ignora este mensaje: tu contraseña no cambia.\n\n"
                        + "BotiCompara\n");
    }

    /**
     * Cambia la contraseña si el código es correcto, vigente y no usado. Cada código incorrecto suma un intento;
     * al quinto el código queda invalidado. Los intentos se guardan aunque se lance el error (noRollbackFor).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void restablecer(String correoIngresado, String codigo, String nuevaPassword) {
        Usuario u = usuarios.findByCorreoIgnoreCase(correoIngresado.trim()).filter(Usuario::isActivo)
                .orElseThrow(RecuperacionService::codigoInvalido);
        Instant ahora = Instant.now();
        List<TokenUsuario> pendientes = tokens.findByUsuarioIdAndTipoAndUsadoFalse(u.getId(), TIPO);
        List<TokenUsuario> vigentes = pendientes.stream()
                .filter(t -> t.getExpiraEn().isAfter(ahora) && t.getIntentos() < MAX_INTENTOS)
                .toList();
        if (vigentes.isEmpty()) {
            throw codigoInvalido();
        }

        String esperado = hash(u.getId(), codigo.trim());
        TokenUsuario coincide = vigentes.stream().filter(t -> iguales(t.getToken(), esperado)).findFirst().orElse(null);
        if (coincide == null) {
            vigentes.forEach(t -> t.setIntentos((short) (t.getIntentos() + 1)));
            tokens.saveAll(vigentes);
            throw codigoInvalido();
        }

        u.setPasswordHash(encoder.encode(nuevaPassword));
        usuarios.save(u);
        pendientes.forEach(t -> t.setUsado(true));
        tokens.saveAll(pendientes);
    }

    /** HMAC-SHA256 con el JWT_SECRET: una filtración de la tabla no permite probar los 1.000.000 de códigos. */
    String hash(Long usuarioId, String codigo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((usuarioId + ":" + codigo).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("No se pudo calcular el hash del código", e);
        }
    }

    private static boolean iguales(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static ApiException codigoInvalido() {
        return ApiException.badRequest("El código no es válido o venció. Pide uno nuevo");
    }
}