package org.example.observabilitytest.infrastructure;

import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class OtelLogExporterConfig {

    @Value("${otlp.tracing.endpoint}")
    private String endpoint;

    @Bean
    SdkLoggerProvider otelSdkLoggerProvider(Resource resource) {
        return SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(
                BatchLogRecordProcessor.builder(
                    OtlpHttpLogRecordExporter.builder().setEndpoint(endpoint).build())
                .build())
            .build();
    }

}
