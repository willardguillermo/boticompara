package com.boticompara.usuario.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorreoServiceTest {

    @Mock
    ObjectProvider<JavaMailSender> proveedor;

    @Mock
    JavaMailSender sender;

    @Test
    void noEnviaNadaSiNoHayServidorConfigurado() {
        CorreoService service = new CorreoService(proveedor, "", "no-reply@boticompara.local");

        assertFalse(service.enviar("a@x.pe", "Asunto", "Texto"));

        verifyNoInteractions(sender);
    }

    @Test
    void enviaElMensajeConRemitenteDestinoAsuntoYTexto() {
        when(proveedor.getIfAvailable()).thenReturn(sender);
        CorreoService service = new CorreoService(proveedor, "sandbox.smtp.mailtrap.io", "no-reply@boticompara.local");

        assertTrue(service.enviar("jorge@farmavida.pe", "Stock bajo", "Hola"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage m = captor.getValue();
        assertEquals("no-reply@boticompara.local", m.getFrom());
        assertArrayEquals(new String[]{"jorge@farmavida.pe"}, m.getTo());
        assertEquals("Stock bajo", m.getSubject());
        assertEquals("Hola", m.getText());
    }

    @Test
    void devuelveFalseSiElServidorFalla() {
        when(proveedor.getIfAvailable()).thenReturn(sender);
        doThrow(new MailSendException("fallo")).when(sender).send(any(SimpleMailMessage.class));
        CorreoService service = new CorreoService(proveedor, "sandbox.smtp.mailtrap.io", "no-reply@boticompara.local");

        assertFalse(service.enviar("a@x.pe", "Asunto", "Texto"));
    }
}