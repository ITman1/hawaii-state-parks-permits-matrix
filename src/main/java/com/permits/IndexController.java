package com.permits;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.awt.*;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Controller
public class IndexController implements DisposableBean {
    private final HttpClient httpClient = createHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final LocalDate start = LocalDate.now();
    private final LocalDate end = start.plusDays(30);

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @GetMapping("/")
    public ModelAndView permits(Map<String, Object> model) {
        System.out.println("Permits called at " + LocalDate.now());

        var dates = generateDateWindow();
        var slots = new ArrayList<Slot>();

        slots.addAll(getParkPermits("Waina papá", dates, "61dd69adf4476d02b032ae48", "6569690e5064ad20485ed20d", 74, Map.of(
          2, "10:00-12:30",
          3, "12:30-15:00",
          4, "15:00-18:00"
        )));

        slots.addAll(getParkPermits("Leo Valley", dates, "63edc307c8f7df7ca39389d0", "643cc6a9b638c78a710c78d2", 20, Map.of(
          5, "9:45-11:15",
          6, "10:30-12:00",
          7, "11:15-12:45",
          8, "12:00-1:30",
          9, "12:45-2:15",
          10, "1:30-3:00",
          11, "2:15-3:45",
          12, "3:00-4:30",
          13, "3:45-5:15",
          14, "4:30-6:00"
        )));

        model.put("title", start.format(DateTimeFormatter.ofPattern("d. M.")) + " - " + end.format(DateTimeFormatter.ofPattern("d. M.")));
        model.put("dates", dates);
        model.put("slots", slots);

        return new ModelAndView("index", model);
    }

    public List<PermitDate> generateDateWindow() {
        List<PermitDate> dates = new ArrayList<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            dates.add(new PermitDate(date.getDayOfMonth(), date.getMonthValue()));
        }

        return dates;
    }

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
              return new Slot(parkName, slots.get(slotId), dayPermits);
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
                      return new DayPermits(timeslot.capacity, color, timeslot.slotId);
                  })
                  .toList();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static HttpClient createHttpClient() {
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{MOCK_TRUST_MANAGER}, new SecureRandom());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        return HttpClient.newBuilder()
          .sslContext(sslContext)
          .version(HttpClient.Version.HTTP_1_1) // There is JDK bug with HTTP 2 - https://bugs.openjdk.org/browse/JDK-8335181
          .connectTimeout(Duration.ofSeconds(30))
          .build();
    }

    @Override
    public void destroy() throws Exception {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
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


    @SuppressWarnings({"java:S1186", "java:S4830"})
    private static final TrustManager MOCK_TRUST_MANAGER = new X509ExtendedTrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {

        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {

        }

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {

        }

        @Override
        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            // empty method
        }
    };

    public static Color generateHeatMapColor(int value, int minValue, int maxValue) {
        // Normalize the value to a range between 0 and 1
        double normalizedValue = (double) (value - minValue) / (maxValue - minValue);

        double h = normalizedValue * 0.35; // Hue (note 0.4 = Green, see huge chart below)
        double s = 0.9; // Saturation
        double b = 0.9; // Brightness

        return Color.getHSBColor((float) h, (float) s, (float) b);
    }

}
