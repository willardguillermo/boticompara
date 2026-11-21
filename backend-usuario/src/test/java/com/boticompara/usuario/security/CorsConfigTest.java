package com.boticompara.usuario.security;

import com.boticompara.usuario.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "CORS_ORIGINS=https://boticompara.vercel.app,http://localhost:5173")
class CorsConfigTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthService authService;

    @Test
    void unOrigenPermitidoRecibeLaCabeceraDeCors() throws Exception {
        mvc.perform(options("/productos/buscar")
                        .header("Origin", "https://boticompara.vercel.app")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().string("Access-Control-Allow-Origin", "https://boticompara.vercel.app"));
    }

    @Test
    void unOrigenNoPermitidoNoRecibeCabeceraDeCors() throws Exception {
        mvc.perform(options("/productos/buscar")
                        .header("Origin", "https://sitio-ajeno.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}