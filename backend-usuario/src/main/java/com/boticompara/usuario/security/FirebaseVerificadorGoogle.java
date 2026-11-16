package com.boticompara.usuario.security;

import com.boticompara.usuario.error.ApiException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** H67: verifica el token de Firebase en el servidor (emisor, audiencia, firma y expiración). */
@Component
public class FirebaseVerificadorGoogle implements VerificadorGoogle {

    private static final Logger log = LoggerFactory.getLogger(FirebaseVerificadorGoogle.class);
    private static final String MENSAJE_NO_VERIFICADO = "No pudimos verificar tu cuenta de Google";

    private final String rutaCredenciales;
    private final String projectId;
    private FirebaseAuth auth;

    public FirebaseVerificadorGoogle(@Value("${FIREBASE_CREDENTIALS_PATH:}") String rutaCredenciales,
                                     @Value("${FIREBASE_PROJECT_ID:}") String projectId) {
        this.rutaCredenciales = rutaCredenciales;
        this.projectId = projectId;
    }

    /** Si falta la configuración, la API arranca igual y el login con Google responde 503. */
    @PostConstruct
    void iniciar() throws IOException {
        if (rutaCredenciales.isBlank() || projectId.isBlank()) {
            log.warn("FIREBASE_CREDENTIALS_PATH o FIREBASE_PROJECT_ID vacíos: el login con Google queda deshabilitado");
            return;
        }
        if (FirebaseApp.getApps().isEmpty()) {
            try (InputStream cuenta = new FileInputStream(rutaCredenciales)) {
                FirebaseOptions opciones = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(cuenta))
                        .setProjectId(projectId)
                        .build();
                FirebaseApp.initializeApp(opciones);
            }
        }
        auth = FirebaseAuth.getInstance();
    }

    @Override
    public GoogleIdentidad verificar(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw ApiException.badRequest("Falta el token de Google");
        }
        if (auth == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El inicio de sesión con Google no está configurado");
        }
        try {
            return identidadDe(auth.verifyIdToken(idToken));
        } catch (FirebaseAuthException e) {
            throw ApiException.unauthorized(MENSAJE_NO_VERIFICADO);
        }
    }

    /** Traduce las claims del token. Sin correo verificado no hay identidad. */
    static GoogleIdentidad identidadDe(FirebaseToken token) {
        String correo = token.getEmail();
        boolean verificado = Boolean.TRUE.equals(token.getClaims().get("email_verified"));
        if (correo == null || correo.isBlank() || !verificado) {
            throw ApiException.unauthorized(MENSAJE_NO_VERIFICADO);
        }
        return new GoogleIdentidad(token.getUid(), correo, token.getName(), true);
    }
}