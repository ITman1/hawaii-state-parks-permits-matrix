package com.permits;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class TimeSupport {
    public LocalDate getStart() {
        return LocalDate.now();
    }

    public LocalDate getEnd() {
        return getStart().plusDays(30);
    }

}
