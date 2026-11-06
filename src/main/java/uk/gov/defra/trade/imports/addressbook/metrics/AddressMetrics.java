package uk.gov.defra.trade.imports.addressbook.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Counts changes to addresses as {@code address.change}, tagged {@code action}. */
@Component
@RequiredArgsConstructor
public class AddressMetrics {

  static final String CHANGE_METRIC = "address.change";

  /** A change made to an address. */
  public enum Change {
    CREATE,
    UPDATE,
    DELETE
  }

  private final MeterRegistry meterRegistry;

  /**
   * Counts one change.
   *
   * @param change the change made
   */
  public void countChange(Change change) {
    Counter.builder(CHANGE_METRIC)
        .tag("action", change.name().toLowerCase(Locale.ROOT))
        .register(meterRegistry)
        .increment();
  }
}
