package uk.gov.defra.trade.imports.addressbook.configuration;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the whole API camelCase: the Spring-managed {@code ObjectMapper} serialises and
 * deserialises with {@link PropertyNamingStrategies#LOWER_CAMEL_CASE}. Enum values stay
 * UPPER_SNAKE (their {@code name()}).
 *
 * <p>Unknown properties are ignored, not rejected, so a request body carrying server-assigned
 * fields such as {@code id}, {@code status} or timestamps is accepted and those fields dropped.
 */
@Configuration
public class JacksonConfig {

  @Bean
  Jackson2ObjectMapperBuilderCustomizer camelCaseNamingStrategy() {
    return builder ->
        builder
            .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
            .failOnUnknownProperties(false)
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }
}
