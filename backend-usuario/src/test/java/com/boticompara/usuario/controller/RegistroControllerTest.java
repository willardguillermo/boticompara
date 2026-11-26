package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.LoginRequest;
import com.boticompara.usuario.dto.LoginResponse;
import com.boticompara.usuario.dto.RegistroRequest;
import com.boticompara.usuario.dto.RucResponse;
import com.boticompara.usuario.dto.UsuarioResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H1, H15, H20: endpoints del registro con código y de las cuentas antiguas (docs/API.md, sección 1), con la
 * seguridad real: son públicos, validan el cuerpo y responden con el formato de error. Los servicios se simulan.
 */
@WebMvcTest({AuthController.class, RegistroController.class})
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class, LimitesPorIp.class})
@TestPropertySource(properties = "jwt.secret=secreto-de-prueba-con-mas-de-treinta-y-dos-caracteres")
class RegistroControllerTest {

    private static final String TOKEN = "c".repeat(64);
    private static final String COMPRADOR = """
            {"tokenVerificacion": "%s", "nombre": "Carlos", "correo": "carlos@correo.pe",
             "password": "Boti2026!", "rol": "COMPRADOR"}""".formatted(TOKEN);

    @Autowired MockMvc mvc;

    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean AuthService auth;
    @MockitoBean RecuperacionService recuperacion;
    @MockitoBean VerificacionService verificacion;
    @MockitoBean RegistroService registro;

    private ResultActions enviar(String ruta, String cuerpo) throws Exception {
        return mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    // ---------- Paso 1: código ----------

    @Test
    void pedirCodigoEsPublicoYNoNecesitaNombre() throws Exception {
        enviar("/auth/registro/codigo", "{\"correo\": \"carlos@correo.pe\", \"nombre\": \"<a href=x>premio</a>\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Te enviamos un código a tu correo. Vence en 15 minutos"));
        verify(registro).enviarCodigo("carlos@correo.pe");
    }

