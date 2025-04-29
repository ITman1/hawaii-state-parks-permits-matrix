package com.permits.util;

import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Slf4j
@Component
public class EmailSender {
    @Value("${sender.username:hawaiipermitsmatrix@gmail.com}")
    private String senderUsername;

    @Value("${sender.password}")
    private String senderPassword;

    public void sendEmails(String subject, String body) {
        Properties prop = new Properties();
        prop.put("mail.smtp.host", "smtp.gmail.com");
        prop.put("mail.smtp.port", "465");
        prop.put("mail.smtp.auth", "true");
        prop.put("mail.smtp.socketFactory.port", "465");
        prop.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

        Session session = Session.getInstance(prop,
          new jakarta.mail.Authenticator() {
              protected PasswordAuthentication getPasswordAuthentication() {
                  return new PasswordAuthentication(senderUsername, senderPassword);
              }
          });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderUsername));
            message.setRecipients(
              MimeMessage.RecipientType.TO,
              InternetAddress.parse("radim.loskot@gmail.com, avaseko@gmail.com")
            );
            message.setSubject(subject);
            message.setText(body);

            Transport.send(message);

            log.info("Email sent");
        } catch (MessagingException e) {
            log.error("Failed to send message", e);
        }
    }

}
