package com.permits.provider;

import com.permits.DayPermits;
import com.permits.PermitDate;
import com.permits.Slot;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Order(2)
@Component
public class KonaSubmarineProvider extends SlotsProvider {

    @Override
    public CompletableFuture<List<Slot>> getSlots(String recaptchaToken, List<PermitDate> dates) {
        List<Future<List<DayPermits>>> permits = new ArrayList<>();
        for (PermitDate date : dates) {
            permits.add(getDayPermits(date, 46));
        }

        return CompletableFuture.completedFuture(permits.stream()
          .map(future -> {
              try {
                  return future.get();
              } catch (Exception e) {
                  throw new RuntimeException(e);
              }
          })
          .flatMap(List::stream)
          .collect(Collectors.groupingBy(DayPermits::slotId))
          .entrySet()
          .stream()
          .map(entry -> {
              var slotId = entry.getKey();
              var dayPermits = entry.getValue();
              return new Slot("Submarine", slotId, dayPermits);
          })
          .toList()
          .stream()
          .map(slot -> {
              List<DayPermits> dayPermits = dates
                .stream()
                .map(date -> slot.dayPermits()
                  .stream()
                  .filter(dayPermit -> dayPermit.date().equals(date))
                  .findFirst()
                  .orElse(new DayPermits(date, 0, generateHeatMapColor(0, 0, 46), slot.slotName())))
                .toList();

              return new Slot(slot.parkName(), slot.slotName(), dayPermits);
          })
          .sorted(Comparator.comparing(Slot::slotName))
          .toList());
    }


    public Future<List<DayPermits>> getDayPermits(PermitDate date, int maxValue) {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return executor.submit(() -> {
            try {
                var urlString = "https://book.atlantisadventures.com/calendar_day.php?com=154563&date=" + "2025-" + date.month() + "-" + date.day() +
                  "&type=calendar&js_timestamp=" + (System.currentTimeMillis() / 1000L) + "&js_timezone=Europe%2FPrague&date_format=M%20d%20Y&time_format=-3&src=%2Fpage_details.php";
                var requestBuilder = HttpRequest.newBuilder()
                  .uri(new URI(urlString))
                  .method("GET", HttpRequest.BodyPublishers.noBody())
                  .timeout(Duration.ofSeconds(30));

                var html = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
                Document doc = Jsoup.parse(html.body());
                var result = new ArrayList<DayPermits>();

                List.of("a", "b", "c", "d", "e", "f").forEach(slotLetter -> {
                    Optional.ofNullable(doc.select("#checkout_1_" + slotLetter + " > span.rezgo-memo.rezgo-time > strong").first())
                      .flatMap(element -> Optional.ofNullable(element.nextSibling()))
                      .map(node -> node.toString().trim())
                      .map(time -> {
                          int permits = Optional.ofNullable(doc.select("#checkout_1_" + slotLetter + " > span.rezgo-memo.rezgo-availability > strong").first())
                            .flatMap(element -> Optional.ofNullable(element.nextSibling()))
                            .map(node -> node.toString().trim())
                            .filter(node -> !node.isEmpty())
                            .map(Integer::parseInt)
                            .orElse(0);

                          return new DayPermits(date, permits, generateHeatMapColor(permits, 0, maxValue), time);
                      })
                      .ifPresent(result::add);
                });

                return result;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
