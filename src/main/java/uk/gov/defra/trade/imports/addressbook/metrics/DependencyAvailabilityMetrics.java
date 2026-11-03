package uk.gov.defra.trade.imports.addressbook.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Binds the gauge {@code DependencyAvailable}, tagged {@code Dependency} = {@code mongodb}: 1 when
 * MongoDB is available and 0 when it is not, from the driver's server heartbeats. Before the first
 * heartbeat it reads NaN, which is not published.
 */
@Component
@RequiredArgsConstructor
public class DependencyAvailabilityMetrics implements MeterBinder {

  static final String AVAILABLE_METRIC = "DependencyAvailable";
  static final String DEPENDENCY_TAG = "Dependency";

  private final MongoAvailability mongoAvailability;

  @Override
  public void bindTo(MeterRegistry meterRegistry) {
    Gauge.builder(AVAILABLE_METRIC, mongoAvailability, DependencyAvailabilityMetrics::gaugeValue)
        .tag(DEPENDENCY_TAG, ExternalCall.MONGODB)
        .register(meterRegistry);
  }

  private static double gaugeValue(MongoAvailability mongoAvailability) {
    return mongoAvailability
        .isAvailable()
        .map(available -> available ? 1.0 : 0.0)
        .orElse(Double.NaN);
  }
}
