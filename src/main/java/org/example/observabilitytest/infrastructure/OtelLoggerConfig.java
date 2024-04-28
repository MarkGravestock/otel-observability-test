package org.example.observabilitytest.infrastructure;

import io.opentelemetry.api.OpenTelemetry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class OtelLoggerConfig {

    private final OpenTelemetry openTelemetry;

    @PostConstruct
    void postConstruct() {
        io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender.install(openTelemetry);
    }
}
