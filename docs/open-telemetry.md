# OpenTelemetry

## Why?

- Looking at observability at work
- Stumbled across announcement of OTEL LGTM
  - Had found other open-source backends a pain
- Decided to look at OpenTelemetry again
- Wanted to put observability in some sort of context
  - A standards-based approach is a good way to understand the concept rather than a specific vendor's view

## Intro

- We'll look at OpenTelemetry concepts
- We'll look at the OpenTelemetry implementation components
- We'll look at a demo
  - Auto Instrumentation

## What is OpenTelemetry?

- Observability toolkit and framework to create and manage telemetry data

## Overview

- It is vendor and tool agnostic
- Its focus is on generation, collection, management and export - allowing instrumentation irrespective of language, runtime or infrastructure
- It is not a backend like Datadog, Prometheus, Jaeger - no storage, visualisation, querying

- Set of standards and specifications
  - API, wire protocols and semantic standards to allow interoperation
  - Good documentation to explain concepts and details
- Set of implementations - SDKs, Collector

## Standards & Specifications

- Observability is the ability to understand the internal state of a system by looking at its output
- What outputs are these? OpenTelemetry calls these Signals

- Signals
  - [Metrics](https://opentelemetry.io/docs/concepts/signals/metrics/) - a measurement captured at runtime
  - Traces - allows you to understand the full path of a request through your distributed system
    - Spans - the individual operations that make up a trace, each with a start time, duration, and attributes
  - Logs - timestamped text record, structured or unstructured; may be associated with a span
  - Baggage - key-value pairs attached to a distributed context and propagated across service boundaries alongside a trace

### [Semantic Conventions](https://opentelemetry.io/docs/concepts/semantic-conventions/)

Provide common names for different kinds of operations and data

![HTTP semantic convention attributes showing standard names for HTTP method, URL, status code, etc.](images/http_semantics.png)

### OTLP - OpenTelemetry Protocol

Standard wire protocol for transmitting telemetry data between SDKs, the Collector, and backends. Supports gRPC and HTTP/protobuf transports.

## Implementation

What are the components that the implementation provides

![OpenTelemetry implementation components: SDK, Instrumentation Libraries, Collector](images/open_telemetry_implementation_components.png)

- Language SDKs for Java, .NET, etc.
  - Provides automatic instrumentation (similar to the Datadog Agent) for Spring
  - Instrumentation Libraries
  - Other libraries also support OpenTelemetry, e.g. Micrometer (will see in demo)

- Collector
  - SDKs can talk directly to a backend or via a Collector

  ![No-collector deployment: SDK exports directly to backend](images/no_collector_deployment.png)

  - Collector can gather from many sources (OTLP/others) and publish to many backends (OTLP/others)

  ![Agent deployment: SDK to local Collector to backend](images/agent_deployment.png)
  ![Gateway deployment: multiple SDKs to central Collector to backend](images/gateway_deployment.png)

- Collector Design

![Collector internal architecture: receivers, processors, exporters](images/collector_architecture.png)

This is how the processing pipeline works in the Collector

![Pipeline flow: receiver to processor to exporter](images/pipeline.png)

See how the pipeline matches the definition in config

![Pipeline config YAML matching the pipeline diagram](images/pipeline_definition.png)

## Demo

#### Demo

The demo uses three services communicating over HTTP, plus a fourth communicating over Kafka — all auto-instrumented with the OpenTelemetry Java agent.

**Service topology:**

- **Greeting** (port 8080) — orchestrates a greeting by calling Salutation and Visitor over HTTP
- **Salutation** (port 8081) — determines time-of-day greeting by requesting the current time from Time-Provider **over Kafka** (request-reply)
- **Visitor** (port 8082) — counts and persists visitor numbers in MySQL
- **Time-Provider** (port 8083) — listens on Kafka `time-request` topic, replies with current hour on `time-reply`

**What gets observed:**

- **Distributed traces** — a single `curl` to Greeting produces a trace spanning HTTP → Kafka produce → Kafka consume (Time-Provider) → Kafka produce (reply) → Kafka consume (Salutation). Visible in Grafana Tempo.
- **Kafka broker metrics** — the OTel Collector scrapes the Kafka broker via `kafkametricsreceiver` (topics, consumer groups, broker stats). Visible in Grafana via Prometheus.
- **MySQL metrics** — scraped by the OTel Collector's `mysqlreceiver`.
- **Logs** — correlated with trace IDs via the OpenTelemetry Logback appender.

No manual `@Span` annotations or OTel SDK calls — everything is instrumented automatically by `opentelemetry-javaagent.jar`.

![demo_c4_container_diagram.png](images/demo_c4_container_diagram.png)

## Summary

- OpenTelemetry provides a vendor-neutral standard for telemetry data
- Auto instrumentation means zero code changes for common frameworks
- The Collector decouples your services from your backend - switch backends without touching your code
- Resources
  - [opentelemetry.io](https://opentelemetry.io)
  - [Java agent](https://opentelemetry.io/docs/zero-code/java/agent/)
  - [Grafana LGTM](https://github.com/grafana/docker-otel-lgtm)
