package org.example.observabilitytest;


import io.micrometer.core.annotation.Timed;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.LocalTime;

@RestController
@Timed
@RequiredArgsConstructor
@Slf4j
@Profile("salutation")
public class SalutationController {

    private final ObservationRegistry observationRegistry;

    @Timed(value = "salutation.api.timing")
    @GetMapping("/salutation")
    String salutation() {
        log.info("Generating salutation");

        var hourOfDay = LocalTime.now().getHour();

        if (hourOfDay < 12) {
            return "Good Morning";
        }

        if (hourOfDay < 18) {
            return "Good Afternoon";
        }

        return "Good Evening";
    }
}
