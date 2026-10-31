package com.boticompara.usuario.service;

import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.Producto;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.ProductoRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertaStockServiceTest {

    @Mock
    BoticaRepository boticas;

    @Mock
    UsuarioRepository usuarios;

    @Mock
    ProductoRepository productos;

    @Mock
    CorreoService correo;

    @InjectMocks
    AlertaStockService service;

    private Botica botica(Long id, Long usuarioId, String nombre) {
        Botica b = new Botica();
        b.setId(id);
        b.setUsuarioId(usuarioId);
        b.setNombreComercial(nombre);
        b.setEstado("APROBADO");
        return b;
    }

    private Usuario usuario(String correo, boolean activo) {
        Usuario u = new Usuario();
        u.setNombre("Jorge Mendoza");
        u.setCorreo(correo);
        u.setActivo(activo);
        return u;
    }

    private Producto producto(String nombre, int stock, int stockMinimo) {
        Producto p = new Producto();
        p.setNombreComercial(nombre);
        p.setPresentacion("Tableta 500 mg x 10");
        p.setStock(stock);
        p.setStockMinimo(stockMinimo);
        return p;
    }

    @Test
    void enviaUnCorreoConSoloLosProductosEnOBajoElMinimo() {
        when(boticas.findByEstado("APROBADO")).thenReturn(List.of(botica(1L, 10L, "FarmaVida")));
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario("jorge@boticompara.nissidev.dev", true)));
        when(productos.findByBoticaIdAndActivoTrueOrderByNombreComercialAsc(1L)).thenReturn(List.of(
                producto("Ibuprofeno Generico", 0, 5),
                producto("Nexium", 5, 5),
                producto("Panadol", 35, 5)));
        when(correo.enviar(eq("jorge@boticompara.nissidev.dev"), anyString(), anyString())).thenReturn(true);

        int enviados = service.enviarAlertas();

        assertEquals(1, enviados);
        ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(correo).enviar(eq("jorge@boticompara.nissidev.dev"), asunto.capture(), texto.capture());
        assertTrue(asunto.getValue().contains("2 productos con stock bajo"));
        assertTrue(asunto.getValue().contains("FarmaVida"));
        assertTrue(texto.getValue().contains("Hola Jorge Mendoza"));
        assertTrue(texto.getValue().contains("Ibuprofeno Generico"));
        assertTrue(texto.getValue().contains("Nexium"));
        assertFalse(texto.getValue().contains("Panadol"));
    }

    @Test
    void usaElSingularConUnSoloProducto() {
        when(boticas.findByEstado("APROBADO")).thenReturn(List.of(botica(1L, 10L, "FarmaVida")));
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario("jorge@boticompara.nissidev.dev", true)));
        when(productos.findByBoticaIdAndActivoTrueOrderByNombreComercialAsc(1L))
                .thenReturn(List.of(producto("Nexium", 1, 5)));
        when(correo.enviar(anyString(), anyString(), anyString())).thenReturn(true);

        service.enviarAlertas();

        ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
        verify(correo).enviar(eq("jorge@boticompara.nissidev.dev"), asunto.capture(), anyString());
        assertTrue(asunto.getValue().contains("1 producto con stock bajo"));
        assertFalse(asunto.getValue().contains("1 productos"));
    }

    @Test
    void noEnviaNadaSiNingunProductoEstaBajo() {
        when(boticas.findByEstado("APROBADO")).thenReturn(List.of(botica(1L, 10L, "Salud Total")));
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario("rosa@boticompara.nissidev.dev", true)));
        when(productos.findByBoticaIdAndActivoTrueOrderByNombreComercialAsc(1L))
                .thenReturn(List.of(producto("Panadol", 40, 5), producto("Advil", 6, 5)));

        int enviados = service.enviarAlertas();

        assertEquals(0, enviados);
        verify(correo, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void omiteALosDuenosInactivos() {
        when(boticas.findByEstado("APROBADO")).thenReturn(List.of(botica(1L, 10L, "FarmaVida")));
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario("eliminado-10@boticompara.local", false)));

        int enviados = service.enviarAlertas();

        assertEquals(0, enviados);
        verifyNoInteractions(productos);
        verifyNoInteractions(correo);
    }

    @Test
    void noCuentaLosCorreosQueFallan() {
        when(boticas.findByEstado("APROBADO")).thenReturn(List.of(botica(1L, 10L, "FarmaVida")));
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario("jorge@boticompara.nissidev.dev", true)));
        when(productos.findByBoticaIdAndActivoTrueOrderByNombreComercialAsc(1L))
                .thenReturn(List.of(producto("Nexium", 0, 5)));
        when(correo.enviar(anyString(), anyString(), anyString())).thenReturn(false);

        assertEquals(0, service.enviarAlertas());
    }

    @Test
    void siUnCorreoFallaSigueConLasDemasBoticas() {
        when(boticas.findByEstado("APROBADO")).thenReturn(
                List.of(botica(1L, 10L, "Botica A"), botica(2L, 20L, "Botica B")));
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario("a@x.pe", true)));
        when(usuarios.findById(20L)).thenReturn(Optional.of(usuario("b@x.pe", true)));
        when(productos.findByBoticaIdAndActivoTrueOrderByNombreComercialAsc(1L))
                .thenReturn(List.of(producto("A1", 0, 5)));
        when(productos.findByBoticaIdAndActivoTrueOrderByNombreComercialAsc(2L))
                .thenReturn(List.of(producto("B1", 1, 5)));
        when(correo.enviar(eq("a@x.pe"), anyString(), anyString())).thenReturn(false);
        when(correo.enviar(eq("b@x.pe"), anyString(), anyString())).thenReturn(true);

        assertEquals(1, service.enviarAlertas());
        verify(correo, times(2)).enviar(anyString(), anyString(), anyString());
    }
}