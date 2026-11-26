package com.boticompara.usuario.service;

import com.boticompara.usuario.entity.CodigoCorreo;
import com.boticompara.usuario.entity.CodigoCorreo.Proposito;
import com.boticompara.usuario.entity.TokenUsuario;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.limite.LimiteExcedidoException;
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
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * H20: verificación del correo de las cuentas que ya existían. Las cuentas nuevas nacen verificadas (RegistroService).
 * El enlace de 48 h ya no se envía; {@link #verificar} sigue atendiendo los enlaces enviados antes del cambio.
 */
@Slf4j
@Service
public class VerificacionService {

    static final String TIPO = "VERIFICACION";
    static final Duration VIGENCIA = Duration.ofHours(48);

    private final UsuarioRepository usuarios;
    private final TokenUsuarioRepository tokens;
    private final CorreoService correo;
    private final CodigoCorreoService codigos;
    private final byte[] clave;

    public VerificacionService(UsuarioRepository usuarios, TokenUsuarioRepository tokens, CorreoService correo,
                               CodigoCorreoService codigos, @Value("${jwt.secret}") String secreto) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.correo = correo;
        this.codigos = codigos;
        this.clave = secreto.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * POST /auth/verificacion/codigo (H20): cuentas antiguas con el correo sin verificar. Solo envía el código si el
     * correo es de una cuenta activa, con contraseña y sin verificar; si no, o si el correo pasó sus límites (60 s,
     * 5 por hora), no hace nada. Nunca lanza por eso: el endpoint siempre responde 200, para no revelar cuentas.
     */
    @Transactional
    public void enviarCodigo(String correoIngresado) {
        String destino = CodigoCorreoService.normalizar(correoIngresado);
        Usuario u = usuarios.findByCorreoIgnoreCase(destino)
                .filter(x -> x.isActivo() && x.getPasswordHash() != null && !x.isCorreoVerificado())
                .orElse(null);
        if (u == null) {
            return;
        }
        String codigo;
        try {
            codigo = codigos.emitir(destino, Proposito.VERIFICACION);
        } catch (LimiteExcedidoException e) {
            log.info("Verificación: el correo de la cuenta {} pasó su límite de códigos", u.getId());
            return;
        }
        // Si el correo no sale, CorreoService ya lo deja en el log; la respuesta sigue siendo 200
        correo.enviar(u.getCorreo(), "BotiCompara: tu código para verificar el correo",
                "Hola " + u.getNombre() + ",\n\n"
                        + "Tu código para verificar tu correo en BotiCompara es: " + codigo + "\n\n"
                        + "Vence en 15 minutos y solo se puede usar una vez. "
                        + "Si no lo pediste tú, ignora este mensaje.\n\n"
                        + "BotiCompara\n");
    }

    /**
     * POST /auth/verificacion/confirmar (H20): con el código correcto marca correoVerificado = true. No abre sesión.
     * Los intentos fallidos se guardan aunque responda 400.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void confirmarCodigo(String correoIngresado, String codigo) {
        String destino = CodigoCorreoService.normalizar(correoIngresado);
        CodigoCorreo fila = codigos.confirmar(destino, Proposito.VERIFICACION, codigo);
        Usuario u = usuarios.findByCorreoIgnoreCase(destino).filter(Usuario::isActivo)
                .orElseThrow(CodigoCorreoService::codigoInvalido);
        u.setCorreoVerificado(true);
        usuarios.save(u);
        codigos.gastar(fila);
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