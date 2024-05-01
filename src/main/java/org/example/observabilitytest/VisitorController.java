package org.example.observabilitytest;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@Profile("visitor")
public class VisitorController {

    private final Counter visitCounter;
    private final VisitorRepository visitorRepository;

    public VisitorController(MeterRegistry meterRegistry, VisitorRepository visitorRepository) {
        this.visitorRepository = visitorRepository;
        visitCounter = Counter.builder("visitor.counter").register(meterRegistry);
    }


    @GetMapping("/permanent-visit")
    Long permanentVisit() {
        long counterId = 1L;

        var visitorCounter = visitorRepository.findById(counterId).orElse(VisitorCounter.of(counterId));

        visitorCounter.increment();
        visitCounter.increment();

        log.info("Visit number {}", visitorCounter.getVisitorCount());

        visitorRepository.save(visitorCounter);

        return visitorCounter.getVisitorCount();
    }

}
