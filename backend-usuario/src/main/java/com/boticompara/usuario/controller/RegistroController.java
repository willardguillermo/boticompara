package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.ConfirmarCodigoRequest;
import com.boticompara.usuario.dto.CorreoRequest;
import com.boticompara.usuario.dto.LoginResponse;
import com.boticompara.usuario.dto.MensajeResponse;
import com.boticompara.usuario.dto.RegistroRequest;
import com.boticompara.usuario.dto.RucRegistroRequest;
import com.boticompara.usuario.dto.RucResponse;
import com.boticompara.usuario.dto.TokenVerificacionResponse;
import com.boticompara.usuario.limite.LimitesPorIp;
import com.boticompara.usuario.service.RegistroService;
import com.boticompara.usuario.service.VerificacionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * H1, H15, H20: registro con el correo verificado por código antes de crear la cuenta, y verificación de las cuentas
 * antiguas (docs/API.md, sección 1). Público: cae en /auth/** de SecurityConfig.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class RegistroController {

    private final RegistroService registro;
    private final VerificacionService verificacion;
    private final LimitesPorIp limites;

    /**
     * Paso 1: envía el código. Gasta el límite de registros de la IP solo si el código sale: un 409, un 429 del
     * correo o un 503 lo devuelven (un 400 de validación ni llega aquí).
     */
    @PostMapping("/registro/codigo")
    public MensajeResponse pedirCodigo(@Valid @RequestBody CorreoRequest request, HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        limites.consumirRegistro(ip);
        try {
            registro.enviarCodigo(request.correo());
        } catch (RuntimeException e) {
            limites.devolverRegistro(ip);
            throw e;
        }
        return new MensajeResponse("Te enviamos un código a tu correo. Vence en 15 minutos");
    }

    /** Paso 2: el código correcto da el tokenVerificacion (un solo uso, 30 minutos). */
    @PostMapping("/registro/verificar")
    public TokenVerificacionResponse verificar(@Valid @RequestBody ConfirmarCodigoRequest request) {
        return new TokenVerificacionResponse(registro.verificarCodigo(request.correo(), request.codigo()));
    }

    /** Paso 3 (dueño, opcional): como GET /boticas/validar-ruc, pero con el token en lugar de la sesión. */
    @PostMapping("/registro/validar-ruc")
    public RucResponse validarRuc(@Valid @RequestBody RucRegistroRequest request) {
        return registro.validarRuc(request.tokenVerificacion(), request.ruc());
    }

    /** Paso 4: crea la cuenta (y la botica del dueño) y abre la sesión, con el mismo JSON que el login. */
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse registrar(@Valid @RequestBody RegistroRequest request) {
        return registro.registrar(request);
    }

    /** Cuentas antiguas sin verificar: siempre 200. El límite por IP cuenta todas las solicitudes. */
    @PostMapping("/verificacion/codigo")
    public MensajeResponse pedirCodigoCuenta(@Valid @RequestBody CorreoRequest request, HttpServletRequest http) {
        limites.consumirVerificacion(http.getRemoteAddr());
        verificacion.enviarCodigo(request.correo());
        return new MensajeResponse("Si el correo tiene una cuenta sin verificar, te enviamos un código");
    }

    @PostMapping("/verificacion/confirmar")
    public MensajeResponse confirmarCodigoCuenta(@Valid @RequestBody ConfirmarCodigoRequest request) {
        verificacion.confirmarCodigo(request.correo(), request.codigo());
        return new MensajeResponse("Correo verificado. Ya puedes iniciar sesión");
    }
}
