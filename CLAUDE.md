# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Start infrastructure (OTEL Collector, Grafana LGTM, PostgreSQL)
docker-compose up -d

# Run each service in a separate terminal — Java agent is pre-configured
./gradlew runGreeting     # port 8080
./gradlew runSalutation   # port 8081
./gradlew runVisitor      # port 8082
./gradlew runTimeProvider # port 8083

# Trigger a distributed trace (requires all 4 services + docker-compose up -d)
curl "http://localhost:8080/greeting?name=World"
```

Grafana UI: `http://localhost:3000` (admin/admin) — traces visible in Explore → Tempo.

## Architecture

This project is an educational demo of OpenTelemetry auto-instrumentation with four Spring Boot microservices sharing a single codebase, differentiated by Spring profiles.

**Service interaction:**
```
User → Greeting Service (8080)
           ├→ Salutation Service (8081)   [requests time via Kafka, returns greeting text]
           │       └→ Kafka time-request → Time-Provider Service (8083)
           └→ Visitor Service (8082)      [visitor count, persisted in PostgreSQL]
```

**Telemetry pipeline:**
```
All Services (auto-instrumented via libs/opentelemetry-javaagent.jar)
    ↓ OTLP (traces, metrics, logs)
OpenTelemetry Collector (config/otel-collector-config.yaml)
    ↓ processes + forwards                  ← also scrapes PostgreSQL (postgresqlreceiver)
    ↓                                       ← also scrapes Kafka (kafkametricsreceiver)
Grafana LGTM stack (Tempo/Prometheus/Loki) → Grafana UI
```

Services are instrumented automatically via the Java agent — no manual `@Span` or SDK setup required. The `logback-spring.xml` integrates the OpenTelemetry Logback appender to correlate logs with traces.

## Key Design Decisions

- **Single codebase, multiple services**: All four controllers exist in one Spring Boot app. Profile-specific `application-{profile}.properties` files set the service name, port, and which beans are active.
- **Auto-instrumentation only**: The project explicitly moved away from manual OTel SDK configuration (`dfe4ab2`). The `libs/opentelemetry-javaagent.jar` agent handles all instrumentation.
- **Collector as central hub**: `config/otel-collector-config.yaml` includes commented-out exporters for Datadog, Honeycomb, and Uptrace — the collector is the switching point for routing telemetry to different backends.
- **PostgreSQL metrics**: The collector's `postgresqlreceiver` scrapes PostgreSQL metrics directly, alongside app telemetry.
- **Kafka metrics**: The collector's `kafkametricsreceiver` scrapes Kafka broker metrics directly, providing visibility into the message bus used by the Salutation ↔ Time-Provider flow.

## Configuration

OTel collector endpoint and service names are set via environment variables (OTEL_EXPORTER_OTLP_ENDPOINT, OTEL_SERVICE_NAME). These are injected by Docker Compose or must be set manually when running locally outside Docker.

The `config/cloud.env` file holds environment variables for cloud backend integrations (Datadog, Honeycomb, etc.) — not committed with secrets.