package uk.gov.defra.trade.imports.addressbook.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import uk.gov.defra.trade.imports.addressbook.address.OperatorRepository;
import uk.gov.defra.trade.imports.addressbook.configuration.MongoConfig;

/**
 * Regenerates {@code docs/openapi/operators.yml} without Testcontainers — for local doc refresh
 * when Docker is unavailable. The compliance IT still byte-checks the artifact on every build.
 *
 * <p>{@code mvn verify -Dopenapi.generate=true -Dit.test=OpenApiArtifactGeneratorIT}
 */
@SpringBootTest(
    classes = OpenApiArtifactGeneratorIT.OpenApiGeneratorApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class OpenApiArtifactGeneratorIT {

  private static final Path GENERATED_DOC = Path.of("docs/openapi/operators.yml");

  @LocalServerPort int port;

  @MockBean OperatorRepository operatorRepository;

  @MockBean MongoTemplate mongoTemplate;

  @Test
  @EnabledIfSystemProperty(named = "openapi.generate", matches = "true")
  void regenerateCommittedOpenApiArtifact() throws IOException {
    Map<String, Object> live = fetchApiDocs();
    live.remove("servers");
    Files.writeString(GENERATED_DOC, yaml().dump(live));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> fetchApiDocs() {
    String json =
        RestClient.create()
            .get()
            .uri("http://localhost:" + port + "/v3/api-docs")
            .retrieve()
            .body(String.class);
    try {
      return new ObjectMapper().readValue(json, Map.class);
    } catch (IOException e) {
      throw new AssertionError("could not parse /v3/api-docs", e);
    }
  }

  private static Yaml yaml() {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setSplitLines(false);
    return new Yaml(options);
  }

  @SpringBootApplication(
      exclude = {
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class,
        MongoRepositoriesAutoConfiguration.class
      })
  @Import(OpenApiArtifactGeneratorIT.MinimalMongoTestConfig.class)
  @ComponentScan(
      basePackages = "uk.gov.defra.trade.imports.addressbook",
      excludeFilters =
          @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = MongoConfig.class))
  static class OpenApiGeneratorApplication {}

  @TestConfiguration
  static class MinimalMongoTestConfig {
    @Bean
    MongoMappingContext mongoMappingContext() {
      return new MongoMappingContext();
    }
  }
}
