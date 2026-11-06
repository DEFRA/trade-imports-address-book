package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import software.amazon.cloudwatchlogs.emf.config.EnvironmentConfigurationProvider;
import software.amazon.cloudwatchlogs.emf.environment.Environments;
import uk.gov.defra.trade.imports.addressbook.metrics.EmfMeterRegistry;

@TestPropertySource(
    properties = {"aws.emf.enabled=true", "aws.emf.namespace=trade-imports-address-book"})
class EmfMeterRegistryIT extends IntegrationBase {

  static {
    EnvironmentConfigurationProvider.getConfig().setEnvironmentOverride(Environments.Local);
  }

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private MeterRegistry meterRegistry;

  @Test
  void theMeterRegistryInUseIsTheEmfMeterRegistry() {
    assertThat(meterRegistry).isInstanceOf(EmfMeterRegistry.class);
  }

  @Test
  void everyMongoCommandAndAddressChangeMeterIsLetThroughTheAllowlist() throws Exception {
    createdAddressId(postAddress(addressBody()));

    for (String name :
        List.of(
            "external.call.duration",
            "external.call.failure",
            "external.dependency.available",
            "address.change",
            "controller.create.time",
            "service.create.time")) {
      assertThat(meterRegistry.find(name).meter()).as(name).isNotNull();
    }
  }

  @Test
  void theRegistryPublishesARecordedCallOnItsOwnScheduleWithin2Minutes() throws Exception {
    ByteArrayOutputStream console = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(console, true, StandardCharsets.UTF_8));
    try {
      createdAddressId(postAddress(addressBody()));

      await()
          .atMost(Duration.ofMinutes(2))
          .pollInterval(Duration.ofSeconds(1))
          .until(() -> emfDocumentFor("external.call.duration", console).isPresent());
    } finally {
      System.setOut(originalOut);
    }

    JsonNode document = emfDocumentFor("external.call.duration", console).orElseThrow();
    assertThat(document.path("_aws").path("CloudWatchMetrics").get(0).path("Namespace").asText())
        .isEqualTo("trade-imports-address-book");
    assertThat(document.path("dependency").asText()).isEqualTo("mongodb");
  }

  private static Optional<JsonNode> emfDocumentFor(String metricName, ByteArrayOutputStream console)
      throws IOException {
    for (String line : console.toString(StandardCharsets.UTF_8).split("\n")) {
      if (line.contains("\"_aws\"") && line.contains("\"" + metricName + "\"")) {
        return Optional.of(JSON.readTree(line));
      }
    }
    return Optional.empty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"cache.gets", "mongodb.driver.commands", "spring.data.repository.invocations"})
  void aMeterOffTheAllowlistIsNeverRegistered(String name) {
    meterRegistry.counter(name).increment();

    assertThat(meterRegistry.find(name).meter()).isNull();
  }
}
