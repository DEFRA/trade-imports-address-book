package uk.gov.defra.trade.imports.addressbook.testsupport;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionId;
import com.mongodb.connection.ServerId;
import com.mongodb.event.ServerHeartbeatFailedEvent;
import com.mongodb.event.ServerHeartbeatSucceededEvent;
import org.bson.BsonDocument;

public final class MongoHeartbeats {

  private static final ClusterId CLUSTER = new ClusterId();

  private MongoHeartbeats() {}

  public static ServerHeartbeatSucceededEvent succeededFrom(String host) {
    return new ServerHeartbeatSucceededEvent(connectionTo(host), new BsonDocument(), 1_000, false);
  }

  public static ServerHeartbeatFailedEvent failedFrom(String host) {
    return new ServerHeartbeatFailedEvent(
        connectionTo(host), 1_000, false, MongoFailures.connectionReset());
  }

  private static ConnectionId connectionTo(String host) {
    return new ConnectionId(new ServerId(CLUSTER, new ServerAddress(host)));
  }
}
