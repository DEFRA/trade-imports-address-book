package uk.gov.defra.trade.imports.addressbook.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.MongoSocketOpenException;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterConnectionMode;
import com.mongodb.connection.ClusterDescription;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ClusterType;
import com.mongodb.connection.ServerConnectionState;
import com.mongodb.connection.ServerDescription;
import com.mongodb.connection.ServerType;
import com.mongodb.event.ClusterDescriptionChangedEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
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
    report(
        ServerDescription.builder()
            .address(ADDRESS)
            .state(ServerConnectionState.CONNECTED)
            .ok(true)
            .type(ServerType.STANDALONE)
            .build());

    assertThat(gauge().value()).isEqualTo(1);
  }

  @Test
  void gauge_isZeroWhileMongoIsUnavailable() {
    report(
        ServerDescription.builder()
            .address(ADDRESS)
            .state(ServerConnectionState.CONNECTING)
            .exception(new MongoSocketOpenException("refused", ADDRESS))
            .build());

    assertThat(gauge().value()).isZero();
  }

  private Gauge gauge() {
    return meterRegistry.get("external.dependency.available").tag("dependency", "mongodb").gauge();
  }

  private void report(ServerDescription server) {
    availability.clusterDescriptionChanged(
        new ClusterDescriptionChangedEvent(
            new ClusterId(),
            new ClusterDescription(
                ClusterConnectionMode.SINGLE, ClusterType.STANDALONE, List.of(server)),
            new ClusterDescription(
                ClusterConnectionMode.SINGLE, ClusterType.UNKNOWN, List.of())));
  }
}
