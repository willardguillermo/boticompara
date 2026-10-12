package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.LoginRequest;
import com.boticompara.usuario.dto.LoginResponse;
import com.boticompara.usuario.dto.MensajeResponse;
import com.boticompara.usuario.dto.RecuperarRequest;
import com.boticompara.usuario.dto.RegistroRequest;
import com.boticompara.usuario.dto.RestablecerRequest;
import com.boticompara.usuario.dto.UsuarioResponse;
import com.boticompara.usuario.service.AuthService;
import com.boticompara.usuario.service.RecuperacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;
    private final RecuperacionService recuperacion;

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registro(@Valid @RequestBody RegistroRequest request) {
        return auth.registrar(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    /** H17: siempre responde 200, exista o no el correo. */
    @PostMapping("/recuperar")
    public MensajeResponse recuperar(@Valid @RequestBody RecuperarRequest request) {
        recuperacion.solicitar(request.correo());
        return new MensajeResponse(
                "Si el correo está registrado, te enviamos las instrucciones para restablecer tu contraseña");
    }

    /** H17 */
    @PostMapping("/restablecer")
    public MensajeResponse restablecer(@Valid @RequestBody RestablecerRequest request) {
        recuperacion.restablecer(request.correo(), request.token(), request.password());
        return new MensajeResponse("Contraseña actualizada");
    }
}