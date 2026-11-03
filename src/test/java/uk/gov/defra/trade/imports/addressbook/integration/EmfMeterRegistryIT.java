package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import software.amazon.cloudwatchlogs.emf.config.EnvironmentConfigurationProvider;
import software.amazon.cloudwatchlogs.emf.environment.Environments;
import uk.gov.defra.trade.imports.addressbook.address.AddressSearchType;
import uk.gov.defra.trade.imports.addressbook.metrics.AddressMetrics;
import uk.gov.defra.trade.imports.addressbook.metrics.EmfMeterRegistry;
import uk.gov.defra.trade.imports.addressbook.metrics.ExternalCall;
import uk.gov.defra.trade.imports.addressbook.metrics.ExternalCallMetrics;

@TestPropertySource(
    properties = {"aws.emf.enabled=true", "aws.emf.namespace=trade-imports-address-book"})
class EmfMeterRegistryIT extends IntegrationBase {

  static {
    EnvironmentConfigurationProvider.getConfig().setEnvironmentOverride(Environments.Local);
  }

  @Autowired private MeterRegistry meterRegistry;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private AddressMetrics addressMetrics;

  @Autowired private ExternalCallMetrics externalCallMetrics;

  @Test
  void listAddresses_shouldRecordItsTimedAndMongoMetricsInTheEmfRegistry() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set(ORG_HEADER, ORGANISATION_ID);

    // When
    var response =
        restTemplate.exchange(
            "/organisation/" + ORGANISATION_ID + "/addresses",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(meterRegistry).isInstanceOf(EmfMeterRegistry.class);
    assertThat(meterRegistry.find("controller.listOperators.time").timer()).isNotNull();
    assertThat(
            meterRegistry
                .find("ExternalCallDuration")
                .tags("Dependency", "mongodb", "Operation", "find")
                .timer())
        .isNotNull();
    assertThat(meterRegistry.find("AddressSearch").tag("SearchType", "none").counter())
        .isNotNull();
  }

  @Test
  void everyCustomMetric_shouldBeLetThroughTheMetricsAllowlist() {
    // When
    addressMetrics.recordCreate();
    addressMetrics.recordSearch(AddressSearchType.NONE);
    externalCallMetrics.recordSuccess(new ExternalCall("mongodb", "find"), Duration.ofMillis(1));

    // Then
    assertThat(meterRegistry.find("AddressChange").counter()).isNotNull();
    assertThat(meterRegistry.find("AddressSearch").counter()).isNotNull();
    assertThat(meterRegistry.find("ExternalCallDuration").timer()).isNotNull();
    assertThat(meterRegistry.find("ExternalCallFailure").summary()).isNotNull();
    assertThat(meterRegistry.find("DependencyAvailable").gauge()).isNotNull();
  }
}
