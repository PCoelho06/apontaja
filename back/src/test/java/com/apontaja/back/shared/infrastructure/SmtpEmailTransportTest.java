package com.apontaja.back.shared.infrastructure;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SmtpEmailTransportTest {

    @Mock
    private JavaMailSender mailSender;

    private MimeMessage prepared(String to, String subject, String body) throws Exception {
        new SmtpEmailTransport(mailSender, "no-reply@apontaja.test", "Apontaja").send(to, subject, body);
        ArgumentCaptor<MimeMessagePreparator> captor = ArgumentCaptor.forClass(MimeMessagePreparator.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        captor.getValue().prepare(message);
        return message;
    }

    @Test
    void compose_un_message_texte_utf8_avec_expediteur_et_destinataire() throws Exception {
        MimeMessage message = prepared("alice@example.com", "Marcação confirmada - Salão", "Olá, Alice,\nAté já.");

        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("no-reply@apontaja.test");
        assertThat(from.getPersonal()).isEqualTo("Apontaja");
        assertThat(((InternetAddress) message.getRecipients(Message.RecipientType.TO)[0]).getAddress())
                .isEqualTo("alice@example.com");
        assertThat(message.getSubject()).isEqualTo("Marcação confirmada - Salão");
        assertThat(message.getContent()).isEqualTo("Olá, Alice,\nAté já.");
        assertThat(message.getContentType()).startsWith("text/plain");
    }

    @Test
    void neutralise_les_retours_a_la_ligne_du_sujet() throws Exception {
        MimeMessage message = prepared("alice@example.com", "Sujet\r\nBcc: pirate@example.com", "Corps");

        assertThat(message.getSubject()).doesNotContain("\n").doesNotContain("\r");
        assertThat(message.getHeader("Bcc")).isNull();
    }
}
