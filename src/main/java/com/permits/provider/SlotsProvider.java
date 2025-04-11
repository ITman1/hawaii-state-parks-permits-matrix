package com.permits.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.permits.PermitDate;
import com.permits.Slot;
import org.springframework.beans.factory.DisposableBean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.awt.*;
import java.net.Socket;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class SlotsProvider implements DisposableBean {
    protected final HttpClient httpClient = createHttpClient();
    protected final ObjectMapper mapper = new ObjectMapper();
    protected final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public abstract CompletableFuture<List<Slot>> getSlots(String recaptchaToken, List<PermitDate> dates);


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

    public static Color generateHeatMapColor(int value, int minValue, int maxValue) {
        // Normalize the value to a range between 0 and 1
        double normalizedValue = (double) (value - minValue) / (maxValue - minValue);

        double h = normalizedValue * 0.35; // Hue (note 0.4 = Green, see huge chart below)
        double s = 0.9; // Saturation
        double b = 0.9; // Brightness

        return Color.getHSBColor((float) h, (float) s, (float) b);
    }

}
