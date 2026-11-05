package uk.gov.defra.trade.imports.addressbook.metrics;

import com.mongodb.connection.ServerDescription;
import com.mongodb.event.ClusterDescriptionChangedEvent;
import com.mongodb.event.ClusterListener;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MongoAvailability implements ClusterListener {

  private enum Availability {
    UNKNOWN,
    AVAILABLE,
    UNAVAILABLE
  }

  private final AtomicReference<List<ServerDescription>> monitoredServers =
      new AtomicReference<>(List.of());
  private Availability lastLogged = Availability.UNKNOWN;

  @Override
  public synchronized void clusterDescriptionChanged(ClusterDescriptionChangedEvent event) {
    List<ServerDescription> servers = event.getNewDescription().getServerDescriptions();
    monitoredServers.set(servers);
    Availability now = availabilityOf(servers);
    if (now == Availability.UNKNOWN || now == lastLogged) {
      return;
    }
    lastLogged = now;
    if (now == Availability.AVAILABLE) {
      log.info("MongoDB is available");
    } else {
      log.warn(
          "MongoDB is unavailable: heartbeats to {} failed",
          addressesOf(servers),
          firstFailureOf(servers));
    }
  }

  public Optional<Boolean> isAvailable() {
    return switch (availabilityOf(monitoredServers.get())) {
      case AVAILABLE -> Optional.of(true);
      case UNAVAILABLE -> Optional.of(false);
      case UNKNOWN -> Optional.empty();
    };
  }

  private static Availability availabilityOf(List<ServerDescription> servers) {
    if (servers.stream().anyMatch(ServerDescription::isOk)) {
      return Availability.AVAILABLE;
    }
    if (!servers.isEmpty() && servers.stream().allMatch(server -> server.getException() != null)) {
      return Availability.UNAVAILABLE;
    }
    return Availability.UNKNOWN;
  }

  private static String addressesOf(List<ServerDescription> servers) {
    return servers.stream()
        .map(server -> server.getAddress().toString())
        .collect(Collectors.joining(", "));
  }

  private static Throwable firstFailureOf(List<ServerDescription> servers) {
    return servers.stream()
        .map(ServerDescription::getException)
        .filter(Objects::nonNull)
        .findFirst()
        .orElseThrow();
  }
}
