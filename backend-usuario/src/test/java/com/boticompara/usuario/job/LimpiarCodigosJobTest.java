package com.boticompara.usuario.job;

import com.boticompara.usuario.service.CodigoCorreoService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** H20: la limpieza horaria de codigo_correo nunca tumba al planificador. */
class LimpiarCodigosJobTest {

    @Test
    void llamaALaLimpieza() {
        CodigoCorreoService codigos = mock(CodigoCorreoService.class);
        when(codigos.limpiar()).thenReturn(3);

        new LimpiarCodigosJob(codigos).ejecutar();

        verify(codigos).limpiar();
    }

    @Test
    void unErrorDeLaBaseNoSeEscapa() {
        CodigoCorreoService codigos = mock(CodigoCorreoService.class);
        when(codigos.limpiar()).thenThrow(new IllegalStateException("base caída"));

        assertDoesNotThrow(() -> new LimpiarCodigosJob(codigos).ejecutar());
    }
}
