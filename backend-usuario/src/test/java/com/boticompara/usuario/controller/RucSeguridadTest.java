package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.RucResponse;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.limite.LimiteExcedidoException;
import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.security.JwtService;
import com.boticompara.usuario.security.SecurityConfig;
import com.boticompara.usuario.security.SecurityErrorHandler;
import com.boticompara.usuario.service.RucSunatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** H2: GET /boticas/validar-ruc exige sesión de dueño y responde 429 con Retry-After al pasar el límite. */
@WebMvcTest(RucController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class})
@TestPropertySource(properties = "jwt.secret=secreto-de-prueba-con-mas-de-treinta-y-dos-caracteres")
class RucSeguridadTest {

    private static final String RUC = "20601234565";
    private static final long DUENO = 1L;

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;

    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean RucSunatService rucSunat;

    @BeforeEach
    void preparar() {
        when(usuarios.puedeEntrar(anyLong(), any())).thenReturn(true);
    }

    private MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder peticion, long id, String rol) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setRol(rol);
        u.setCorreo("prueba@boticompara.test");
        return peticion.header("Authorization", "Bearer " + jwt.generar(u));
    }

    @Test
    void sinToken_401YNoConsultaNada() throws Exception {
        mvc.perform(get("/boticas/validar-ruc/" + RUC)).andExpect(status().isUnauthorized());

        verifyNoInteractions(rucSunat);
    }

    @Test
    void conTokenDeComprador_403YNoConsultaNada() throws Exception {
        mvc.perform(con(get("/boticas/validar-ruc/" + RUC), 4L, "COMPRADOR")).andExpect(status().isForbidden());

        verifyNoInteractions(rucSunat);
    }

    @Test
    void conTokenDeDueno_consultaConSuId() throws Exception {
        when(rucSunat.consultarParaValidar(RUC, DUENO)).thenReturn(new RucResponse(RUC, true, "RUC válido",
                "SALUD TOTAL FARMA S.A.C.", "ACTIVO", "HABIDO", "SUNAT", null, null));

        mvc.perform(con(get("/boticas/validar-ruc/" + RUC), DUENO, "DUENO_BOTICA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuente").value("SUNAT"))
                .andExpect(jsonPath("$.razonSocial").value("SALUD TOTAL FARMA S.A.C."));
    }

    @Test
    void alPasarElLimite_429ConRetryAfterYElFormatoDeError() throws Exception {
        when(rucSunat.consultarParaValidar(RUC, DUENO)).thenThrow(new LimiteExcedidoException(
                "Consultaste demasiados RUC. Espera un momento y vuelve a intentar", 1200));

        mvc.perform(con(get("/boticas/validar-ruc/" + RUC), DUENO, "DUENO_BOTICA"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1200"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.mensaje").value("Consultaste demasiados RUC. Espera un momento y vuelve a intentar"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
