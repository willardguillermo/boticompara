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
import com.boticompara.usuario.service.VerificacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;
    private final RecuperacionService recuperacion;
    private final VerificacionService verificacion;

    /** H1, H15, H20: el registro se guarda primero; el correo de verificación se manda después y nunca lo hace fallar. */
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registro(@Valid @RequestBody RegistroRequest request) {
        UsuarioResponse nuevo = auth.registrar(request);
        verificacion.enviarAlRegistrarse(nuevo.id());
        return nuevo;
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

    /** H20: enlace del correo. Responde una página HTML, no JSON (única excepción al formato de error). */
    @GetMapping("/verificar")
    public ResponseEntity<String> verificar(@RequestParam(required = false) String token) {
        boolean ok = verificacion.verificar(token);
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .body(pagina(ok));
    }

    private static String pagina(boolean ok) {
        String titulo = ok ? "Correo verificado" : "Enlace no válido";
        String mensaje = ok
                ? "Tu correo quedó verificado. Ya puedes cerrar esta ventana y volver a BotiCompara."
                : "El enlace no es válido o ya venció.";
        String color = ok ? "#1b7f5c" : "#b3261e";
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s - BotiCompara</title>
                <style>
                body{font-family:Arial,Helvetica,sans-serif;background:#f4f7f6;color:#1f2d2b;display:flex;min-height:100vh;align-items:center;justify-content:center;margin:0}
                main{background:#fff;padding:32px 40px;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.1);max-width:420px;text-align:center}
                h1{margin:0 0 12px;font-size:24px;color:%s}
                p{margin:0;line-height:1.5}
                </style>
                </head>
                <body>
                <main>
                <h1>%s</h1>
                <p>%s</p>
                </main>
                </body>
                </html>
                """.formatted(titulo, color, titulo, mensaje);
    }
}