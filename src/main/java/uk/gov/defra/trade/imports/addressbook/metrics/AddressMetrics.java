package uk.gov.defra.trade.imports.addressbook.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.defra.trade.imports.addressbook.address.AddressSearchType;

/**
 * Counts the address book's business events: {@code AddressSearch} by {@code SearchType} for each
 * list of addresses, and {@code AddressChange} by {@code Action} for each create, update and
 * delete.
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
