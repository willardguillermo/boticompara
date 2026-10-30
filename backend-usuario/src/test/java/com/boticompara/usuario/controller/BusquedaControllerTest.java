package com.boticompara.usuario.controller;

import com.boticompara.usuario.dto.BusquedaResponse;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.security.UsuarioAutenticado;
import com.boticompara.usuario.service.BusquedaService;
import com.boticompara.usuario.service.RegistroBusquedaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** H62: el controlador registra la búsqueda después de buscar(), fuera de su transacción de solo lectura. */
@ExtendWith(MockitoExtension.class)
class BusquedaControllerTest {

    @Mock
    BusquedaService busqueda;

    @Mock
    RegistroBusquedaService registro;

    @InjectMocks
    BusquedaController controller;

    private static final UsuarioAutenticado CARLOS = new UsuarioAutenticado(4L, "COMPRADOR");

    @Test
    void buscar_registraConLaCantidadDeResultadosDevueltos() {
        // El contenido no importa, solo la cantidad (ya con el filtro de radio aplicado por el servicio)
        List<BusquedaResponse> dos = List.of(mock(BusquedaResponse.class), mock(BusquedaResponse.class));
        when(busqueda.buscar("para", "principio", null, -11.94, -76.69, "2")).thenReturn(dos);

        List<BusquedaResponse> r = controller.buscar("para", "principio", null, -11.94, -76.69, "2", CARLOS);

        assertSame(dos, r);
        verify(registro).registrar("para", "principio", 2, 4L);
    }

    @Test
    void buscar_sinResultados_registraCero() {
        when(busqueda.buscar("zzz", "nombre", null, null, null, null)).thenReturn(List.of());

        controller.buscar("zzz", "nombre", null, null, null, null, CARLOS);

        verify(registro).registrar("zzz", "nombre", 0, 4L);
    }

    @Test
    void buscar_sinSesion_registraSinUsuario() {
        when(busqueda.buscar("para", "nombre", null, null, null, null)).thenReturn(List.of());

        controller.buscar("para", "nombre", null, null, null, null, null);

        verify(registro).registrar("para", "nombre", 0, null);
    }

    @Test
    void buscar_conError400_noRegistra() {
        when(busqueda.buscar("p", "nombre", null, null, null, null))
                .thenThrow(ApiException.badRequest("La búsqueda requiere mínimo 2 caracteres"));

        assertThrows(ApiException.class, () -> controller.buscar("p", "nombre", null, null, null, null, CARLOS));

        verify(registro, never()).registrar(anyString(), anyString(), anyInt(), any());
    }
}