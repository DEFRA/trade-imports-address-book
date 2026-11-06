package uk.gov.defra.trade.imports.addressbook.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs.loggedBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
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
import java.util.List;
import org.junit.jupiter.api.Test;

class MongoAvailabilityTest {

  private static final ServerAddress FIRST = new ServerAddress("mongo-1", 27017);
  private static final ServerAddress SECOND = new ServerAddress("mongo-2", 27017);

  private final MongoAvailability availability = new MongoAvailability();

  @Test
  void state_isUnknownBeforeAnyServerIsChecked() {
    assertThat(availability.state()).isEqualTo(MongoAvailability.State.UNKNOWN);
  }

  @Test
  void state_isUnknownWhileAServerIsNotYetChecked() {
    report(unchecked(FIRST));

    assertThat(availability.state()).isEqualTo(MongoAvailability.State.UNKNOWN);
  }

  @Test
  void state_isUnknownWhenTheDriverMonitorsNoServers() {
    report();

    assertThat(availability.state()).isEqualTo(MongoAvailability.State.UNKNOWN);
  }

  @Test
  void state_isAvailableWhileAConnectedServerAcceptsWrites() {
    report(failing(FIRST), primary(SECOND));

    assertThat(availability.state()).isEqualTo(MongoAvailability.State.AVAILABLE);
  }

  @Test
  void state_isUnknownWhileOneServerHasFailedAndAnotherIsNotYetChecked() {
    report(failing(FIRST), unchecked(SECOND));

    assertThat(availability.state()).isEqualTo(MongoAvailability.State.UNKNOWN);
  }

  @Test
  void state_isUnavailableOnceEveryServerHasFailedItsCheck() {
    report(failing(FIRST), failing(SECOND));

    assertThat(availability.state()).isEqualTo(MongoAvailability.State.UNAVAILABLE);
  }

  @Test
  void state_isUnavailableWhenTheConnectedServersAreSecondariesAndTheRestHaveFailed() {
    report(failing(FIRST), secondary(SECOND));

    assertThat(availability.state()).isEqualTo(MongoAvailability.State.UNAVAILABLE);
  }

  @Test
  void state_isUnknownWhileASecondaryIsConnectedAndAnotherServerIsNotYetChecked() {
    report(secondary(FIRST), unchecked(SECOND));

    assertThat(availability.state()).isEqualTo(MongoAvailability.State.UNKNOWN);
  }

  @Test
  void state_isUnavailableOnceThePrimaryIsRemoved() {
    report(failing(FIRST), primary(SECOND));

    report(failing(FIRST));

    assertThat(availability.state()).isEqualTo(MongoAvailability.State.UNAVAILABLE);
  }

  @Test
  void state_isAvailableAgainOnceAServerRecovers() {
    report(failing(FIRST));

    report(primary(FIRST));

    assertThat(availability.state()).isEqualTo(MongoAvailability.State.AVAILABLE);
  }

  @Test
  void clusterDescriptionChanged_logsOnceAtWarnWithTheFailure_whenTheDatabaseBecomesUnavailable() {
    report(primary(FIRST));

    List<ILoggingEvent> lines =
        loggedBy(
            MongoAvailability.class,
            () -> {
              report(failing(FIRST));
              report(failing(FIRST));
            });

    assertThat(lines).hasSize(1);
    assertThat(lines.getFirst().getLevel()).isEqualTo(Level.WARN);
    assertThat(lines.getFirst().getFormattedMessage()).isEqualTo("MongoDB is unavailable");
    assertThat(lines.getFirst().getThrowableProxy().getMessage()).isEqualTo("refused");
  }

  @Test
  void clusterDescriptionChanged_logsOnceAtWarnNamingNoWritableServer_whenThePrimaryIsLost() {
    report(primary(FIRST), secondary(SECOND));

    List<ILoggingEvent> lines =
        loggedBy(
            MongoAvailability.class,
            () -> {
              report(failing(FIRST), secondary(SECOND));
              report(failing(FIRST), secondary(SECOND));
            });

    assertThat(lines).hasSize(1);
    assertThat(lines.getFirst().getLevel()).isEqualTo(Level.WARN);
    assertThat(lines.getFirst().getFormattedMessage())
        .isEqualTo("MongoDB is unavailable: servers are connected but none accepts writes");
  }

  @Test
  void clusterDescriptionChanged_logsOnceAtInfo_whenTheDatabaseBecomesAvailable() {
    List<ILoggingEvent> lines =
        loggedBy(
            MongoAvailability.class,
            () -> {
              report(primary(FIRST));
              report(primary(FIRST), primary(SECOND));
            });

    assertThat(lines).hasSize(1);
    assertThat(lines.getFirst().getLevel()).isEqualTo(Level.INFO);
    assertThat(lines.getFirst().getFormattedMessage()).isEqualTo("MongoDB is available");
  }

  @Test
  void clusterDescriptionChanged_logsNothing_whileAnotherServerStillAcceptsWrites() {
    report(primary(FIRST), primary(SECOND));

    List<ILoggingEvent> lines =
        loggedBy(MongoAvailability.class, () -> report(failing(FIRST), primary(SECOND)));

    assertThat(lines).isEmpty();
  }

  private void report(ServerDescription... servers) {
    ClusterDescription description =
        new ClusterDescription(
            ClusterConnectionMode.MULTIPLE, ClusterType.REPLICA_SET, List.of(servers));
    ClusterDescription previous =
        new ClusterDescription(ClusterConnectionMode.MULTIPLE, ClusterType.UNKNOWN, List.of());
    availability.clusterDescriptionChanged(
        new ClusterDescriptionChangedEvent(new ClusterId(), description, previous));
  }

  private static ServerDescription primary(ServerAddress address) {
    return ServerDescription.builder()
        .address(address)
        .state(ServerConnectionState.CONNECTED)
        .ok(true)
        .type(ServerType.REPLICA_SET_PRIMARY)
        .build();
  }

  private static ServerDescription secondary(ServerAddress address) {
    return ServerDescription.builder()
        .address(address)
        .state(ServerConnectionState.CONNECTED)
        .ok(true)
        .type(ServerType.REPLICA_SET_SECONDARY)
        .build();
  }

  private static ServerDescription failing(ServerAddress address) {
    return ServerDescription.builder()
        .address(address)
        .state(ServerConnectionState.CONNECTING)
        .exception(new MongoSocketOpenException("refused", address))
        .build();
  }

  private static ServerDescription unchecked(ServerAddress address) {
    return ServerDescription.builder()
        .address(address)
        .state(ServerConnectionState.CONNECTING)
        .build();
  }
}
