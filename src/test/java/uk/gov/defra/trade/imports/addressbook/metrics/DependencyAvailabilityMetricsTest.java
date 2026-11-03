package uk.gov.defra.trade.imports.addressbook.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoHeartbeats.failedFrom;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoHeartbeats.succeededFrom;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class DependencyAvailabilityMetricsTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final MongoAvailability mongoAvailability = new MongoAvailability();

  {
    new DependencyAvailabilityMetrics(mongoAvailability).bindTo(meterRegistry);
  }

  @Test
  void gauge_shouldReadNotANumber_beforeTheFirstHeartbeat() {
    assertThat(mongoGauge()).isNaN();
  }

  @Test
  void gauge_shouldReadOne_whenMongosHeartbeatSucceeds() {
    // When
    mongoAvailability.serverHeartbeatSucceeded(succeededFrom("mongo-1"));

    // Then
    assertThat(mongoGauge()).isEqualTo(1.0);
  }

  @Test
  void gauge_shouldReadZero_whenMongosHeartbeatFails() {
    // When
    mongoAvailability.serverHeartbeatFailed(failedFrom("mongo-1"));

    // Then
    assertThat(mongoGauge()).isZero();
  }

  private double mongoGauge() {
    return meterRegistry.get("DependencyAvailable").tag("Dependency", "mongodb").gauge().value();
  }
}
