package uk.gov.defra.trade.imports.addressbook.metrics;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ConnectionId;
import com.mongodb.event.ServerHeartbeatFailedEvent;
import com.mongodb.event.ServerHeartbeatSucceededEvent;
import com.mongodb.event.ServerMonitorListener;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tracks whether MongoDB is up from the driver's own server heartbeats. MongoDB is available while
 * the latest heartbeat of at least one server succeeded. A change between available and
 * unavailable is logged once, at warn with the failure when it goes down.
 */
@Slf4j
@Component
public class MongoAvailability implements ServerMonitorListener {

  private static final Optional<Boolean> AVAILABLE = Optional.of(true);
  private static final Optional<Boolean> UNAVAILABLE = Optional.of(false);

  private final Map<ServerAddress, Boolean> lastHeartbeatSucceededByServer =
      new ConcurrentHashMap<>();

  @Override
  public synchronized void serverHeartbeatSucceeded(ServerHeartbeatSucceededEvent event) {
    Optional<Boolean> before = isAvailable();
    lastHeartbeatSucceededByServer.put(serverOf(event.getConnectionId()), true);
    if (!before.equals(AVAILABLE)) {
      log.info("MongoDB is available");
    }
  }

  @Override
  public synchronized void serverHeartbeatFailed(ServerHeartbeatFailedEvent event) {
    Optional<Boolean> before = isAvailable();
    ServerAddress server = serverOf(event.getConnectionId());
    lastHeartbeatSucceededByServer.put(server, false);
    if (!before.equals(UNAVAILABLE) && isAvailable().equals(UNAVAILABLE)) {
      log.warn("MongoDB is unavailable: heartbeat to {} failed", server, event.getThrowable());
    }
  }

  /**
   * Whether MongoDB is available now.
   *
   * @return {@code true} while at least one server's latest heartbeat succeeded, {@code false}
   *     when every server's latest heartbeat failed, and empty before the first heartbeat
   */
  public Optional<Boolean> isAvailable() {
    if (lastHeartbeatSucceededByServer.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(lastHeartbeatSucceededByServer.containsValue(true));
  }

  private static ServerAddress serverOf(ConnectionId connectionId) {
    return connectionId.getServerId().getAddress();
  }
}
