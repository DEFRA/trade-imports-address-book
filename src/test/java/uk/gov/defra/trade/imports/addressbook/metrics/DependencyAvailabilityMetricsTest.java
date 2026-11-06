package uk.gov.defra.trade.imports.addressbook.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoClusterEvents.failing;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoClusterEvents.primary;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoClusterEvents.report;

import com.mongodb.ServerAddress;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DependencyAvailabilityMetricsTest {

  private static final ServerAddress ADDRESS = new ServerAddress("mongo-1", 27017);

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final MongoAvailability availability = new MongoAvailability();

  @BeforeEach
  void registerTheGauge() {
    new DependencyAvailabilityMetrics(meterRegistry, availability);
  }

  @Test
  void gauge_isNaNWhileAvailabilityIsUnknown() {
    assertThat(gauge().value()).isNaN();
  }

  @Test
  void gauge_isOneWhileMongoIsAvailable() {
    report(availability, primary(ADDRESS));

    assertThat(gauge().value()).isEqualTo(1);
  }

  @Test
  void gauge_isZeroWhileMongoIsUnavailable() {
    report(availability, failing(ADDRESS));

    assertThat(gauge().value()).isZero();
  }

  private Gauge gauge() {
    return meterRegistry.get("external.dependency.available").tag("dependency", "mongodb").gauge();
  }
}
