package uk.gov.defra.trade.imports.addressbook.metrics;

import com.mongodb.connection.ServerConnectionState;
import com.mongodb.connection.ServerDescription;
import com.mongodb.event.ClusterDescriptionChangedEvent;
import com.mongodb.event.ClusterListener;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Whether MongoDB is available, from the servers the driver monitors: available while any is
 * connected, unavailable once every one has failed its check, and unknown while there are none or
 * one has not been checked yet. Logs each change to available or unavailable.
 */
@Slf4j
@Component
public class MongoAvailability implements ClusterListener {

  /** What the driver's checks say of the database. */
  public enum State {
    AVAILABLE,
    UNAVAILABLE,
    UNKNOWN
  }

  private volatile State state = State.UNKNOWN;

  @Override
  public synchronized void clusterDescriptionChanged(ClusterDescriptionChangedEvent event) {
    List<ServerDescription> servers = event.getNewDescription().getServerDescriptions();
    State reported = stateOf(servers);
    if (reported == state) {
      return;
    }
    state = reported;
    if (reported == State.AVAILABLE) {
      log.info("MongoDB is available");
    } else if (reported == State.UNAVAILABLE) {
      log.warn("MongoDB is unavailable", firstFailure(servers));
    }
  }

  private static State stateOf(List<ServerDescription> servers) {
    if (servers.stream().anyMatch(MongoAvailability::isConnected)) {
      return State.AVAILABLE;
    }
    if (!servers.isEmpty() && servers.stream().allMatch(server -> server.getException() != null)) {
      return State.UNAVAILABLE;
    }
    return State.UNKNOWN;
  }

  private static boolean isConnected(ServerDescription server) {
    return server.getState() == ServerConnectionState.CONNECTED;
  }

  private static Throwable firstFailure(List<ServerDescription> servers) {
    return servers.stream()
        .map(ServerDescription::getException)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /**
   * The database's state as of the driver's latest checks.
   *
   * @return the state
   */
  public State state() {
    return state;
  }
}
