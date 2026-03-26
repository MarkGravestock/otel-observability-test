package org.example.observabilitytest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.requestreply.RequestReplyFuture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@Slf4j
@Profile("salutation")
public class SalutationController {

    private final ReplyingKafkaTemplate<String, String, String> replyingKafkaTemplate;

    // TimeoutException / ExecutionException propagates as HTTP 500 by design —
    // time-provider must be running. See design spec for failure-mode rationale.
    @GetMapping("/salutation")
    String salutation() throws Exception {
        log.info("Requesting time via Kafka");
        var record = new ProducerRecord<String, String>("time-request", "get-time");
        var future = replyingKafkaTemplate.sendAndReceive(record);
        var hourOfDay = Integer.parseInt(future.get(5, TimeUnit.SECONDS).value());

        if (hourOfDay < 12) return "Good Morning";
        if (hourOfDay < 18) return "Good Afternoon";
        return "Good Evening";
    }
}
