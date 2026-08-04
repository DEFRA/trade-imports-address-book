package uk.gov.defra.trade.imports.addressbook.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.defra.trade.imports.addressbook.filter.IdentityHeaderFilter;

/**
 * Binds springdoc's schema generation to the application {@link ObjectMapper} (design §1.6). Without
 * this, swagger-core resolves models through its own default mapper, so the generated
 * {@code /v3/api-docs} could diverge from the runtime wire and the locked contract — the exact
 * divergence {@code OperatorComplianceIT} fails the build on. The {@link ModelResolver} makes the
 * {@link JacksonConfig} camelCase naming strategy (cv-001) authoritative for the generated document
 * too.
 *
 * <p>springdoc emits per-operation {@code security} blocks, places {@code securitySchemes} after
 * {@code schemas}, and may reorder schema property keys — the committed {@code docs/openapi/operators.yml}
 * is a SnakeYAML byte-stable dump of live {@code /v3/api-docs} (servers stripped). Regenerate with
 * {@code mvn verify -Dopenapi.generate=true -Dit.test=OperatorComplianceIT#regenerateCommittedOpenApiArtifact}
 * after any annotation change; a plain build then fails until it is committed.
 */
@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "trade-imports-address-book",
            version = "1.0.0",
            description = "Org-scoped address book API for EUDP Live Animals (EUDPA-58)"))
public class OpenApiConfig {

  private static final String ORGANISATION_ID_SCHEME = "Trade-Imports-Organisation-Id";

  @Bean
  OpenAPI openAPI() {
    return new OpenAPI()
        .servers(
            List.of(new Server().url("http://localhost:8089").description("Local dev")))
        .components(
            new Components()
                .addSecuritySchemes(
                    ORGANISATION_ID_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(IdentityHeaderFilter.ORGANISATION_ID_HEADER)
                        .description(
                            "Caller organisation id from the trusted forwarded header; must match"
                                + " path orgId (cv-010)")))
        .addSecurityItem(new SecurityRequirement().addList(ORGANISATION_ID_SCHEME));
  }

  @Bean
  ModelResolver modelResolver(ObjectMapper objectMapper) {
    return new ModelResolver(objectMapper.copy());
  }
}
