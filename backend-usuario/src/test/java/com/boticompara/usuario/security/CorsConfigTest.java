package com.boticompara.usuario.security;

import com.boticompara.usuario.BaseDePrueba;
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

/** Levanta la aplicación completa, que necesita la base. Sin base configurada, la clase se omite. */
@SpringBootTest
@AutoConfigureMockMvc
// H84: sin la tarea de vencer reservas, que al arrancar escribiría en la base compartida
@TestPropertySource(properties = {"CORS_ORIGINS=https://boticompara.vercel.app,http://localhost:5173",
        "app.reservas.vencer.habilitada=false"})
class CorsConfigTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthService authService;

    CorsConfigTest() {
        BaseDePrueba.exigir();
    }

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