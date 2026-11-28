package com.boticompara.usuario.security;

import com.boticompara.usuario.BaseDePrueba;
import com.boticompara.usuario.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Levanta la aplicación completa, que necesita la base. Sin base configurada, la clase se omite. */
@SpringBootTest
@AutoConfigureMockMvc
// H84: sin la tarea de vencer reservas, que al arrancar escribiría en la base compartida
// H20: con el límite de registros por IP en 0, POST /auth/registro/codigo da 429 sin tocar la base ni enviar correos
@TestPropertySource(properties = {"CORS_ORIGINS=https://boticompara.vercel.app,http://localhost:5173",
        "app.reservas.vencer.habilitada=false", "auth.limite.registro-por-ip-hora=0"})
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

    @Test
    void unaRespuesta429DejaLeerRetryAfterDesdeUnOrigenPermitido() throws Exception {
        mvc.perform(post("/auth/registro/codigo")
                        .header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\": \"cors@boticompara.invalid\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Expose-Headers", "Retry-After"));
    }
}
