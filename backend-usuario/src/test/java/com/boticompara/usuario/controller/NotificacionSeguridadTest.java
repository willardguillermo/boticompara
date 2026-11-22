package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.NotificacionResponse;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.security.JwtService;
import com.boticompara.usuario.security.SecurityConfig;
import com.boticompara.usuario.security.SecurityErrorHandler;
import com.boticompara.usuario.service.NotificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** H103: reglas de acceso reales (SecurityConfig y JWT) con el servicio real; solo se simula la base. */
@WebMvcTest(NotificacionController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class, NotificacionService.class})
@TestPropertySource(properties = "jwt.secret=secreto-de-prueba-con-mas-de-treinta-y-dos-caracteres")
class NotificacionSeguridadTest {

    private static final long ROSA = 1L;
    private static final long JORGE = 2L;
    /** La única notificación de la base simulada, de Rosa. */
    private static final long DE_ROSA = 31L;

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    @MockitoBean
    UsuarioRepository usuarios;

    @MockitoBean
    NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        when(usuarios.puedeEntrar(anyLong(), any())).thenReturn(true);
        // Como la base: el update solo toca la fila si el id y el dueño coinciden
        lenient().when(jdbc.update(contains("where id = :id and usuario_id = :usuario"), any(SqlParameterSource.class)))
                .thenAnswer(inv -> {
                    SqlParameterSource p = inv.getArgument(1);
                    boolean esSuya = p.getValue("id").equals(DE_ROSA) && p.getValue("usuario").equals(ROSA);
                    return esSuya ? 1 : 0;
                });
    }

    private MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder peticion, long id, String rol) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setRol(rol);
        u.setCorreo("prueba@boticompara.test");
        return peticion.header("Authorization", "Bearer " + jwt.generar(u));
    }

    private MockHttpServletRequestBuilder dueno(MockHttpServletRequestBuilder peticion, long id) {
        return con(peticion, id, "DUENO_BOTICA");
    }

    @ParameterizedTest
    @CsvSource({"GET, /notificaciones", "GET, /notificaciones/no-leidas/total",
            "PATCH, /notificaciones/31/leida", "PATCH, /notificaciones/leidas"})
    void sinTokenDa401(String metodo, String ruta) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(metodo), ruta)).andExpect(status().isUnauthorized());
        verifyNoInteractions(jdbc);
    }

    @ParameterizedTest
    @CsvSource({"GET, /notificaciones", "GET, /notificaciones/no-leidas/total",
            "PATCH, /notificaciones/31/leida", "PATCH, /notificaciones/leidas"})
    void compradorDa403(String metodo, String ruta) throws Exception {
        mvc.perform(con(request(HttpMethod.valueOf(metodo), ruta), 4L, "COMPRADOR")).andExpect(status().isForbidden());
        verifyNoInteractions(jdbc);
    }

    @Test
    void marcarLaPropiaDa204SinCuerpo() throws Exception {
        mvc.perform(dueno(patch("/notificaciones/31/leida"), ROSA))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void marcarLaDeOtroDuenoYUnaInexistenteDanElMismo404() throws Exception {
        for (String ruta : List.of("/notificaciones/31/leida", "/notificaciones/999/leida")) {
            mvc.perform(dueno(patch(ruta), JORGE))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensaje").value("La notificación no existe"));
        }
    }

    @Test
    void cadaDuenoSoloListaCuentaYMarcaLasSuyas() throws Exception {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(0L);

        mvc.perform(dueno(get("/notificaciones"), JORGE)).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(dueno(get("/notificaciones/no-leidas/total"), JORGE)).andExpect(status().isOk())
                .andExpect(content().json("{\"total\": 0}"));
        mvc.perform(dueno(patch("/notificaciones/leidas"), JORGE)).andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(jdbc).query(anyString(), deJorge(), ArgumentMatchers.<RowMapper<NotificacionResponse>>any());
        verify(jdbc).queryForObject(anyString(), deJorge(), eq(Long.class));
        verify(jdbc).update(contains("and not leida"), deJorge());
    }

    private static SqlParameterSource deJorge() {
        return argThat(p -> p.getValue("usuario").equals(JORGE));
    }

    @Test
    void parametrosInvalidosDan400() throws Exception {
        mvc.perform(dueno(get("/notificaciones").param("soloNoLeidas", "quizas"), ROSA))
                .andExpect(status().isBadRequest());
        mvc.perform(dueno(patch("/notificaciones/abc/leida"), ROSA)).andExpect(status().isBadRequest());
    }
}
