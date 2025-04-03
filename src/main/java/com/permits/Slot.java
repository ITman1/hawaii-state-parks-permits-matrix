package com.permits;

import java.util.List;

public record Slot(String parkName, String slotName, List<DayPermits> dayPermits) {

}
