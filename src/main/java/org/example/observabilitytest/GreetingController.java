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

@RestController
@Timed
@RequiredArgsConstructor
@Slf4j
@Profile("greeting")
public class GreetingController {

    private final ObservationRegistry observationRegistry;
    private final RestClient.Builder restClientBuilder;

    @Timed(value = "greeting.api.timing")
    @GetMapping("/greeting")
    String message(@RequestParam("name")String name) {
        RestClient client = restClientBuilder.observationRegistry(observationRegistry).build();

        log.info("Generating greeting");

        var salutation = Observation.createNotStarted("calling.salutation.service", this.observationRegistry).observe(() -> client.get()
                .uri("http://localhost:8081/salutation")
                .retrieve()
                .toEntity(String.class)
                .getBody());

        var visitNumber = Observation.createNotStarted("calling.visitor.service", this.observationRegistry).observe(() -> client.get()
                .uri("http://localhost:8082/permanent-visit")
                .retrieve()
                .toEntity(Long.class)
                .getBody());

        return String.format("%s, %s. You are our %d visitor",salutation, name, visitNumber);
    }
}
