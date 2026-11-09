package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedConsole.captureSystemOut;
import static uk.gov.defra.trade.imports.addressbook.testsupport.EmfDocuments.namespaceOf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import uk.gov.defra.trade.imports.addressbook.metrics.EmfMeterRegistry;
import uk.gov.defra.trade.imports.addressbook.testsupport.CapturedConsole;

@TestPropertySource(
    properties = {
      "aws.emf.enabled=true",
      "aws.emf.environment=Local",
      "aws.emf.namespace=trade-imports-address-book",
      "aws.emf.step=1s"
    })
class EmfMeterRegistryIT extends IntegrationBase {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private MeterRegistry meterRegistry;

  @Test
  void theMeterRegistryInUseIsTheEmfMeterRegistry() {
    assertThat(meterRegistry).isInstanceOf(EmfMeterRegistry.class);
  }

  @Test
  void theMetersAnAddressCreateRecordsAndTheDependencyGaugeAreLetThroughTheAllowlist()
      throws Exception {
    createdResourceId(postAddress(addressBody()));

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
  void theRegistryPublishesARecordedCallOnItsOwnSchedule() throws Exception {
    CapturedConsole console = captureSystemOut();
    try (console) {
      createdResourceId(postAddress(addressBody()));

      await()
          .atMost(Duration.ofSeconds(10))
          .pollInterval(Duration.ofSeconds(1))
          .until(() -> emfDocumentFor("external.call.duration", console).isPresent());
    }

    JsonNode document = emfDocumentFor("external.call.duration", console).orElseThrow();
    assertThat(namespaceOf(document)).isEqualTo("trade-imports-address-book");
    assertThat(document.path("dependency").asText()).isEqualTo("mongodb");
  }

  private static Optional<JsonNode> emfDocumentFor(String metricName, CapturedConsole console)
      throws IOException {
    for (String line : console.text().split("\n")) {
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
