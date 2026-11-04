package uk.gov.defra.trade.imports.addressbook.testsupport;

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

public final class MongoClusters {

  private static final ClusterId CLUSTER = new ClusterId();
  private static final ClusterDescription NO_SERVERS =
      new ClusterDescription(ClusterConnectionMode.MULTIPLE, ClusterType.UNKNOWN, List.of());

  private MongoClusters() {}

  public static ClusterDescriptionChangedEvent monitoring(ServerDescription... servers) {
    ClusterDescription cluster =
        new ClusterDescription(
            ClusterConnectionMode.MULTIPLE, ClusterType.REPLICA_SET, List.of(servers));
    return new ClusterDescriptionChangedEvent(CLUSTER, cluster, NO_SERVERS);
  }

  public static ServerDescription up(String host) {
    return ServerDescription.builder()
        .address(new ServerAddress(host))
        .state(ServerConnectionState.CONNECTED)
        .type(ServerType.REPLICA_SET_PRIMARY)
        .ok(true)
        .build();
  }

  public static ServerDescription down(String host) {
    return ServerDescription.builder()
        .address(new ServerAddress(host))
        .state(ServerConnectionState.CONNECTING)
        .type(ServerType.UNKNOWN)
        .exception(MongoFailures.connectionReset())
        .build();
  }

  public static ServerDescription notYetChecked(String host) {
    return ServerDescription.builder()
        .address(new ServerAddress(host))
        .state(ServerConnectionState.CONNECTING)
        .type(ServerType.UNKNOWN)
        .build();
  }
}
