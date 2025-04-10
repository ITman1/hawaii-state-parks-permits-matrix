package com.permits.provider;

import com.permits.PermitDate;
import com.permits.Slot;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Order(1)
@Component
public class LeoValleyParkProvider extends MauiParkProvider {

    @Override
    public CompletableFuture<List<Slot>> getSlots(List<PermitDate> dates) {
        return CompletableFuture.completedFuture(getParkPermits("Leo Valley", dates, "63edc307c8f7df7ca39389d0", "643cc6a9b638c78a710c78d2", 20, Map.of(
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
    }
}
