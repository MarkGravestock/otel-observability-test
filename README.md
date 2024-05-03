## OTEL Demo Project

### Technologies
 
 - Spring Boot
   - Auto Instrumentation using Open Telemetry Agent (branch auto-instrumentation)
   - Instrumentation using Spring Observability and Micrometer (branch main)
 - Open Telementry Collector
 - Open Telementry LGTM Backend

#### See [Documentation](docs/open-telemetry.md) for details.

Note the demo OTEL Collector config will try and connect to uptrace/honeycomb/datadog

If you have connection details for these you can provide them in file call cloud.env under the /config directory
