package uk.gov.defra.trade.imports.addressbook.metrics;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandSucceededEvent;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Records every MongoDB command the service sends as an external call to {@code mongodb}, with
 * the command name as the operation. A command outside the known set is recorded as {@code
 * other}, so the operation stays one of a fixed few values.
 */
@Component
@RequiredArgsConstructor
public class MongoCommandMetrics implements CommandListener {

  static final String OTHER_OPERATION = "other";
  private static final Set<String> KNOWN_OPERATIONS =
      Set.of(
          "find",
          "getMore",
          "insert",
          "update",
          "delete",
          "findAndModify",
          "aggregate",
          "count",
          "distinct",
          "createIndexes",
          "listIndexes",
          "dropIndexes",
          "listCollections");

  private final ExternalCallMetrics externalCallMetrics;

  @Override
  public void commandSucceeded(CommandSucceededEvent event) {
    externalCallMetrics.recordSuccess(
        callFor(event.getCommandName()),
        Duration.ofNanos(event.getElapsedTime(TimeUnit.NANOSECONDS)));
  }

  @Override
  public void commandFailed(CommandFailedEvent event) {
    externalCallMetrics.recordFailure(
        callFor(event.getCommandName()),
        Duration.ofNanos(event.getElapsedTime(TimeUnit.NANOSECONDS)),
        event.getThrowable());
  }

  private static ExternalCall callFor(String commandName) {
    String operation = KNOWN_OPERATIONS.contains(commandName) ? commandName : OTHER_OPERATION;
    return new ExternalCall(ExternalCall.MONGODB, operation);
  }
}
