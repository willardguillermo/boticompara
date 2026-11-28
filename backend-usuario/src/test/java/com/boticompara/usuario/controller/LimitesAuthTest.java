package com.boticompara.usuario.controller;

import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.limite.LimiteExcedidoException;
import com.boticompara.usuario.limite.LimitesPorIp;
import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.security.JwtService;
import com.boticompara.usuario.security.SecurityConfig;
import com.boticompara.usuario.security.SecurityErrorHandler;
import com.boticompara.usuario.service.AuthService;
import com.boticompara.usuario.service.RecuperacionService;
import com.boticompara.usuario.service.RegistroService;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Límites por IP de POST /auth/registro/codigo, POST /auth/recuperar y POST /auth/verificacion/codigo (API.md,
 * sección 1), con la seguridad real. Los servicios se simulan: no se toca la base ni se envían correos. Aquí el límite
 * es 2 por hora. Los contadores viven en el bean LimitesPorIp: cada prueba arranca con un contexto nuevo.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest({AuthController.class, RegistroController.class})
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class, LimitesPorIp.class})
@TestPropertySource(properties = {
        "jwt.secret=secreto-de-prueba-con-mas-de-treinta-y-dos-caracteres",
        "auth.limite.registro-por-ip-hora=2",
        "auth.limite.recuperar-por-ip-hora=2",
        "auth.limite.verificacion-por-ip-hora=2"})
class LimitesAuthTest {

    private static final String CODIGO = "{\"correo\": \"ana@boticompara.test\"}";
    private static final String RECUPERAR = "{\"correo\": \"ana@boticompara.test\"}";

    @Autowired MockMvc mvc;

    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean AuthService auth;
    @MockitoBean RecuperacionService recuperacion;
    @MockitoBean VerificacionService verificacion;
    @MockitoBean RegistroService registro;

    private ResultActions enviar(String ruta, String cuerpo, String ip) throws Exception {
        return mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .with(peticion -> {
                    peticion.setRemoteAddr(ip);
                    return peticion;
                }));
    }

    @Test
    void codigoDeRegistro_alPasarElLimiteDeLaIp_429ConRetryAfterSinEnviar() throws Exception {
        enviar("/auth/registro/codigo", CODIGO, "1.2.3.4").andExpect(status().isOk());
        enviar("/auth/registro/codigo", CODIGO, "1.2.3.4").andExpect(status().isOk());

        enviar("/auth/registro/codigo", CODIGO, "1.2.3.4")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.mensaje")
                        .value("Hiciste demasiados registros desde esta conexión. Intenta más tarde"))
                .andExpect(jsonPath("$.codigo").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
        verify(registro, times(2)).enviarCodigo(anyString());
    }

    @Test
    void codigoDeRegistro_otraIpTieneSuPropioLimite() throws Exception {
        enviar("/auth/registro/codigo", CODIGO, "1.2.3.4").andExpect(status().isOk());
        enviar("/auth/registro/codigo", CODIGO, "1.2.3.4").andExpect(status().isOk());

        enviar("/auth/registro/codigo", CODIGO, "5.6.7.8").andExpect(status().isOk());
    }

    @Test
    void codigoDeRegistro_losQueFallanNoGastanElLimite() throws Exception {
        doThrow(ApiException.conflict("El correo ya está registrado")).when(registro).enviarCodigo(anyString());
        for (int i = 0; i < 3; i++) {
            enviar("/auth/registro/codigo", CODIGO, "1.2.3.4").andExpect(status().isConflict());
        }
        doThrow(new LimiteExcedidoException("Espera un momento antes de pedir otro código", 42))
                .when(registro).enviarCodigo(anyString());
        enviar("/auth/registro/codigo", CODIGO, "1.2.3.4")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"));
        doThrow(ApiException.unavailable("No pudimos enviar el correo. Intenta en unos minutos"))
                .when(registro).enviarCodigo(anyString());
        enviar("/auth/registro/codigo", CODIGO, "1.2.3.4").andExpect(status().isServiceUnavailable());
        // Un 400 de validación tampoco llega a contar
        enviar("/auth/registro/codigo", "{\"correo\": \"no-es-correo\"}", "1.2.3.4").andExpect(status().isBadRequest());

        doNothing().when(registro).enviarCodigo(anyString());
        enviar("/auth/registro/codigo", CODIGO, "1.2.3.4").andExpect(status().isOk());
        enviar("/auth/registro/codigo", CODIGO, "1.2.3.4").andExpect(status().isOk());
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

        enviar("/auth/registro/codigo", CODIGO, "1.2.3.4").andExpect(status().isOk());
        verify(registro, times(1)).enviarCodigo(anyString());
    }

    @Test
    void verificacion_cuentaTodasLasSolicitudesYNoRevelaNada() throws Exception {
        enviar("/auth/verificacion/codigo", CODIGO, "1.2.3.4")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Si el correo tiene una cuenta sin verificar, te enviamos un código"));
        enviar("/auth/verificacion/codigo", "{\"correo\": \"nadie@boticompara.test\"}", "1.2.3.4")
                .andExpect(status().isOk());

        enviar("/auth/verificacion/codigo", CODIGO, "1.2.3.4")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"));
        verify(verificacion, times(2)).enviarCodigo(anyString());
        // Su propio contador: el registro y la recuperación siguen libres
        enviar("/auth/registro/codigo", CODIGO, "1.2.3.4").andExpect(status().isOk());
        enviar("/auth/recuperar", RECUPERAR, "1.2.3.4").andExpect(status().isOk());
    }
}
