package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

  @Autowired private AddressMetrics addressMetrics;

  @Autowired private ExternalCallMetrics externalCallMetrics;

  @Test
  void theApplicationsRegistry_shouldBeAnEmfRegistryWithItsPublisherRunning() {
    assertThat(meterRegistry).isInstanceOf(EmfMeterRegistry.class);
    assertThat(Thread.getAllStackTraces().keySet())
        .anySatisfy(thread -> assertThat(thread.getName()).startsWith("emf-metrics-publisher"));
  }

  @Test
  void listAddresses_shouldRecordItsTimersItsMongoFindAndItsSearch() throws Exception {
    Search controllerTimer = meterRegistry.find("controller.listOperators.time");
    Search serviceTimer = meterRegistry.find("service.listOperators.time");
    Search mongoFind =
        meterRegistry
            .find("external.call.duration")
            .tags("dependency", "mongodb", "operation", "find");
    Search unfilteredSearch = meterRegistry.find("address.search").tag("search.type", "none");
    removeEveryMeterFoundBy(controllerTimer, serviceTimer, mongoFind, unfilteredSearch);

    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isOk());

    assertThat(controllerTimer.timer()).isNotNull();
    assertThat(serviceTimer.timer()).isNotNull();
    assertThat(mongoFind.timer()).isNotNull();
    assertThat(unfilteredSearch.counter()).isNotNull();
  }

  @Test
  void everyCustomMetric_shouldBeLetThroughTheMetricsAllowlist() {
    addressMetrics.recordCreate();
    addressMetrics.recordSearch(AddressSearchType.NONE);
    externalCallMetrics.recordSuccess(new ExternalCall("mongodb", "find"), Duration.ofMillis(1));

    assertThat(meterRegistry.find("address.change").counter()).isNotNull();
    assertThat(meterRegistry.find("address.search").counter()).isNotNull();
    assertThat(meterRegistry.find("external.call.duration").timer()).isNotNull();
    assertThat(meterRegistry.find("external.call.failure").summary()).isNotNull();
    assertThat(meterRegistry.find("external.dependency.available").gauge()).isNotNull();
  }

  private void removeEveryMeterFoundBy(Search... searches) {
    for (Search search : searches) {
      search.meters().forEach(meterRegistry::remove);
    }
  }
}
