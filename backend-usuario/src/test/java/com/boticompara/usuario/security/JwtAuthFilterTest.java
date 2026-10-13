package com.boticompara.usuario.security;

import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    private static final String SECRETO = "secreto-de-prueba-con-mas-de-treinta-y-dos-caracteres";

    @Mock
    UsuarioRepository usuarios;

    JwtService jwt;
    JwtAuthFilter filtro;

    @BeforeEach
    void preparar() {
        SecurityContextHolder.clearContext();
        jwt = new JwtService(SECRETO, 3_600_000L);
        filtro = new JwtAuthFilter(jwt, usuarios);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private String tokenDe(long id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setRol("COMPRADOR");
        u.setCorreo("ana@correo.com");
        return jwt.generar(u);
    }

    /** Cambia un carácter dentro de la firma: así el token deja de ser válido de verdad. */
    private String alterar(String token) {
        int i = token.length() - 10;
        char original = token.charAt(i);
        char otro = original == 'A' ? 'B' : 'A';
        return token.substring(0, i) + otro + token.substring(i + 1);
    }

    private void ejecutar(String cabecera) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (cabecera != null) {
            request.addHeader("Authorization", cabecera);
        }
        filtro.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void unTokenValidoDeUnaCuentaActivaAutentica() throws Exception {
        when(usuarios.existsByIdAndActivoTrue(10L)).thenReturn(true);

        ejecutar("Bearer " + tokenDe(10L));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(10L, ((UsuarioAutenticado) auth.getPrincipal()).id());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_COMPRADOR")));
    }

    @Test
    void unTokenValidoDeUnaCuentaEliminadaNoAutentica() throws Exception {
        when(usuarios.existsByIdAndActivoTrue(10L)).thenReturn(false);

        ejecutar("Bearer " + tokenDe(10L));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void sinCabeceraNoAutenticaNiConsultaLaBase() throws Exception {
        ejecutar(null);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(usuarios);
    }

    @Test
    void unTokenAlteradoNoAutenticaNiConsultaLaBase() throws Exception {
        ejecutar("Bearer " + alterar(tokenDe(10L)));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(usuarios);
    }
}