    @Test
    void pedirCodigoConCorreoInvalido400() throws Exception {
        enviar("/auth/registro/codigo", "{\"correo\": \"no-es-correo\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El correo no tiene un formato válido"));
        verify(registro, never()).enviarCodigo(anyString());
    }

    @Test
    void pedirCodigoAntesDe60Segundos429ConRetryAfter() throws Exception {
        doThrow(new LimiteExcedidoException("Espera un momento antes de pedir otro código", 37))
                .when(registro).enviarCodigo(anyString());

        enviar("/auth/registro/codigo", "{\"correo\": \"carlos@correo.pe\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "37"))
                .andExpect(jsonPath("$.mensaje").value("Espera un momento antes de pedir otro código"));
    }

    // ---------- Paso 2: verificar ----------

    @Test
    void verificarDevuelveElToken() throws Exception {
        when(registro.verificarCodigo("carlos@correo.pe", "123456")).thenReturn(TOKEN);

        enviar("/auth/registro/verificar", "{\"correo\": \"carlos@correo.pe\", \"codigo\": \"123456\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenVerificacion").value(TOKEN));
    }

    @Test
    void verificarConUnCodigoQueNoTieneSeisDigitos400SinLlamarAlServicio() throws Exception {
        enviar("/auth/registro/verificar", "{\"correo\": \"carlos@correo.pe\", \"codigo\": \"12a4\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El código debe tener 6 dígitos"));
        verify(registro, never()).verificarCodigo(anyString(), anyString());
    }

    @Test
    void verificarConUnCodigoQueNoSirve400ConElMensajeUnico() throws Exception {
        when(registro.verificarCodigo(anyString(), anyString()))
                .thenThrow(ApiException.badRequest("El código no es válido o venció. Pide uno nuevo"));

        enviar("/auth/registro/verificar", "{\"correo\": \"carlos@correo.pe\", \"codigo\": \"000000\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El código no es válido o venció. Pide uno nuevo"));
    }

    // ---------- Paso 3: validar RUC sin cuenta ----------

    @Test
    void validarRucDelRegistroEsPublicoConElToken() throws Exception {
        when(registro.validarRuc(TOKEN, "20601234565")).thenReturn(
                new RucResponse("20601234565", true, "RUC válido", null, null, null, "LOCAL", null, null));

        enviar("/auth/registro/validar-ruc", "{\"tokenVerificacion\": \"%s\", \"ruc\": \"20601234565\"}".formatted(TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true))
                .andExpect(jsonPath("$.fuente").value("LOCAL"));
    }

    @Test
    void validarRucDelRegistroSinToken400() throws Exception {
        enviar("/auth/registro/validar-ruc", "{\"ruc\": \"20601234565\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Falta el token de verificación"));
    }

    @Test
    void elValidarRucConSesionSigueExigiendoAlDueno() throws Exception {
        mvc.perform(get("/boticas/validar-ruc/20601234565")).andExpect(status().isUnauthorized());
    }

    // ---------- Paso 4: registro ----------

    @Test
    void registroCreaLaCuentaYRespondeComoElLogin() throws Exception {
        when(registro.registrar(any(RegistroRequest.class))).thenReturn(new LoginResponse("jwt-nuevo", "Bearer",
                new UsuarioResponse(15L, "Carlos", "carlos@correo.pe", "COMPRADOR")));

        enviar("/auth/registro", COMPRADOR)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-nuevo"))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.usuario.id").value(15))
                .andExpect(jsonPath("$.usuario.rol").value("COMPRADOR"));
    }

    @Test
    void elRegistroViejoSinTokenYaNoCreaCuentas() throws Exception {
        enviar("/auth/registro", """
                {"nombre": "Ana", "correo": "ana@correo.pe", "password": "Clave-de-prueba-1", "rol": "COMPRADOR"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Falta el token de verificación"));
        verify(registro, never()).registrar(any());
    }

    @Test
    void laBoticaDelRegistroSeValidaComoEnPostBoticas() throws Exception {
        enviar("/auth/registro", """
                {"tokenVerificacion": "%s", "nombre": "Rosa", "correo": "rosa@correo.pe", "password": "Boti2026!",
                 "rol": "DUENO_BOTICA", "botica": {"nombreComercial": "", "ruc": "20601234565",
                 "razonSocial": "X", "direccion": "Y", "distrito": "Z", "latitud": 95}}""".formatted(TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("El nombre comercial es obligatorio")))
                .andExpect(jsonPath("$.mensaje", containsString("La latitud debe estar entre -90 y 90")));
        verify(registro, never()).registrar(any());
    }

    // ---------- Cuentas antiguas ----------

    @Test
    void confirmarCodigoDeCuentaAntigua() throws Exception {
        enviar("/auth/verificacion/confirmar", "{\"correo\": \"lucia@correo.pe\", \"codigo\": \"654321\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Correo verificado. Ya puedes iniciar sesión"));
        verify(verificacion).confirmarCodigo("lucia@correo.pe", "654321");
    }

    @Test
    void loginDeCuentaSinVerificar403ConElCodigoEnElJson() throws Exception {
        when(auth.login(any(LoginRequest.class), anyString())).thenThrow(
                new ApiException(HttpStatus.FORBIDDEN, "Verifica tu correo para entrar", "CORREO_NO_VERIFICADO"));

        enviar("/auth/login", "{\"correo\": \"lucia@correo.pe\", \"password\": \"Boti2026!\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.mensaje").value("Verifica tu correo para entrar"))
                .andExpect(jsonPath("$.codigo").value("CORREO_NO_VERIFICADO"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void losDemasErroresNoTraenElCampoCodigo() throws Exception {
        when(auth.login(any(LoginRequest.class), eq("127.0.0.1")))
                .thenThrow(ApiException.unauthorized("Correo o contraseña incorrectos"));

        enviar("/auth/login", "{\"correo\": \"lucia@correo.pe\", \"password\": \"otra\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    void elEnlaceViejoSigueRespondiendoHtml() throws Exception {
        when(verificacion.verificar("viejo")).thenReturn(true);

        mvc.perform(get("/auth/verificar").param("token", "viejo"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/html")));
    }
}
