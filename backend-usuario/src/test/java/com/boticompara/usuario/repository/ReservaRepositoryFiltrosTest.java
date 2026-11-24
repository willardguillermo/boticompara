package com.boticompara.usuario.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S4-01 y S4-03: lo disponible y las reglas de reservas activas no cuentan las que ya pasaron venceEn.
 * Las consultas se validan contra la base al arrancar la aplicación (CorsConfigTest, con .env); aquí se fija el filtro.
 */
class ReservaRepositoryFiltrosTest {

    private static Method metodo(String nombre) {
        return Arrays.stream(ReservaRepository.class.getMethods())
                .filter(m -> m.getName().equals(nombre)).findFirst().orElseThrow();
    }

    private static String jpql(String nombre) {
        return metodo(nombre).getAnnotation(Query.class).value();
    }

    @Test
    void unidadesApartadasNoCuentaLasVencidas() {
        assertTrue(jpql("unidadesApartadas").contains("r.venceEn > :ahora"));
    }

    @Test
    void unidadesApartadasSinEstaNoCuentaLasVencidas() {
        assertTrue(jpql("unidadesApartadasSinEsta").contains("r.venceEn > :ahora"));
    }

    @Test
    void unaReservaActivaPorProductoNoCuentaLasVencidas() {
        String q = jpql("existeActivaDelProducto");
        assertTrue(q.contains("r.usuarioId = :usuarioId") && q.contains("r.productoId = :productoId"));
        assertTrue(q.contains("r.venceEn > :ahora"));
    }

    @Test
    void elMaximoPorCompradorNoCuentaLasVencidas() {
        String q = jpql("contarActivasDe");
        assertTrue(q.contains("r.usuarioId = :usuarioId"));
        assertTrue(q.contains("r.venceEn > :ahora"));
    }

    @Test
    void lasVencidasSeBloqueanSaltandoLasOcupadas() {
        Method m = metodo("bloquearVencidas");
        assertTrue(jpql("bloquearVencidas").contains("r.venceEn <= :ahora"));
        assertEquals(LockModeType.PESSIMISTIC_WRITE, m.getAnnotation(Lock.class).value());
        // -2 = SKIP LOCKED en Hibernate
        var hint = m.getAnnotation(QueryHints.class).value()[0];
        assertEquals("jakarta.persistence.lock.timeout", hint.name());
        assertEquals("-2", hint.value());
    }

    @Test
    void elCompradorSeBloqueaAlReservar() {
        Method m = Arrays.stream(UsuarioRepository.class.getMethods())
                .filter(x -> x.getName().equals("bloquearPorId")).findFirst().orElseThrow();
        assertEquals(LockModeType.PESSIMISTIC_WRITE, m.getAnnotation(Lock.class).value());
    }
}
