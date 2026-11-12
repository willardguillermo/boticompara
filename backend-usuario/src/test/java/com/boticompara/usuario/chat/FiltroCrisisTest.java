package com.boticompara.usuario.chat;

import com.boticompara.usuario.chat.FiltroCrisis.Grupo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** H68: el filtro de crisis es una lista corta y literal; cada frase activa su grupo y las comunes no activan nada. */
class FiltroCrisisTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Últimamente pienso en hacerme daño",
            "a veces quiero matarme",
            "he pensado en el suicidio",
            "quiero suicidarme",
            "pienso en quitarme la vida",
            "ya no quiero vivir",
            "solo quiero morir",
            "tengo ganas de cortarme",
    })
    void cadaFraseDeAutolesionActivaSuGrupo(String mensaje) {
        assertEquals(Optional.of(Grupo.AUTOLESION), FiltroCrisis.detectar(mensaje));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ayuda, no puedo respirar",
            "tengo un dolor de pecho muy fuerte",
            "Me duele el pecho",
            "creo que es una intoxicación",
            "posible envenenamiento con lejía",
            "mi hijo tiene una convulsión",
            "sufrió un desmayo",
    })
    void cadaFraseDeEmergenciaActivaSuGrupo(String mensaje) {
        assertEquals(Optional.of(Grupo.EMERGENCIA), FiltroCrisis.detectar(mensaje));
    }

    /** Una prueba por cada frase de las listas, tal como está escrita, para que ninguna quede sin cubrir. */
    @Test
    void todasLasFrasesDeLasListasSeDetectan() {
        assertEquals(7, FiltroCrisis.AUTOLESION.size());
        assertEquals(7, FiltroCrisis.EMERGENCIA.size());
        FiltroCrisis.AUTOLESION.forEach(f ->
                assertEquals(Optional.of(Grupo.AUTOLESION), FiltroCrisis.detectar("hola, " + f + " ahora"), f));
        FiltroCrisis.EMERGENCIA.forEach(f ->
                assertEquals(Optional.of(Grupo.EMERGENCIA), FiltroCrisis.detectar("hola, " + f + " ahora"), f));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "me duele la cabeza",
            "dolor de cabeza",
            "¿Qué tomo para el dolor de espalda?",
            "Paracetamol cerca de mí",
            "¿Hay alternativas más baratas al Panadol?",
            "¿Cuántas pastillas de ibuprofeno tomo para la fiebre?",
            "me duele la garganta y el estómago",
            "jarabe para la tos de pecho",
            "¿Qué productos tengo con stock bajo?",
            "quiero vivir mejor, ¿tienen vitaminas?",
            "",
    })
    void lasFrasesComunesNoActivanElFiltro(String mensaje) {
        assertEquals(Optional.empty(), FiltroCrisis.detectar(mensaje));
    }

    @Test
    void comparaEnMinusculasSinTildesYSinEspaciosDeMas() {
        assertEquals(Optional.of(Grupo.AUTOLESION), FiltroCrisis.detectar("QUIERO   HACERME   DAÑO"));
        assertEquals(Optional.of(Grupo.AUTOLESION), FiltroCrisis.detectar("quiero hacerme dano"));
        assertEquals(Optional.of(Grupo.EMERGENCIA), FiltroCrisis.detectar("INTOXICACION por pastillas"));
        assertEquals(Optional.of(Grupo.EMERGENCIA), FiltroCrisis.detectar("se desmayó hace un rato"));
        assertEquals("hacerme dano", FiltroCrisis.normalizar("  Hacerme\n Daño "));
        assertEquals(Optional.empty(), FiltroCrisis.detectar(null));
    }

    @Test
    void siHayFrasesDeLosDosGrupos_ganaLaAutolesion() {
        assertEquals(Optional.of(Grupo.AUTOLESION),
                FiltroCrisis.detectar("no puedo respirar y quiero quitarme la vida"));
    }

    @Test
    void losTextosFijosTraenLosNumerosYNoDanConsejosDeSalud() {
        assertTrue(Grupo.AUTOLESION.texto().contains("SAMU al 106"));
        assertTrue(Grupo.AUTOLESION.texto().contains("Línea 113, opción 5"));
        assertTrue(Grupo.AUTOLESION.texto().startsWith("Lamento mucho que te sientas así"));
        assertTrue(Grupo.EMERGENCIA.texto().startsWith("Llama al SAMU al 106 de inmediato."));
        for (Grupo g : Grupo.values()) {
            assertTrue(g.texto().length() <= 600);
            assertFalse(g.texto().contains("*") || g.texto().contains("http"));
        }
    }

    @Test
    void losNumerosDeAyudaSeAgreganConUnSoloPunto() {
        assertEquals("Intenta más tarde. Si es una emergencia, llama al SAMU 106. Si piensas en hacerte daño, "
                + "llama a la Línea 113, opción 5.", FiltroCrisis.conNumerosDeAyuda("Intenta más tarde"));
        assertEquals("Intenta mañana. " + FiltroCrisis.NUMEROS_DE_AYUDA, FiltroCrisis.conNumerosDeAyuda("Intenta mañana."));
    }
}
