package com.boticompara.usuario.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Envío de correos de texto plano. Si no hay servidor configurado (MAIL_HOST vacío) no envía y lo avisa en el log. */
@Slf4j
@Service
public class CorreoService {

    private final ObjectProvider<JavaMailSender> sender;
    private final String host;
    private final String remitente;

    public CorreoService(ObjectProvider<JavaMailSender> sender,
                         @Value("${spring.mail.host:}") String host,
                         @Value("${app.mail.from:no-reply@boticompara.local}") String remitente) {
        this.sender = sender;
        this.host = host;
        this.remitente = remitente;
    }

    /** @return true si el servidor SMTP aceptó el mensaje; false si no está configurado o falló. */
    public boolean enviar(String para, String asunto, String texto) {
        JavaMailSender s = sender.getIfAvailable();
        if (host.isBlank() || s == null) {
            log.warn("Correo no configurado (MAIL_HOST vacio): no se envio \"{}\"", asunto);
            return false;
        }
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(remitente);
            mensaje.setTo(para);
            mensaje.setSubject(asunto);
            mensaje.setText(texto);
            s.send(mensaje);
            return true;
        } catch (MailException e) {
            log.error("No se pudo enviar el correo \"{}\" a {}: {}", asunto, para, e.getMessage());
            return false;
        }
    }
}