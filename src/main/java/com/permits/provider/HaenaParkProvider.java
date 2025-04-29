package com.permits.provider;

import com.permits.DayPermits;
import com.permits.PermitDate;
import com.permits.Slot;
import com.permits.util.EmailSender;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor
@Slf4j
@Order(4)
@Component
public class HaenaParkProvider extends SlotsProvider {

    private final EmailSender emailSender;

    @Override
    public CompletableFuture<List<Slot>> getSlots(String recaptchaToken, List<PermitDate> dates) {
        List<Map<String, Object>> json = getJson(recaptchaToken, dates);

        json = json.stream()
          .filter((Map<String, Object> entry) -> ((String) entry.get("description")).startsWith("WT"))
          .toList();

        SortedMap<Object, List<Map<String, Object>>> slotsByStartTime = new TreeMap<>(json.stream()
          .collect(Collectors.groupingBy(entry -> entry.get("startTime"))));

        List<Slot> slots = new ArrayList<>();
        for (Map.Entry<Object, List<Map<String, Object>>> slot : slotsByStartTime.entrySet()) {
            String startTime = (String) slot.getValue().get(0).get("startTimeFriendly");

            List<DayPermits> dayPermits = slot.getValue().stream()
              .flatMap((Map<String, Object> day) -> {
                  int remainingQuantity = (int) day.get("remainingQuantity");
                  var date = LocalDate.parse(String.valueOf((int) day.get("dateInt")), DateTimeFormatter.ofPattern("yyyyMMdd"));
                  var permitDate = new PermitDate(date.getDayOfMonth(), date.getMonthValue());
                  Color colour = MauiParkProvider.generateHeatMapColor(remainingQuantity, 0, 24);
                  return (int) day.get("startTime") >= 1000 ? Stream.empty() : Stream.of(new DayPermits(permitDate, remainingQuantity, colour, "", permitDate.month() == 5 && permitDate.day() > 10 && permitDate.day() < 17));
              })
              .toList();

            if (!dayPermits.isEmpty()) {
                slots.add(new Slot("Haena", startTime, dayPermits));
            }
        }

        return CompletableFuture.completedFuture(slots);
    }

    private List<Map<String, Object>> getJson(String recaptchaToken, List<PermitDate> dates) {
        Map<String, String> parameters = new LinkedHashMap<>(Map.of(
          "method", "validateGoogleRecaptchaAndGetStartTimesForProductsAndDateRanges",
          "getStartTimesForProductsAndDateRangesRequest[recaptchaTokenRequest][action]", "KNSS_ShuttleParkingParkEntry_GetStartTimesForProductsAndDateRanges",
          "getStartTimesForProductsAndDateRangesRequest[recaptchaTokenRequest][token]", recaptchaToken
        ));

        for (int i = 0; i < dates.size(); i++) {
            parameters.put("getStartTimesForProductsAndDateRangesRequest[occurrenceDescriptors][" + i + "][productId]", "2");
            parameters.put("getStartTimesForProductsAndDateRangesRequest[occurrenceDescriptors][" + i + "][dateInt]", "2025" + String.format("%02d%02d", dates.get(i).month(), dates.get(i).day()));
            parameters.put("getStartTimesForProductsAndDateRangesRequest[occurrenceDescriptors][" + i + "][isReturnTrip]", "false");
            parameters.put("getStartTimesForProductsAndDateRangesRequest[occurrenceDescriptors][" + i + "][pickupLocationId]", "1");
            parameters.put("getStartTimesForProductsAndDateRangesRequest[occurrenceDescriptors][" + i + "][dropoffLocationId]", "6");
        }

        String form = parameters.entrySet()
          .stream()
          .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
          .collect(Collectors.joining("&"));

        HttpRequest.Builder requestBuilder = null;
        try {
            requestBuilder = HttpRequest.newBuilder()
              .uri(new URI("https://gohaena.com/wp-content/plugins/knss_gohaena_shuttle_parking_park_entry/api.php"))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(form)).timeout(Duration.ofSeconds(30));

            var responseString = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            return (List<Map<String, Object>>) mapper.readValue(responseString.body(), List.class);
        } catch (Exception e) {
            log.error("Error while getting JSON from Haena Park", e);
            return List.of();
        }
    }

}

