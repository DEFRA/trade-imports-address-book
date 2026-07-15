package uk.gov.defra.trade.imports.operators.configuration;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the whole API snake_case: the Spring-managed {@code ObjectMapper} serialises and
 * deserialises with {@link PropertyNamingStrategies#SNAKE_CASE}. Enum values are unaffected and
 * stay UPPER_SNAKE (their {@code name()}).
 *
 * <p>Two digit-bearing fields ({@code addressLine1}/{@code addressLine2}) still need an explicit
 * {@code @JsonProperty} on their DTO components — the strategy renders them {@code address_line1},
 * not the contract's {@code address_line_1}. {@code GlobalExceptionHandler} resolves validation
 * error-map keys through this same {@code ObjectMapper} so a rejected field is reported under its
 * real wire name.
 *
 * <p>Unknown properties are ignored (not rejected) so a request body carrying server-assigned
 * fields — {@code id}, {@code status}, timestamps — is accepted and those fields dropped (§1.5).
 */
@Configuration
public class JacksonConfig {

  @Bean
  Jackson2ObjectMapperBuilderCustomizer snakeCaseNamingStrategy() {
    return builder ->
        builder
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .failOnUnknownProperties(false);
  }
}
