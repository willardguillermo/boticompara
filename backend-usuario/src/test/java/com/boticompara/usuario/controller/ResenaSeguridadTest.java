package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.ResenaDtos;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.security.JwtService;
import com.boticompara.usuario.security.SecurityConfig;
import com.boticompara.usuario.security.SecurityErrorHandler;
import com.boticompara.usuario.service.ResenaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** H85-H87: reglas de acceso reales (SecurityConfig y JWT); el servicio se simula. */
@WebMvcTest(ResenaController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class})
@TestPropertySource(properties = "jwt.secret=secreto-de-prueba-con-mas-de-treinta-y-dos-caracteres")
class ResenaSeguridadTest {

    private static final long COMPRADOR = 4L;
    private static final String CUERPO = "{\"estrellas\": 4, \"comentario\": \"Me atendieron rápido\"}";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    @MockitoBean
    UsuarioRepository usuarios;

    @MockitoBean
    ResenaService resenas;

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

    private static ResenaDtos.Propia propia() {
        return new ResenaDtos.Propia(8L, 2L, (short) 4, "Me atendieron rápido", null, null, false,
                Instant.now(), Instant.now());
    }

    private static ResenaDtos.Listado vacio() {
        return new ResenaDtos.Listado(2L, (BigDecimal) null, 0, 0, 0, List.of(), null);
    }

    @Test
    void elGetEsPublicoYSinTokenNoTrataUsuario() throws Exception {
        when(resenas.listar(2L, 0, null)).thenReturn(vacio());

        mvc.perform(get("/boticas/2/resenas")).andExpect(status().isOk());

        verify(resenas).listar(2L, 0, null);
    }

    @Test
    void elGetConTokenDeCompradorPasaSuId() throws Exception {
        when(resenas.listar(2L, 0, COMPRADOR)).thenReturn(vacio());

        mvc.perform(con(get("/boticas/2/resenas"), COMPRADOR, "COMPRADOR")).andExpect(status().isOk());

        verify(resenas).listar(2L, 0, COMPRADOR);
    }

    @Test
    void elPutSinTokenDa401() throws Exception {
        mvc.perform(put("/boticas/2/resenas/mia").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(resenas);
    }

    @Test
    void elPutDeUnDuenoDa403() throws Exception {
        mvc.perform(con(put("/boticas/2/resenas/mia"), 1L, "DUENO_BOTICA")
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isForbidden());
        verifyNoInteractions(resenas);
    }

    @Test
    void elPutDeUnCompradorCreaConLa201YElCuerpoDeLaResena() throws Exception {
        when(resenas.guardar(COMPRADOR, 2L, 4, "Me atendieron rápido"))
                .thenReturn(new ResenaService.Guardada(true, propia()));

        mvc.perform(con(put("/boticas/2/resenas/mia"), COMPRADOR, "COMPRADOR")
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.estrellas").value(4))
                .andExpect(jsonPath("$.oculta").value(false));
    }

    @Test
    void actualizarResponde200() throws Exception {
        when(resenas.guardar(COMPRADOR, 2L, 4, "Me atendieron rápido"))
                .thenReturn(new ResenaService.Guardada(false, propia()));

        mvc.perform(con(put("/boticas/2/resenas/mia"), COMPRADOR, "COMPRADOR")
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk());
    }

    @Test
    void sinEstrellasDa400() throws Exception {
        mvc.perform(con(put("/boticas/2/resenas/mia"), COMPRADOR, "COMPRADOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comentario\": \"Bien\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(resenas);
    }

    @Test
    void lasDemasRutasDeBoticasSiguenSiendoDelDueno() throws Exception {
        mvc.perform(con(get("/boticas/mia"), COMPRADOR, "COMPRADOR")).andExpect(status().isForbidden());
    }
}