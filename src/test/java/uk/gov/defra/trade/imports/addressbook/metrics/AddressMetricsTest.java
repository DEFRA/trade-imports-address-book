package uk.gov.defra.trade.imports.addressbook.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.defra.trade.imports.addressbook.address.AddressSearchType;

class AddressMetricsTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final AddressMetrics metrics = new AddressMetrics(meterRegistry);

  @ParameterizedTest
  @CsvSource({
    "NONE, none",
    "QUERY, query",
    "COUNTRY, country",
    "QUERY_AND_COUNTRY, query-and-country"
  })
  void recordSearch_shouldCountOneSearchTaggedByItsType(
      AddressSearchType searchType, String tagValue) {
    metrics.recordSearch(searchType);

    assertThat(meterRegistry.get("address.search").tag("search.type", tagValue).counter().count())
        .isEqualTo(1.0);
  }

  @Test
  void recordCreate_shouldCountOneCreateChange() {
    metrics.recordCreate();

    assertThat(changesOf("create")).isEqualTo(1.0);
  }

  @Test
  void recordUpdate_shouldCountOneUpdateChange() {
    metrics.recordUpdate();

    assertThat(changesOf("update")).isEqualTo(1.0);
  }

  @Test
  void recordDelete_shouldCountOneDeleteChange() {
    metrics.recordDelete();

    assertThat(changesOf("delete")).isEqualTo(1.0);
  }

  private double changesOf(String action) {
    return meterRegistry.get("address.change").tag("action", action).counter().count();
  }
}
