package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.LoginResponse;
import com.boticompara.usuario.dto.RegistroRequest;
import com.boticompara.usuario.dto.RucResponse;
import com.boticompara.usuario.dto.UsuarioResponse;
import com.boticompara.usuario.entity.CodigoCorreo;
import com.boticompara.usuario.entity.CodigoCorreo.Proposito;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * H1, H15, H20: registro con el correo verificado ANTES de crear la cuenta (docs/API.md, sección 1).
 * Hasta POST /auth/registro no se guarda nada en usuario ni en botica: solo el código en codigo_correo.
 */
@Service
@RequiredArgsConstructor
public class RegistroService {

    static final String DUENO = "DUENO_BOTICA";
    static final String MENSAJE_SIN_CORREO = "No pudimos enviar el correo. Intenta en unos minutos";

    private final UsuarioRepository usuarios;
    private final CodigoCorreoService codigos;
    private final CorreoService correo;
    private final BoticaService boticas;
    private final RucSunatService rucSunat;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    /**
     * POST /auth/registro/codigo. 409 si el correo ya tiene cuenta; 429 por los límites del correo. El correo se envía
     * dentro de la transacción: si no sale (503), se deshace el código nuevo y el anterior sigue valiendo.
     * El texto no lleva nada que haya escrito quien lo pide: el correo todavía no está verificado.
     */
    @Transactional
    public void enviarCodigo(String correoIngresado) {
        String destino = CodigoCorreoService.normalizar(correoIngresado);
        if (usuarios.existsByCorreoIgnoreCase(destino)) {
            throw ApiException.conflict("El correo ya está registrado");
        }
        String codigo = codigos.emitir(destino, Proposito.REGISTRO);
        boolean enviado = correo.enviar(destino, "BotiCompara: tu código para crear la cuenta",
                "Hola,\n\n"
                        + "Tu código para crear tu cuenta en BotiCompara es: " + codigo + "\n\n"
                        + "Vence en 15 minutos y solo se puede usar una vez. "
                        + "Si no lo pediste tú, ignora este mensaje: sin el código no se crea ninguna cuenta.\n\n"
                        + "BotiCompara\n");
        if (!enviado) {
            throw ApiException.unavailable(MENSAJE_SIN_CORREO);
        }
    }

    /** POST /auth/registro/verificar. Los intentos fallidos se guardan aunque responda 400. */
    @Transactional(noRollbackFor = ApiException.class)
    public String verificarCodigo(String correoIngresado, String codigo) {
        CodigoCorreo c = codigos.confirmar(CodigoCorreoService.normalizar(correoIngresado), Proposito.REGISTRO, codigo);
        return codigos.emitirToken(c);
    }

    /** POST /auth/registro/validar-ruc: como GET /boticas/validar-ruc, con el límite contado por token. No lo gasta. */
    @Transactional(readOnly = true)
    public RucResponse validarRuc(String token, String ruc) {
        CodigoCorreo c = codigos.tokenVigente(token, false);
        return rucSunat.consultarParaValidar(ruc.trim(), claveCuota(c));
    }

    /**
     * POST /auth/registro: cuenta (ya verificada) y, si es dueño, botica PENDIENTE en UNA transacción. Si la botica
     * falla, la excepción deshace la cuenta y el token queda sin gastar para corregir y reintentar. La fila del token
     * se bloquea hasta el commit: dos envíos a la vez con el mismo token crean una sola cuenta.
     */
    @Transactional
    public LoginResponse registrar(RegistroRequest r) {
        CodigoCorreo c = codigos.tokenVigente(r.tokenVerificacion(), true);
        String correoCuenta = CodigoCorreoService.normalizar(r.correo());
        if (!c.getCorreo().equals(correoCuenta)) {
            throw CodigoCorreoService.tokenInvalido();
        }
        boolean dueno = DUENO.equals(r.rol());
        if (dueno && r.botica() == null) {
            throw ApiException.badRequest("Faltan los datos de la botica");
        }
        if (!dueno && r.botica() != null) {
            throw ApiException.badRequest("Un comprador no registra botica");
        }
        if (usuarios.existsByCorreoIgnoreCase(correoCuenta)) {
            throw ApiException.conflict("El correo ya está registrado");
        }

        Usuario u = new Usuario();
        u.setNombre(r.nombre().trim());
        u.setCorreo(correoCuenta);
        u.setPasswordHash(encoder.encode(r.password()));
        u.setTelefono(r.telefono() == null || r.telefono().isBlank() ? null : r.telefono().trim());
        u.setRol(r.rol());
        u.setCorreoVerificado(true);
        u = usuarios.save(u);

        if (dueno) {
            // Mismas reglas que POST /boticas (RUC, A5, A7 con este nombre, SUNAT, coordenadas)
            boticas.registrar(u.getId(), r.botica(), claveCuota(c));
        }
        codigos.gastar(c);
        return new LoginResponse(jwt.generar(u), "Bearer", UsuarioResponse.from(u));
    }

    /** Clave del límite de SUNAT "por usuario" mientras la cuenta no existe: nunca choca con un id numérico. */
    static String claveCuota(CodigoCorreo c) {
        return "registro-" + c.getId();
    }
}
