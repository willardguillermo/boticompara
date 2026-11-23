package com.boticompara.usuario.security;

import com.boticompara.usuario.auth.LimitadorLogin;
import com.boticompara.usuario.limite.LimitesPorIp;
import com.boticompara.usuario.controller.AuthController;
import com.boticompara.usuario.controller.UsuarioController;
import com.boticompara.usuario.dto.PerfilResponse;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.service.AuthService;
import com.boticompara.usuario.service.RecuperacionService;
import com.boticompara.usuario.service.UsuarioService;
import com.boticompara.usuario.service.VerificacionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring Boot no debe crear su usuario en memoria con contraseña generada, y las tres entradas de verdad siguen
 * funcionando: login con contraseña, login con Google y JWT. La base y Firebase se simulan.
 */
@WebMvcTest({AuthController.class, UsuarioController.class})
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class, AuthService.class, LimitadorLogin.class,
        LimitesPorIp.class})
@TestPropertySource(properties = "jwt.secret=secreto-de-prueba-con-mas-de-treinta-y-dos-caracteres")
class SinUsuarioPorDefectoTest {

    private static final String CORREO = "carlos@boticompara.test";

    @Autowired ApplicationContext contexto;
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;

    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean VerificadorGoogle google;
    @MockitoBean UsuarioService perfil;
    @MockitoBean RecuperacionService recuperacion;
    @MockitoBean VerificacionService verificacion;

    @BeforeEach
    void preparar() {
        Usuario carlos = new Usuario();
        carlos.setId(4L);
        carlos.setNombre("Carlos");
        carlos.setCorreo(CORREO);
        carlos.setRol("COMPRADOR");
        carlos.setPasswordHash(encoder.encode("Clave-de-prueba-1"));
        when(usuarios.findByCorreoIgnoreCase(CORREO)).thenReturn(Optional.of(carlos));
        when(usuarios.findByGoogleUid("uid-carlos")).thenReturn(Optional.of(carlos));
        when(google.verificar("token-de-google"))
                .thenReturn(new GoogleIdentidad("uid-carlos", CORREO, "Carlos", true));
        when(usuarios.puedeEntrar(eq(4L), any())).thenReturn(true);
        when(perfil.obtener(4L)).thenReturn(PerfilResponse.from(carlos));
    }

    private ResultActions entrar(String ruta, String cuerpo) throws Exception {
        return mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    /** El token que devolvió el login sirve para una ruta protegida. */
    private void assertTokenSirve(ResultActions login) throws Exception {
        String cuerpo = login.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(cuerpo).get("token").asText();
        mvc.perform(get("/usuarios/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(CORREO));
    }

    @Test
    void noHayUsuarioEnMemoria() {
        assertEquals(0, contexto.getBeanNamesForType(UserDetailsService.class).length);
    }

    @Test
    void elLoginConContrasenaYSuJwtFuncionan() throws Exception {
        assertTokenSirve(entrar("/auth/login", "{\"correo\":\"" + CORREO + "\",\"password\":\"Clave-de-prueba-1\"}"));
    }

    @Test
    void elLoginConContrasenaIncorrectaDa401() throws Exception {
        entrar("/auth/login", "{\"correo\":\"" + CORREO + "\",\"password\":\"otra\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Correo o contraseña incorrectos"));
    }

    @Test
    void elLoginConGoogleYSuJwtFuncionan() throws Exception {
        assertTokenSirve(entrar("/auth/google", "{\"idToken\":\"token-de-google\"}"));
    }

    @Test
    void sinTokenOConBasicDa401() throws Exception {
        mvc.perform(get("/usuarios/me")).andExpect(status().isUnauthorized());
        // "user:x" en Base64: el usuario por defecto de Spring no abre nada
        mvc.perform(get("/usuarios/me").header("Authorization", "Basic dXNlcjp4")).andExpect(status().isUnauthorized());
        verifyNoInteractions(perfil);
    }
}
