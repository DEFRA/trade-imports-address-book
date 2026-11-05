package uk.gov.defra.trade.imports.addressbook.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoClusters.down;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoClusters.monitoring;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoClusters.up;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DependencyAvailabilityMetricsTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final MongoAvailability mongoAvailability = new MongoAvailability();

  @BeforeEach
  void bindTheGauge() {
    new DependencyAvailabilityMetrics(mongoAvailability).bindTo(meterRegistry);
  }

  @Test
  void gauge_shouldReadNotANumber_beforeTheClusterIsDescribed() {
    assertThat(mongoGauge()).isNaN();
  }

  @Test
  void gauge_shouldReadOne_whenMongoIsAvailable() {
    mongoAvailability.clusterDescriptionChanged(monitoring(up("mongo-1")));

    assertThat(mongoGauge()).isEqualTo(1.0);
  }

  @Test
  void gauge_shouldReadZero_whenMongoIsUnavailable() {
    mongoAvailability.clusterDescriptionChanged(monitoring(down("mongo-1")));

    assertThat(mongoGauge()).isZero();
  }

  private double mongoGauge() {
    return meterRegistry
        .get("external.dependency.available")
        .tag("dependency", "mongodb")
        .gauge()
        .value();
  }
}
