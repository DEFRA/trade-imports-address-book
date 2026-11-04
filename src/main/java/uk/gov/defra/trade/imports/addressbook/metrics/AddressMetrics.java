package uk.gov.defra.trade.imports.addressbook.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.defra.trade.imports.addressbook.address.AddressSearchType;

/**
 * Counts address book events on the meter registry: {@code AddressSearch} tagged {@code
 * SearchType}, and {@code AddressChange} tagged {@code Action} as {@code create}, {@code update}
 * or {@code delete}. Each record method adds one to its counter.
 */
@Component
@RequiredArgsConstructor
public class AddressMetrics {

  static final String SEARCH_METRIC = "AddressSearch";
  static final String SEARCH_TYPE_TAG = "SearchType";
  static final String CHANGE_METRIC = "AddressChange";
  static final String ACTION_TAG = "Action";

  private final MeterRegistry meterRegistry;

  public void recordSearch(AddressSearchType searchType) {
    meterRegistry.counter(SEARCH_METRIC, SEARCH_TYPE_TAG, searchType.metricValue()).increment();
  }

  public void recordCreate() {
    recordChange("create");
  }

  public void recordUpdate() {
    recordChange("update");
  }

  public void recordDelete() {
    recordChange("delete");
  }

  private void recordChange(String action) {
    meterRegistry.counter(CHANGE_METRIC, ACTION_TAG, action).increment();
  }
}
