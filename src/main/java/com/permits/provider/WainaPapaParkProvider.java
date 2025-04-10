package com.permits.provider;

import com.permits.PermitDate;
import com.permits.Slot;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Order(0)
@Component
public class WainaPapaParkProvider extends MauiParkProvider {

    @Async
    @Override
    public CompletableFuture<List<Slot>> getSlots(List<PermitDate> dates) {
        return CompletableFuture.completedFuture(getParkPermits("Waina papá", dates, "61dd69adf4476d02b032ae48", "6569690e5064ad20485ed20d", 74, Map.of(
          2, "10:00-12:30",
          3, "12:30-15:00",
          4, "15:00-18:00"
        )));
    }
}
