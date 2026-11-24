package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.RegistroRequest;
import com.boticompara.usuario.dto.UsuarioResponse;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.limite.LimitesPorIp;
import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.security.JwtService;
import com.boticompara.usuario.security.SecurityConfig;
import com.boticompara.usuario.security.SecurityErrorHandler;
import com.boticompara.usuario.service.AuthService;
import com.boticompara.usuario.service.RecuperacionService;
import com.boticompara.usuario.service.VerificacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Límites por IP de POST /auth/registro y POST /auth/recuperar (API.md, sección 1), con la seguridad real.
 * Los servicios se simulan: no se toca la base ni se envían correos. Aquí el límite es 2 por hora.
 * Los contadores viven en el bean LimitesPorIp: cada prueba arranca con un contexto nuevo para empezar en cero.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class, LimitesPorIp.class})
@TestPropertySource(properties = {
        "jwt.secret=secreto-de-prueba-con-mas-de-treinta-y-dos-caracteres",
        "auth.limite.registro-por-ip-hora=2",
        "auth.limite.recuperar-por-ip-hora=2"})
class LimitesAuthTest {

    private static final String REGISTRO = """
            {"nombre": "Ana", "correo": "ana@boticompara.test", "password": "Clave-de-prueba-1", "rol": "COMPRADOR"}""";
    private static final String RECUPERAR = "{\"correo\": \"ana@boticompara.test\"}";

    @Autowired MockMvc mvc;

    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean AuthService auth;
    @MockitoBean RecuperacionService recuperacion;
    @MockitoBean VerificacionService verificacion;

    private ResultActions enviar(String ruta, String cuerpo, String ip) throws Exception {
        return mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .with(peticion -> {
                    peticion.setRemoteAddr(ip);
                    return peticion;
                }));
    }

    @Test
    void registro_alPasarElLimiteDeLaIp_429ConRetryAfterSinRegistrar() throws Exception {
        when(auth.registrar(any(RegistroRequest.class)))
                .thenReturn(new UsuarioResponse(9L, "Ana", "ana@boticompara.test", "COMPRADOR"));
        enviar("/auth/registro", REGISTRO, "1.2.3.4").andExpect(status().isCreated());
        enviar("/auth/registro", REGISTRO, "1.2.3.4").andExpect(status().isCreated());

        enviar("/auth/registro", REGISTRO, "1.2.3.4")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.mensaje")
                        .value("Hiciste demasiados registros desde esta conexión. Intenta más tarde"))
                .andExpect(jsonPath("$.timestamp").exists());
        verify(auth, times(2)).registrar(any(RegistroRequest.class));
        verify(verificacion, times(2)).enviarAlRegistrarse(9L);
    }

    @Test
    void registro_otraIpTieneSuPropioLimite() throws Exception {
        when(auth.registrar(any(RegistroRequest.class)))
                .thenReturn(new UsuarioResponse(9L, "Ana", "ana@boticompara.test", "COMPRADOR"));
        enviar("/auth/registro", REGISTRO, "1.2.3.4").andExpect(status().isCreated());
        enviar("/auth/registro", REGISTRO, "1.2.3.4").andExpect(status().isCreated());

        enviar("/auth/registro", REGISTRO, "5.6.7.8").andExpect(status().isCreated());
    }

    @Test
    void registro_losQueFallanNoGastanElLimite() throws Exception {
        when(auth.registrar(any(RegistroRequest.class))).thenThrow(ApiException.conflict("El correo ya está registrado"));
        for (int i = 0; i < 3; i++) {
            enviar("/auth/registro", REGISTRO, "1.2.3.4").andExpect(status().isConflict());
        }
        // Un 400 de validación tampoco llega a contar
        enviar("/auth/registro", "{\"correo\": \"no-es-correo\"}", "1.2.3.4").andExpect(status().isBadRequest());

        when(auth.registrar(any(RegistroRequest.class)))
                .thenReturn(new UsuarioResponse(9L, "Ana", "ana@boticompara.test", "COMPRADOR"));
        enviar("/auth/registro", REGISTRO, "1.2.3.4").andExpect(status().isCreated());
        enviar("/auth/registro", REGISTRO, "1.2.3.4").andExpect(status().isCreated());
    }

    @Test
    void recuperar_alPasarElLimiteDeLaIp_429SinBuscarElCorreo() throws Exception {
        enviar("/auth/recuperar", RECUPERAR, "1.2.3.4").andExpect(status().isOk());
        enviar("/auth/recuperar", "{\"correo\": \"otro@boticompara.test\"}", "1.2.3.4").andExpect(status().isOk());

        enviar("/auth/recuperar", RECUPERAR, "1.2.3.4")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(jsonPath("$.mensaje")
                        .value("Hiciste demasiadas solicitudes desde esta conexión. Intenta más tarde"));
        verify(recuperacion, times(2)).solicitar(anyString());
    }

    @Test
    void recuperar_noComparteElLimiteConElRegistro() throws Exception {
        enviar("/auth/recuperar", RECUPERAR, "1.2.3.4").andExpect(status().isOk());
        enviar("/auth/recuperar", RECUPERAR, "1.2.3.4").andExpect(status().isOk());
        when(auth.registrar(any(RegistroRequest.class)))
                .thenReturn(new UsuarioResponse(9L, "Ana", "ana@boticompara.test", "COMPRADOR"));

        enviar("/auth/registro", REGISTRO, "1.2.3.4").andExpect(status().isCreated());
        verify(auth, times(1)).registrar(any(RegistroRequest.class));
    }
}
