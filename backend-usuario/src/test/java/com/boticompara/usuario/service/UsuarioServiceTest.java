package com.boticompara.usuario.service;

import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.ProductoRepository;
import com.boticompara.usuario.repository.TokenUsuarioRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    UsuarioRepository usuarios;

    @Mock
    BoticaRepository boticas;

    @Mock
    ProductoRepository productos;

    @Mock
    TokenUsuarioRepository tokens;

    @Mock
    PasswordEncoder encoder;

    @InjectMocks
    UsuarioService service;

    private Usuario usuario(boolean activo) {
        Usuario u = new Usuario();
        u.setId(10L);
        u.setNombre("Ana Torres");
        u.setCorreo("ana@correo.com");
        u.setTelefono("999888777");
        u.setDireccion("Av. Lima 123");
        u.setPasswordHash("HASH_VIEJO");
        u.setRol("DUENO_BOTICA");
        u.setActivo(activo);
        return u;
    }

    private Botica botica(Long id) {
        Botica b = new Botica();
        b.setId(id);
        b.setUsuarioId(10L);
        return b;
    }

    @Test
    void eliminarAnonimizaLosDatosPersonalesYDesactivaLaCuenta() {
        Usuario u = usuario(true);
        when(usuarios.findById(10L)).thenReturn(Optional.of(u));
        when(encoder.encode(anyString())).thenReturn("HASH_INUTIL");

        service.eliminar(10L);

        assertEquals("Usuario eliminado", u.getNombre());
        assertEquals("eliminado-10@boticompara.local", u.getCorreo());
        assertNull(u.getTelefono());
        assertNull(u.getDireccion());
        assertFalse(u.isActivo());
        assertEquals("HASH_INUTIL", u.getPasswordHash());
        assertEquals("DUENO_BOTICA", u.getRol());
        verify(usuarios).save(u);
    }

    @Test
    void laContrasenaNuevaEsAleatoriaYNuncaLaAnterior() {
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario(true)));
        when(encoder.encode(anyString())).thenReturn("HASH_INUTIL");

        service.eliminar(10L);

        ArgumentCaptor<String> clave = ArgumentCaptor.forClass(String.class);
        verify(encoder).encode(clave.capture());
        assertTrue(clave.getValue().matches("[0-9a-f]{64}"));
        assertNotEquals("HASH_VIEJO", clave.getValue());
    }

    @Test
    void eliminarDaDeBajaLosProductosDeSuBotica() {
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario(true)));
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.of(botica(5L)));

        service.eliminar(10L);

        verify(productos).desactivarPorBotica(5L);
    }

    @Test
    void sinBoticaNoTocaProductos() {
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario(true)));
        when(boticas.findByUsuarioId(10L)).thenReturn(Optional.empty());

        service.eliminar(10L);

        verifyNoInteractions(productos);
    }

    @Test
    void eliminarInvalidaLosCodigosYTokensPendientes() {
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario(true)));

        service.eliminar(10L);

        verify(tokens).invalidarPendientes(10L);
    }

    @Test
    void obtenerDevuelveSiElCorreoEstaVerificado() {
        Usuario u = usuario(true);
        u.setCorreoVerificado(true);
        when(usuarios.findById(10L)).thenReturn(Optional.of(u));

        assertTrue(service.obtener(10L).correoVerificado());
    }

    @Test
    void obtenerConCorreoSinVerificarDevuelveFalse() {
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario(true)));

        assertFalse(service.obtener(10L).correoVerificado());
    }

    @Test
    void unUsuarioInexistenteDa401SinTocarNada() {
        when(usuarios.findById(10L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.eliminar(10L));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(usuarios, never()).save(any(Usuario.class));
        verifyNoInteractions(boticas, productos, tokens, encoder);
    }

    @Test
    void unaCuentaYaEliminadaDa401SinTocarNada() {
        Usuario u = usuario(false);
        when(usuarios.findById(10L)).thenReturn(Optional.of(u));

        ApiException ex = assertThrows(ApiException.class, () -> service.eliminar(10L));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("Ana Torres", u.getNombre());
        verify(usuarios, never()).save(any(Usuario.class));
        verifyNoInteractions(boticas, productos, tokens, encoder);
    }
}