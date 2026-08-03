package uk.gov.defra.trade.imports.addressbook.configuration;

import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds springdoc's schema generation to the application {@link ObjectMapper} (design §1.6). Without
 * this, swagger-core resolves models through its own default mapper, so the generated
 * {@code /v3/api-docs} could diverge from the runtime wire and the locked contract — the exact
 * divergence {@code OperatorComplianceIT} fails the build on. The {@link ModelResolver} makes the
 * {@link JacksonConfig} camelCase naming strategy (cv-001) authoritative for the generated document
 * too.
 */
@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "trade-imports-address-book",
            version = "1.0.0",
            description = "Org-scoped address book API for EUDP Live Animals (EUDPA-58)"))
public class OpenApiConfig {

  @Bean
  ModelResolver modelResolver(ObjectMapper objectMapper) {
    return new ModelResolver(objectMapper);
  }
}
