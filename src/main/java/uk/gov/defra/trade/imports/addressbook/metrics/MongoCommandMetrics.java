package uk.gov.defra.trade.imports.addressbook.metrics;

import com.mongodb.event.CommandEvent;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandSucceededEvent;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Records every MongoDB command as an external call to {@code mongodb}, its operation the command
 * name, or {@code other} for a command outside the fixed set. A command fails when it errors or
 * when its reply reports {@code writeErrors} or a {@code writeConcernError}. The call's log line
 * carries {@code mongodb.server_connection}, the server's name for the connection that ran the
 * command (as {@code conn<n>}, matching the {@code ctx} of the server's own log lines), when the
 * server has given the connection an id.
 */
@Component
@RequiredArgsConstructor
public class MongoCommandMetrics implements CommandListener {

  static final String DEPENDENCY = "mongodb";
  static final String MDC_SERVER_CONNECTION = "mongodb.server_connection";

  private final ExternalCallRecorder externalCallRecorder;

  @Override
  public void commandSucceeded(CommandSucceededEvent event) {
    String operation = operationOf(event.getCommandName());
    Duration elapsed = Duration.ofNanos(event.getElapsedTime(TimeUnit.NANOSECONDS));
    withServerConnectionInMdc(
        event,
        () ->
            MongoReplyWriteException.reportedBy(event.getResponse())
                .ifPresentOrElse(
                    writeFailure ->
                        externalCallRecorder.recordFailure(
                            DEPENDENCY, operation, elapsed, writeFailure),
                    () -> externalCallRecorder.recordSuccess(DEPENDENCY, operation, elapsed)));
  }

  @Override
  public void commandFailed(CommandFailedEvent event) {
    withServerConnectionInMdc(
        event,
        () ->
            externalCallRecorder.recordFailure(
                DEPENDENCY,
                operationOf(event.getCommandName()),
                Duration.ofNanos(event.getElapsedTime(TimeUnit.NANOSECONDS)),
                event.getThrowable()));
  }

  private static void withServerConnectionInMdc(CommandEvent event, Runnable record) {
    Long serverConnectionId =
        event.getConnectionDescription().getConnectionId().getServerValue();
    if (serverConnectionId == null) {
      record.run();
      return;
    }
    try (MDC.MDCCloseable ignored =
        MDC.putCloseable(MDC_SERVER_CONNECTION, "conn" + serverConnectionId)) {
      record.run();
    }
  }

  private static String operationOf(String commandName) {
    return switch (commandName) {
      case "find",
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
          "listCollections" ->
          commandName;
      default -> "other";
    };
  }
}
