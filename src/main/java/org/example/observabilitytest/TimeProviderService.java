package org.example.observabilitytest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

@Service
@Profile("time-provider")
@Slf4j
public class TimeProviderService {

    @KafkaListener(topics = "time-request", groupId = "time-provider-group")
    @SendTo
    public String provideTime(String request) {
        var currentHour = LocalTime.now().getHour();
        log.info("Received time request, returning current hour {}", currentHour);
        return String.valueOf(currentHour);
    }
}
