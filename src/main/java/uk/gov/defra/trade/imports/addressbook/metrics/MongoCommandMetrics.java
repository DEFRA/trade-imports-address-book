package uk.gov.defra.trade.imports.addressbook.metrics;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandSucceededEvent;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Records every MongoDB command as an external call to {@code mongodb}, its operation the command
 * name, or {@code other} for a command outside the fixed set. A command fails when it errors or
 * when its reply reports {@code writeErrors} or a {@code writeConcernError}.
 */
@Component
@RequiredArgsConstructor
public class MongoCommandMetrics implements CommandListener {

  static final String DEPENDENCY = "mongodb";

  private final ExternalCallRecorder externalCallRecorder;

  @Override
  public void commandSucceeded(CommandSucceededEvent event) {
    String operation = operationOf(event.getCommandName());
    Duration elapsed = Duration.ofNanos(event.getElapsedTime(TimeUnit.NANOSECONDS));
    MongoReplyWriteException.reportedBy(event.getResponse())
        .ifPresentOrElse(
            writeFailure ->
                externalCallRecorder.recordFailure(DEPENDENCY, operation, elapsed, writeFailure),
            () -> externalCallRecorder.recordSuccess(DEPENDENCY, operation, elapsed));
  }

  @Override
  public void commandFailed(CommandFailedEvent event) {
    externalCallRecorder.recordFailure(
        DEPENDENCY,
        operationOf(event.getCommandName()),
        Duration.ofNanos(event.getElapsedTime(TimeUnit.NANOSECONDS)),
        event.getThrowable());
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
