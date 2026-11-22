package com.boticompara.usuario.controller;

import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.security.JwtService;
import com.boticompara.usuario.security.SecurityConfig;
import com.boticompara.usuario.security.SecurityErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /salud con las reglas de acceso reales (SecurityConfig). */
@WebMvcTest(SaludController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class})
@TestPropertySource(properties = "jwt.secret=secreto-de-prueba-con-mas-de-treinta-y-dos-caracteres")
class SaludControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean UsuarioRepository usuarios;

    @Test
    void esPublicoNoTocaLaBaseYSoloDiceOk() throws Exception {
        mvc.perform(get("/salud")).andExpect(status().isOk()).andExpect(content().string("{\"estado\":\"OK\"}"));
        verifyNoInteractions(usuarios);
    }

    @Test
    void soloElGetEsPublico() throws Exception {
        mvc.perform(post("/salud")).andExpect(status().isUnauthorized());
        mvc.perform(get("/salud/otra")).andExpect(status().isUnauthorized());
    }
}
