package uk.gov.defra.trade.imports.addressbook.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Registers the gauge {@code external.dependency.available}, tagged {@code dependency}: 1 while the
 * dependency is available, 0 while it is not, and NaN while unknown.
 */
@Component
public class DependencyAvailabilityMetrics {

  DependencyAvailabilityMetrics(MeterRegistry meterRegistry, MongoAvailability mongoAvailability) {
    Gauge.builder(
            "external.dependency.available",
            mongoAvailability,
            availability -> gaugeValueOf(availability.state()))
        .tag(ExternalCallRecorder.DEPENDENCY_TAG, MongoCommandMetrics.DEPENDENCY)
        .register(meterRegistry);
  }

  private static double gaugeValueOf(MongoAvailability.State state) {
    return switch (state) {
      case AVAILABLE -> 1;
      case UNAVAILABLE -> 0;
      case UNKNOWN -> Double.NaN;
    };
  }
}
