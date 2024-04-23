package org.example.observabilitytest;

import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OtelLoggingConfig {

    @Value("${otlp.tracing.endpoint}")
    String endpoint;

    @Bean
    SdkLoggerProvider otelSdkLoggerProvider() {
        return SdkLoggerProvider.builder()
                .addLogRecordProcessor(
                        BatchLogRecordProcessor.builder(
                            OtlpHttpLogRecordExporter.builder().setEndpoint(endpoint).build())
                        .build())
                .build();
    }

}
