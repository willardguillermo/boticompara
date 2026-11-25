package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.LoginRequest;
import com.boticompara.usuario.limite.LimitesPorIp;
import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.security.JwtService;
import com.boticompara.usuario.security.SecurityConfig;
import com.boticompara.usuario.security.SecurityErrorHandler;
import com.boticompara.usuario.service.AuthService;
import com.boticompara.usuario.service.RecuperacionService;
import com.boticompara.usuario.service.VerificacionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S4-13: POST /auth/login valida el cuerpo; un campo nulo o vacío da 400 con el formato de error, no 500. */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class, LimitesPorIp.class})
@TestPropertySource(properties = "jwt.secret=secreto-de-prueba-con-mas-de-treinta-y-dos-caracteres")
class LoginValidacionTest {

    @Autowired MockMvc mvc;

    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean AuthService auth;
    @MockitoBean RecuperacionService recuperacion;
    @MockitoBean VerificacionService verificacion;

    private ResultActions login(String cuerpo) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"password\": \"Boti2026!\"}",
            "{\"correo\": null, \"password\": \"Boti2026!\"}",
            "{\"correo\": \"  \", \"password\": \"Boti2026!\"}",
    })
    void sinCorreo_400SinLlamarAlServicio(String cuerpo) throws Exception {
        login(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje").value("El correo es obligatorio"));
        verify(auth, never()).login(any(LoginRequest.class), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"correo\": \"rosa@boticompara.test\"}",
            "{\"correo\": \"rosa@boticompara.test\", \"password\": null}",
            "{\"correo\": \"rosa@boticompara.test\", \"password\": \"\"}",
    })
    void sinContrasena_400SinLlamarAlServicio(String cuerpo) throws Exception {
        login(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La contraseña es obligatoria"));
        verify(auth, never()).login(any(LoginRequest.class), anyString());
    }

    @Test
    void cuerpoVacio_400ConLosDosMensajes() throws Exception {
        login("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("El correo es obligatorio")))
                .andExpect(jsonPath("$.mensaje").value(containsString("La contraseña es obligatoria")));
        verify(auth, never()).login(any(LoginRequest.class), anyString());
    }
}
