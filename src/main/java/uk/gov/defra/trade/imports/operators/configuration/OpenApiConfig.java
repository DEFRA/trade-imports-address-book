package uk.gov.defra.trade.imports.operators.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds springdoc's schema generation to the application {@link ObjectMapper} (design §1.6). Without
 * this, swagger-core resolves models through its own default mapper and emits camelCase property
 * names ({@code operatorType}), so the generated {@code /v3/api-docs} would diverge from both the
 * snake_case runtime wire and the locked contract — the exact divergence {@code OperatorComplianceIT}
 * fails the build on. The {@link ModelResolver} makes the {@link JacksonConfig} snake_case naming
 * strategy authoritative for the generated document too.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  ModelResolver modelResolver(ObjectMapper objectMapper) {
    return new ModelResolver(objectMapper);
  }
}
