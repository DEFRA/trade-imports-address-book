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
    // When
    metrics.recordSearch(searchType);

    // Then
    assertThat(meterRegistry.get("AddressSearch").tag("SearchType", tagValue).counter().count())
        .isEqualTo(1.0);
  }

  @Test
  void recordCreateUpdateAndDelete_shouldCountOneChangeOfEachAction() {
    // When
    metrics.recordCreate();
    metrics.recordUpdate();
    metrics.recordDelete();

    // Then
    assertThat(changesOf("create")).isEqualTo(1.0);
    assertThat(changesOf("update")).isEqualTo(1.0);
    assertThat(changesOf("delete")).isEqualTo(1.0);
  }

  private double changesOf(String action) {
    return meterRegistry.get("AddressChange").tag("Action", action).counter().count();
  }
}
