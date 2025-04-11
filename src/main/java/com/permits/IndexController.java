package com.permits;

import com.permits.provider.SlotsProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Controller
public class IndexController {

    private final TimeSupport timeSupport;
    private final List<SlotsProvider> slotsProviders;

    @GetMapping("/")
    public ModelAndView permits(Map<String, Object> model, @RequestParam(required = false) String recaptchaToken) {
        var dates = generateDateWindow();
        List<Slot> slots;
        if (StringUtils.hasText(recaptchaToken)) {
            slots = slotsProviders
              .stream()
              .map(provider -> provider.getSlots(recaptchaToken, dates))
              .reduce(CompletableFuture.completedFuture(new ArrayList<Slot>()), (future, slotsFuture) -> future.thenCombine(slotsFuture, (s1, s2) -> {
                  s1.addAll(s2);
                  return s1;
              }))
              .join();
        } else {
            slots = new ArrayList<>();
        }

        model.put("title", timeSupport.getStart().format(DateTimeFormatter.ofPattern("d. M.")) + " - " + timeSupport.getEnd().format(DateTimeFormatter.ofPattern("d. M.")));
        model.put("dates", dates);
        model.put("slots", slots);

        return new ModelAndView("index", model);
    }

    public List<PermitDate> generateDateWindow() {
        List<PermitDate> dates = new ArrayList<>();

        for (LocalDate date = timeSupport.getStart(); !date.isAfter(timeSupport.getEnd()); date = date.plusDays(1)) {
            dates.add(new PermitDate(date.getDayOfMonth(), date.getMonthValue()));
        }

        return dates;
    }
}
