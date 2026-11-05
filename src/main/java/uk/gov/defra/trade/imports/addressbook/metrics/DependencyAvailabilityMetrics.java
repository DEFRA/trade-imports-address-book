package uk.gov.defra.trade.imports.addressbook.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Binds the gauge {@code external.dependency.available}, tagged {@code dependency} = {@code
 * mongodb}: 1 when {@link MongoAvailability} reports MongoDB available, 0 when it reports it
 * unavailable, and NaN while it does not know.
 */
@Component
@RequiredArgsConstructor
public class DependencyAvailabilityMetrics implements MeterBinder {

  static final String AVAILABLE_METRIC = "external.dependency.available";

  private final MongoAvailability mongoAvailability;

  @Override
  public void bindTo(MeterRegistry meterRegistry) {
    Gauge.builder(AVAILABLE_METRIC, mongoAvailability, DependencyAvailabilityMetrics::gaugeValue)
        .tag(ExternalCallMetrics.DEPENDENCY_TAG, ExternalCall.MONGODB)
        .register(meterRegistry);
  }

  private static double gaugeValue(MongoAvailability mongoAvailability) {
    return mongoAvailability
        .isAvailable()
        .map(available -> available ? 1.0 : 0.0)
        .orElse(Double.NaN);
  }
}
