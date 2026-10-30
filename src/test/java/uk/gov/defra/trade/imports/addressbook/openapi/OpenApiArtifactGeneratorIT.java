package uk.gov.defra.trade.imports.addressbook.openapi;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.defra.trade.imports.addressbook.address.OperatorRepository;
import uk.gov.defra.trade.imports.addressbook.configuration.MongoConfig;
import uk.gov.defra.trade.imports.addressbook.testsupport.ApiDocs;

@EnabledIfSystemProperty(named = "openapi.generate", matches = "true")
@SpringBootTest(
    classes = OpenApiArtifactGeneratorIT.OpenApiGeneratorApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class OpenApiArtifactGeneratorIT {

  @LocalServerPort int port;

  @MockBean OperatorRepository operatorRepository;

  @MockBean MongoTemplate mongoTemplate;

  @Test
  void regenerateCommittedOpenApiArtifact() throws IOException {
    Files.writeString(
        ApiDocs.COMMITTED_ARTIFACT, ApiDocs.renderCommittedForm(ApiDocs.fetchLive(port)));
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
