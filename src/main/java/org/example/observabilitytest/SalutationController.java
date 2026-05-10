package org.example.observabilitytest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@Slf4j
@Profile("salutation")
public class SalutationController {

    private final ReplyingKafkaTemplate<String, String, String> replyingKafkaTemplate;

    @GetMapping("/salutation")
    String salutation() throws Exception {
        log.info("Requesting time via Kafka");
        var record = new ProducerRecord<String, String>("time-request", "get-time");
        var future = replyingKafkaTemplate.sendAndReceive(record);
        try {
            var hourOfDay = Integer.parseInt(future.get(5, TimeUnit.SECONDS).value());
            log.info("Received hour via Kafka {}", hourOfDay);
            return switch (hourOfDay / 6) {
                case 0, 1 -> "Good Morning";    // 0–11
                case 2    -> "Good Afternoon";  // 12–17
                default   -> "Good Evening";    // 18–23
            };
        } catch (Exception e) {
            log.warn("Time-provider unavailable, using fallback salutation");
            return "Hello";
        }
    }
}
