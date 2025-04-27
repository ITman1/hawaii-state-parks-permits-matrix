package com.permits.provider;

import com.permits.DayPermits;
import com.permits.PermitDate;
import com.permits.Slot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Order(2)
@Component
public class NaPaliCatamaran extends SlotsProvider {

    @Override
    public CompletableFuture<List<Slot>> getSlots(String recaptchaToken, List<PermitDate> dates) {
        var result = getJson(dates)
          .collect(Collectors.groupingBy(DayPermits::slotId))
          .entrySet()
          .stream()
          .map(entry -> {
              var slotId = entry.getKey();
              var dayPermits = entry.getValue();
              return new Slot("Na Pali Catamaran", slotId, dayPermits);
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
                  .orElse(new DayPermits(date, 0, generateHeatMapColor(0, 0, 14), slot.slotName(), date.month() == 5 && date.day() > 10 && date.day() < 17)))
                .toList();

              return new Slot(slot.parkName(), slot.slotName(), dayPermits);
          })
          .sorted(Comparator.comparing(Slot::slotName))
          .toList();

        return CompletableFuture.completedFuture(result);
    }

    private Stream<DayPermits> getJson(List<PermitDate> dates) {
        var firstMonth = LocalDate.of(LocalDate.now().getYear(), dates.getFirst().month(), dates.getFirst().day());
        var secondMonth = firstMonth.plusMonths(1);
        HttpRequest.Builder requestBuilder = null;
        try {
            requestBuilder = HttpRequest.newBuilder()
              .uri(new URI("https://fareharbor.com/api/v1/companies/napalicatamaran/items/346489,576501/calendar/" + firstMonth.getYear() + "/" + String.format("%02d", firstMonth.getMonthValue()) + "/?allow_grouped=yes&bookable_only=no&language=en-us&asn="))
              .GET().timeout(Duration.ofSeconds(30));
            var firstMonthResponseString = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            requestBuilder = HttpRequest.newBuilder()
              .uri(new URI("https://fareharbor.com/api/v1/companies/napalicatamaran/items/346489,576501/calendar/" + secondMonth.getYear() + "/" + String.format("%02d", secondMonth.getMonthValue()) + "/?allow_grouped=yes&bookable_only=no&language=en-us&asn="))
              .GET().timeout(Duration.ofSeconds(30));
            var secondMonthResponseString = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            var firstMonthJson = mapper.readValue(firstMonthResponseString.body(), Map.class);
            var secondMonthJson = mapper.readValue(secondMonthResponseString.body(), Map.class);

            return Stream.concat(
              getDayPermits(firstMonth, firstMonthJson),
              getDayPermits(secondMonth, secondMonthJson)
            );
        } catch (Exception e) {
            log.error("Error while getting JSON from Haena Park", e);
            return Stream.of();
        }
    }

    private Stream<DayPermits> getDayPermits(LocalDate firstMonth, Map json) {
        return ((List) ((Map) json.get("calendar")).get("weeks"))
          .stream()
          .flatMap(a -> ((List) ((Map) a).get("days")).stream())
          .flatMap(a -> {
              var month = firstMonth.getMonthValue();
              var day = (int) ((Map) a).get("number");
              var permitDay = new PermitDate(day, month);

              return ((List) ((Map) a).get("availabilities"))
                .stream()
                .map(availability -> {
                    var current = (int) ((Map) availability).get("blocks_included_bookable_capacity");
                    var slotId = LocalDateTime.parse((String) ((Map) availability).get("start_at")).format(DateTimeFormatter.ofPattern("HH:mm"));
                    var presence = permitDay.month() == 5 && permitDay.day() > 10 && permitDay.day() < 17;
                    return new DayPermits(permitDay, current, generateHeatMapColor(current, 0, 14), slotId, presence);
                });
          });
    }

}

