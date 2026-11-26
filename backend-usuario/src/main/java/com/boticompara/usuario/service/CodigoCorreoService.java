package com.boticompara.usuario.service;

import com.boticompara.usuario.entity.CodigoCorreo;
import com.boticompara.usuario.entity.CodigoCorreo.Proposito;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.limite.LimiteExcedidoException;
import com.boticompara.usuario.repository.CodigoCorreoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * H1, H20: códigos de 6 dígitos que verifican un correo (docs/API.md, sección 1) y el tokenVerificacion de un solo uso
 * del registro. En la tabla codigo_correo solo quedan hashes HMAC-SHA256 con el JWT_SECRET.
 * <p>
 * Los métodos (salvo {@link #limpiar}) NO abren su propia transacción: corren dentro de la de quien los llama
 * (RegistroService o VerificacionService). Así un intento fallido se guarda con el noRollbackFor del que llama, y un
 * correo que no se pudo enviar deshace el código recién creado.
 */
@Service
public class CodigoCorreoService {

    static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(15);
    static final Duration VIGENCIA_TOKEN = Duration.ofMinutes(30);
    static final Duration ESPERA = Duration.ofSeconds(60);
    static final Duration VENTANA = Duration.ofHours(1);
    static final int MAX_POR_HORA = 5;
    static final int MAX_INTENTOS = 5;
    /** Un código vive 15 min y su token 30 más; el límite mira la última hora. Pasado un día, la fila no sirve. */
    static final Duration CONSERVAR = Duration.ofDays(1);

    static final String MENSAJE_ESPERA = "Espera un momento antes de pedir otro código";
    static final String MENSAJE_POR_HORA = "Pediste demasiados códigos para este correo. Intenta más tarde";
    static final String MENSAJE_CODIGO = "El código no es válido o venció. Pide uno nuevo";
    static final String MENSAJE_TOKEN = "Tu verificación del correo venció. Vuelve a pedir un código";

    private final CodigoCorreoRepository codigos;
    private final byte[] clave;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();

    @Autowired
    public CodigoCorreoService(CodigoCorreoRepository codigos, @Value("${jwt.secret}") String secreto) {
        this(codigos, secreto, Clock.systemUTC());
    }

    /** Para las pruebas: permite mover el reloj. */
    CodigoCorreoService(CodigoCorreoRepository codigos, String secreto, Clock reloj) {
        this.codigos = codigos;
        this.clave = secreto.getBytes(StandardCharsets.UTF_8);
        this.reloj = reloj;
    }

    /** Correo en minúsculas y sin espacios, como lo exige el check de la tabla. */
    public static String normalizar(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Crea un código nuevo y lo devuelve en claro, para enviarlo por correo (nunca se guarda así). Anula los
     * anteriores de ese correo y propósito, y sus tokens. Se debe llamar dentro de una transacción: bloquea el correo
     * hasta el commit para que dos pedidos a la vez no se salten los límites.
     *
     * @throws LimiteExcedidoException si pasaron menos de 60 s desde el último o ya hubo 5 en la última hora
     */
    public String emitir(String correo, Proposito proposito) {
        Instant ahora = reloj.instant();
        codigos.bloquearCorreo(proposito + ":" + correo);

        List<CodigoCorreo> enLaHora = codigos.findByCorreoAndPropositoAndCreadoEnAfterOrderByIdAsc(
                correo, proposito, ahora.minus(VENTANA));
        if (!enLaHora.isEmpty()) {
            Instant ultimo = enLaHora.get(enLaHora.size() - 1).getCreadoEn();
            if (ahora.isBefore(ultimo.plus(ESPERA))) {
                throw new LimiteExcedidoException(MENSAJE_ESPERA,
                        segundos(Duration.between(ahora, ultimo.plus(ESPERA))));
            }
        }
        if (enLaHora.size() >= MAX_POR_HORA) {
            Instant libre = enLaHora.get(enLaHora.size() - MAX_POR_HORA).getCreadoEn().plus(VENTANA);
            throw new LimiteExcedidoException(MENSAJE_POR_HORA,
                    segundos(Duration.between(ahora, libre)));
        }

        codigos.anularPendientes(correo, proposito);
        String codigo = String.format("%06d", azar.nextInt(1_000_000));
        CodigoCorreo c = new CodigoCorreo();
        c.setCorreo(correo);
        c.setProposito(proposito);
        c.setCodigoHash(hashCodigo(correo, proposito, codigo));
        c.setVenceEn(ahora.plus(VIGENCIA_CODIGO));
        c.setCreadoEn(ahora);
        codigos.save(c);
        return codigo;
    }

    /**
     * Confirma el código: debe ser el último de ese correo y propósito, sin anular, sin confirmar, vigente y con
     * menos de 5 intentos fallidos. Un código incorrecto suma un intento (quien llama debe usar
     * {@code noRollbackFor = ApiException.class} para que el intento se guarde).
     *
     * @return la fila ya marcada como verificada
     * @throws ApiException 400 con un único mensaje para cualquier código que no sirva
     */
    public CodigoCorreo confirmar(String correo, Proposito proposito, String codigo) {
        Instant ahora = reloj.instant();
        CodigoCorreo c = codigos.findFirstByCorreoAndPropositoOrderByIdDesc(correo, proposito)
                .filter(x -> !x.isAnulado() && x.getVerificadoEn() == null && x.getUsadoEn() == null)
                .filter(x -> ahora.isBefore(x.getVenceEn()) && x.getIntentos() < MAX_INTENTOS)
                .orElseThrow(CodigoCorreoService::codigoInvalido);
        if (codigo == null || !iguales(c.getCodigoHash(), hashCodigo(correo, proposito, codigo.trim()))) {
            c.setIntentos((short) (c.getIntentos() + 1));
            codigos.save(c);
            throw codigoInvalido();
        }
        c.setVerificadoEn(ahora);
        return codigos.save(c);
    }

    /** Solo REGISTRO: entrega el tokenVerificacion (en claro, una sola vez) de un código ya confirmado. */
    public String emitirToken(CodigoCorreo c) {
        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        c.setTokenHash(hashToken(token));
        c.setTokenVenceEn(reloj.instant().plus(VIGENCIA_TOKEN));
        codigos.save(c);
        return token;
    }

    /**
     * El token vigente: existe, no está anulado ni gastado y no venció.
     *
     * @param bloquear true en POST /auth/registro (select ... for update hasta el commit)
     * @throws ApiException 400 si no sirve
     */
    public CodigoCorreo tokenVigente(String token, boolean bloquear) {
        if (token == null || token.isBlank()) {
            throw tokenInvalido();
        }
        String hash = hashToken(token.trim());
        Instant ahora = reloj.instant();
        return (bloquear ? codigos.bloquearPorTokenHash(hash) : codigos.findByTokenHash(hash))
                .filter(c -> c.getProposito() == Proposito.REGISTRO && !c.isAnulado() && c.getUsadoEn() == null)
                .filter(c -> c.getTokenVenceEn() != null && ahora.isBefore(c.getTokenVenceEn()))
                .orElseThrow(CodigoCorreoService::tokenInvalido);
    }

    /** Gasta el token (o cierra un código de VERIFICACION): ya no sirve para nada más. */
    public void gastar(CodigoCorreo c) {
        c.setUsadoEn(reloj.instant());
        codigos.save(c);
    }

    /** Borra las filas de más de un día. La llama LimpiarCodigosJob cada hora. */
    @Transactional
    public int limpiar() {
        return codigos.borrarCreadosAntesDe(reloj.instant().minus(CONSERVAR));
    }

    String hashCodigo(String correo, Proposito proposito, String codigo) {
        return hmac("CODIGO:" + proposito + ":" + correo + ":" + codigo);
    }

    String hashToken(String token) {
        return hmac("TOKEN:" + token);
    }

    private String hmac(String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("No se pudo calcular el hash", e);
        }
    }

    /** Segundos completos que faltan, redondeando hacia arriba y como mínimo 1 (header Retry-After). */
    static long segundos(Duration falta) {
        return Math.max(1, (falta.toMillis() + 999) / 1000);
    }

    private static boolean iguales(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    static ApiException codigoInvalido() {
        return ApiException.badRequest(MENSAJE_CODIGO);
    }

    static ApiException tokenInvalido() {
        return ApiException.badRequest(MENSAJE_TOKEN);
    }
}
