package uk.gov.defra.trade.imports.addressbook.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs.keyValues;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs.loggedBy;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.mongodb.MongoSocketReadException;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.BsonDocument;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class MongoCommandMetricsTest {

  private static final ConnectionDescription CONNECTION =
      new ConnectionDescription(new ServerId(new ClusterId(), new ServerAddress()));

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final MongoCommandMetrics listener =
      new MongoCommandMetrics(new ExternalCallRecorder(meterRegistry));

  @ParameterizedTest
  @ValueSource(strings = {"find", "findAndModify", "createIndexes"})
  void commandSucceeded_recordsAKnownCommandUnderItsOwnName(String commandName) {
    listener.commandSucceeded(succeeded(commandName, 5));

    assertThat(durationCount(commandName)).isEqualTo(1);
    assertThat(failureTotal(commandName)).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"hello", "killCursors"})
  void commandSucceeded_recordsAnUnknownCommandAsOther(String commandName) {
    listener.commandSucceeded(succeeded(commandName, 5));

    assertThat(durationCount("other")).isEqualTo(1);
    assertThat(failureTotal("other")).isZero();
  }

  @Test
  void commandSucceeded_recordsTheElapsedTime() {
    listener.commandSucceeded(succeeded("find", 12));

    assertThat(
            meterRegistry
                .get("external.call.duration")
                .tags("dependency", "mongodb", "operation", "find")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
        .isEqualTo(12);
  }

  @Test
  void commandFailed_recordsTheElapsedTimeAndAFailureAndLogsTheCause() {
    List<ILoggingEvent> lines =
        loggedBy(
            ExternalCallRecorder.class,
            () ->
                listener.commandFailed(
                    new CommandFailedEvent(
                        null,
                        1,
                        1,
                        CONNECTION,
                        "trade-imports-address-book",
                        "insert",
                        TimeUnit.MILLISECONDS.toNanos(3),
                        new MongoSocketReadException("closed", new ServerAddress()))));

    assertThat(
            meterRegistry
                .get("external.call.duration")
                .tags("dependency", "mongodb", "operation", "insert")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
        .isEqualTo(3);
    assertThat(failureTotal("insert")).isEqualTo(1);
    assertThat(lines).hasSize(1);
    assertThat(keyValues(lines.getFirst()))
        .containsEntry("event.action", "mongodb/insert")
        .containsEntry("error.type", "com.mongodb.MongoSocketReadException")
        .containsEntry("error.message", "closed");
  }

  private static CommandSucceededEvent succeeded(String commandName, long elapsedMillis) {
    return new CommandSucceededEvent(
        null,
        1,
        1,
        CONNECTION,
        "trade-imports-address-book",
        commandName,
        new BsonDocument(),
        TimeUnit.MILLISECONDS.toNanos(elapsedMillis));
  }

  private long durationCount(String operation) {
    return meterRegistry
        .get("external.call.duration")
        .tags("dependency", "mongodb", "operation", operation)
        .timer()
        .count();
  }

  private double failureTotal(String operation) {
    return meterRegistry
        .get("external.call.failure")
        .tags("dependency", "mongodb", "operation", operation)
        .summary()
        .totalAmount();
  }
}
