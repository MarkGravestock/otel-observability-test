package org.example.observabilitytest;


import io.micrometer.core.annotation.Timed;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@Timed
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final ObservationRegistry observationRegistry;

    // Spring AOP to be enabled for this
    @Timed(value = "message.api.timing")
    @GetMapping("/message")
    String message() {
        RestClient client = RestClient.builder().observationRegistry(observationRegistry).baseUrl("https://publisher-service.uat.develop.farm/api/v1").build();

        log.info("Generating Message");

        return Observation.createNotStarted("calling.publisher", this.observationRegistry).observe(() -> {
            var result = client.get().uri("/publishers/identifier/JCDECAUX_AU").retrieve().toEntity(String.class);
            return result.getBody();
        });
    }
}
