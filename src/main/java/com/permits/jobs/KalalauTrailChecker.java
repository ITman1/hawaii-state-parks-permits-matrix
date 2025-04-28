package com.permits.jobs;

import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.permits.provider.SlotsProvider.createHttpClient;

@Slf4j
@Component
public class KalalauTrailChecker {
    private static final HttpClient httpClient = createHttpClient();

    @Value("${sender.username:hawaiipermitsmatrix@gmail.com}")
    private String senderUsername;

    @Value("${sender.password}")
    private String senderPassword;

    @Scheduled(fixedDelayString = "${refresh.delay:300000}")
    public void checkKalalau() throws URISyntaxException, IOException, InterruptedException, MessagingException {
        checkKalalau("https://camping.ehawaii.gov/camping/all,sites,0,25,1,1692,,,,20250511,6,,,1,1745854702235.html");
    }

    public void checkOnRequest(String monthDay) throws URISyntaxException, IOException, InterruptedException, MessagingException {
        checkKalalau("https://camping.ehawaii.gov/camping/all,sites,0,25,1,1692,,,,2025" + monthDay + ",6,,,1,1745854702235.html");
    }

    private void checkKalalau(String checkUrl) throws URISyntaxException, IOException, InterruptedException, MessagingException {
        var requestBuilder = HttpRequest.newBuilder()
          .uri(new URI(checkUrl))
          .method("GET", HttpRequest.BodyPublishers.noBody())
          .timeout(Duration.ofSeconds(30));

        var html = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        Document doc = Jsoup.parse(html.body());
        var currentDayElement = new AtomicReference<>(doc.selectXpath("//*[@id=\"sites_table\"]/tbody/tr[1]/td[6]").first());

        var maxAvailbility = IntStream.range(0, 6)
          .boxed()
          .flatMap(i -> {
              var nextDayElement = currentDayElement.get().nextElementSibling();
              if (nextDayElement != null) {
                  currentDayElement.set(nextDayElement);
                  return Stream.of(Integer.parseInt(nextDayElement.text()));
              } else {
                  return Stream.empty();
              }
          })
          .max(Comparator.naturalOrder())
          .orElse(0);

        if (maxAvailbility > 1) {
            sendEmails(checkUrl);
        } else {
            log.info("Nothing new - {}", maxAvailbility);
        }
    }

    private void sendEmails(String checkUrl) {
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
            message.setSubject("Kalalau - ringme");
            message.setText("There are available permits, check: " + checkUrl);

            Transport.send(message);

            log.info("Email sent");
        } catch (MessagingException e) {
            log.error("Failed to send message", e);
        }
    }

}
