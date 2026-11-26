package com.boticompara.usuario.service;

import com.boticompara.usuario.dto.BoticaRequest;
import com.boticompara.usuario.dto.RegistroRequest;
import com.boticompara.usuario.dto.RucResponse;
import com.boticompara.usuario.entity.Botica;
import com.boticompara.usuario.entity.CodigoCorreo;
import com.boticompara.usuario.entity.CodigoCorreo.Proposito;
import com.boticompara.usuario.entity.Usuario;
import com.boticompara.usuario.error.ApiException;
import com.boticompara.usuario.repository.BoticaRepository;
import com.boticompara.usuario.repository.CodigoCorreoRepository;
import com.boticompara.usuario.repository.UsuarioRepository;
import com.boticompara.usuario.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H1, H20: POST /auth/registro guarda cuenta y botica en UNA transacción. Con las transacciones de Spring de verdad y el
 * BoticaService real (solo se simulan la base, SUNAT y el administrador de transacciones): si la botica falla, la
 * transacción se deshace entera, así que la cuenta no queda creada y el token no se gasta.
 */
@SpringJUnitConfig(RegistroTransaccionTest.Config.class)
@TestPropertySource(properties = "jwt.secret=" + RegistroTransaccionTest.SECRETO)
class RegistroTransaccionTest {

    static final String SECRETO = "secreto-de-prueba-con-mas-de-treinta-y-dos-caracteres";
    private static final String CORREO = "rosa@correo.pe";
    private static final String RUC = "20601234565";

    @Configuration
    @EnableTransactionManagement
    @Import({RegistroService.class, BoticaService.class, CodigoCorreoService.class})
    static class Config {
        @Bean
        Transacciones transacciones() {
            return new Transacciones();
        }

        @Bean
        JwtService jwtService() {
            return new JwtService(SECRETO, 3_600_000L);
        }
    }

    /** Anota cada transacción que se abre, confirma o deshace. */
    static class Transacciones extends AbstractPlatformTransactionManager implements PlatformTransactionManager {
        final List<String> pasos = new ArrayList<>();
        private boolean abierta;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object t) {
            return abierta;
        }

        @Override
        protected void doBegin(Object t, TransactionDefinition definicion) {
            abierta = true;
            pasos.add("abre");
        }

        @Override
        protected void doCommit(DefaultTransactionStatus s) {
            pasos.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus s) {
            pasos.add("rollback");
        }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus s) {
            pasos.add("solo rollback");
        }

        @Override
        protected void doCleanupAfterCompletion(Object t) {
            abierta = false;
        }
    }

    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean BoticaRepository boticas;
    @MockitoBean CodigoCorreoRepository codigos;
    @MockitoBean RucSunatService rucSunat;
    @MockitoBean CorreoService correo;
    @MockitoBean PasswordEncoder encoder;

    @Autowired RegistroService registro;
    @Autowired CodigoCorreoService codigoService;
    @Autowired Transacciones transacciones;

    private CodigoCorreo fila;
    private String token;

    @BeforeEach
    void preparar() {
        transacciones.pasos.clear();
        token = "b".repeat(64);
        fila = new CodigoCorreo();
        fila.setId(5L);
        fila.setCorreo(CORREO);
        fila.setProposito(Proposito.REGISTRO);
        fila.setVerificadoEn(Instant.now());
        fila.setTokenHash(codigoService.hashToken(token));
        fila.setTokenVenceEn(Instant.now().plusSeconds(600));
        when(codigos.bloquearPorTokenHash(fila.getTokenHash())).thenReturn(Optional.of(fila));
        when(encoder.encode(anyString())).thenReturn("HASH");
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> {
            Usuario u = i.getArgument(0);
            u.setId(15L);
            return u;
        });
        when(usuarios.findById(15L)).thenAnswer(i -> Optional.of(new Usuario()));
        when(boticas.save(any(Botica.class))).thenAnswer(i -> {
            Botica b = i.getArgument(0);
            b.setId(3L);
            return b;
        });
    }

    private RegistroRequest dueno() {
        return new RegistroRequest(token, "Rosa Quispe", CORREO, "Boti2026!", null, "DUENO_BOTICA",
                new BoticaRequest("Botica Salud Total", RUC, "Salud Total Farma S.A.C.", "Av. Lima Sur 450",
                        "Lurigancho-Chosica", null, null, null));
    }

    @Test
    void siLaBoticaFallaSeDeshaceTodoYElTokenNoSeGasta() {
        when(rucSunat.exigirValido(RUC, "registro-5")).thenThrow(ApiException.badRequest("El RUC no existe en SUNAT"));

        ApiException e = assertThrows(ApiException.class, () -> registro.registrar(dueno()));

        assertEquals("El RUC no existe en SUNAT", e.getMessage());
        // La cuenta se llegó a guardar dentro de la transacción, pero esta se deshizo entera
        verify(usuarios).save(any(Usuario.class));
        verify(boticas, never()).save(any(Botica.class));
        assertEquals(List.of("abre", "solo rollback", "rollback"), transacciones.pasos);
        assertNull(fila.getUsadoEn());
    }

    @Test
    void siElRucYaEsDeUnaBoticaAprobadaTambienSeDeshaceTodo() {
        when(boticas.existsByRucAndEstado(RUC, "APROBADO")).thenReturn(true);

        assertThrows(ApiException.class, () -> registro.registrar(dueno()));

        assertEquals(List.of("abre", "solo rollback", "rollback"), transacciones.pasos);
        verify(rucSunat, never()).exigirValido(anyString(), anyString());
        assertNull(fila.getUsadoEn());
    }

    @Test
    void cuentaYBoticaSeConfirmanJuntasEnUnaSolaTransaccion() {
        when(rucSunat.exigirValido(RUC, "registro-5")).thenReturn(
                new RucResponse(RUC, true, "RUC válido", "SALUD TOTAL FARMA S.A.C.", "ACTIVO", "HABIDO", "SUNAT",
                        null, null));

        registro.registrar(dueno());

        verify(boticas).save(any(Botica.class));
        assertEquals(List.of("abre", "commit"), transacciones.pasos);
        assertEquals(fila.getUsadoEn() != null, true);
    }

    @Test
    void unCodigoIncorrectoGuardaElIntentoAunqueResponda400() {
        CodigoCorreo pendiente = new CodigoCorreo();
        pendiente.setId(9L);
        pendiente.setCorreo(CORREO);
        pendiente.setProposito(Proposito.REGISTRO);
        pendiente.setCodigoHash(codigoService.hashCodigo(CORREO, Proposito.REGISTRO, "123456"));
        pendiente.setVenceEn(Instant.now().plusSeconds(600));
        when(codigos.findFirstByCorreoAndPropositoOrderByIdDesc(CORREO, Proposito.REGISTRO))
                .thenReturn(Optional.of(pendiente));

        assertThrows(ApiException.class, () -> registro.verificarCodigo(CORREO, "000000"));

        assertEquals(1, pendiente.getIntentos());
        assertEquals(List.of("abre", "commit"), transacciones.pasos);
    }

    @Test
    void siElCorreoDelCodigoNoSaleSeDeshaceElCodigoNuevo() {
        when(correo.enviar(eq(CORREO), anyString(), anyString())).thenReturn(false);

        assertThrows(ApiException.class, () -> registro.enviarCodigo(CORREO));

        verify(codigos).save(any(CodigoCorreo.class));
        assertEquals(List.of("abre", "rollback"), transacciones.pasos);
    }
}
