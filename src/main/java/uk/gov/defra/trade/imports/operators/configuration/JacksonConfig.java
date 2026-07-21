package uk.gov.defra.trade.imports.operators.configuration;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the whole API camelCase (cv-001): the Spring-managed {@code ObjectMapper} serialises and
 * deserialises with {@link PropertyNamingStrategies#LOWER_CAMEL_CASE}, matching reference-data. Enum
 * values are unaffected and stay UPPER_SNAKE (their {@code name()}).
 *
 * <p>Unknown properties are ignored (not rejected) so a request body carrying server-assigned
 * fields — {@code id}, {@code status}, timestamps — is accepted and those fields dropped (§1.5).
 */
@Configuration
public class JacksonConfig {

  @Bean
  Jackson2ObjectMapperBuilderCustomizer camelCaseNamingStrategy() {
    return builder ->
        builder
            .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
            .failOnUnknownProperties(false);
  }
}
