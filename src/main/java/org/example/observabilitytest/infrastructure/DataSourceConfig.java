package org.example.observabilitytest.infrastructure;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

	@Bean
	public DataSource dataSource(OpenTelemetry openTelemetry, DataSourceProperties dataSourceProperties) {
		var dataSourceBuilder = dataSourceProperties.initializeDataSourceBuilder();
		return JdbcTelemetry.create(openTelemetry).wrap(dataSourceBuilder.build());
	}

}