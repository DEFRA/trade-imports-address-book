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
 * Describes the API for springdoc and resolves its schemas through the application
 * {@link ObjectMapper}, so the generated document uses the same property names as the wire.
 */
@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "trade-imports-address-book",
            version = "1.0.0",
            description = "Org-scoped address book API for EUDP Live Animals"))
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
                                + " path orgId")))
        .addSecurityItem(new SecurityRequirement().addList(ORGANISATION_ID_SCHEME));
  }

  @Bean
  ModelResolver modelResolver(ObjectMapper objectMapper) {
    return new ModelResolver(objectMapper.copy());
  }
}
