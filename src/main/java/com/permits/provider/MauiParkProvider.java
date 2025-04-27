package com.permits.provider;

import com.permits.DayPermits;
import com.permits.PermitDate;
import com.permits.Slot;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public abstract class MauiParkProvider extends SlotsProvider {

    public List<Slot> getParkPermits(String parkName, List<PermitDate> dates, String parkId, String ticketId, int maxValue, Map<Integer, String> slots) {
        List<Future<List<DayPermits>>> permits = new ArrayList<>();
        for (PermitDate date : dates) {
            permits.add(getDayPermits(parkId, ticketId, date, maxValue, slots));
        }
        return permits.stream()
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
              return new Slot(parkName, slots.get(Integer.parseInt(slotId)), dayPermits);
          })
          .toList();
    }

    public Future<List<DayPermits>> getDayPermits(String parkId, String ticketId, PermitDate date, int maxValue, Map<Integer, String> slots) {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return executor.submit(() -> {
            try {
                var requestBuilder = HttpRequest.newBuilder()
                  .uri(new URI(String.format("https://gostateparksapi.hawaii.gov/api/v1/parks/%s/timeslots?segment=visitor&ticketId=%s&quantity=2&date=%s", parkId, ticketId, "2025-" + date.month() + "-" + date.day())))
                  .method("GET", HttpRequest.BodyPublishers.noBody())
                  .header("Api-Key", "7adea29e739bb9cb60e615b0e23e5ae3")
                  .timeout(Duration.ofSeconds(30));

                var responseString = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
                var response = mapper.readValue(responseString.body(), Response.class);
                return response.data.timeslots
                  .stream()
                  .filter(t -> slots.containsKey(t.slotId))
                  .map(timeslot -> {
                      var color = generateHeatMapColor(timeslot.capacity, 0, maxValue);
                      return new DayPermits(date, timeslot.capacity, color, String.valueOf(timeslot.slotId), date.month() == 5 && date.day() > 4 && date.day() < 10);
                  })
                  .toList();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static class Response {
        public String message;
        public Data data;
        public Status status;

        // Getters and setters
    }

    public static class Data {
        public List<TicketType> ticketTypes;
        public List<Timeslot> timeslots;
        public String bookingStartDate;

        // Getters and setters
    }

    public static class TicketType {
        public String value;
        public String option;

        // Getters and setters
    }

    public static class Timeslot {
        public int slotId;
        public String label;
        public int capacity;

        // Getters and setters
    }

    public static class Status {
        public boolean status;
        public int count;

        // Getters and setters
    }
}
