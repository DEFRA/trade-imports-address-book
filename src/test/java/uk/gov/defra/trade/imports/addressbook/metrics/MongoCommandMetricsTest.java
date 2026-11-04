package uk.gov.defra.trade.imports.addressbook.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs.keyValues;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoFailures.CONNECTION_RESET;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoFailures.connectionReset;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs;

class MongoCommandMetricsTest {

  private static final ConnectionDescription CONNECTION =
      new ConnectionDescription(new ServerId(new ClusterId(), new ServerAddress()));

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final MongoCommandMetrics metrics =
      new MongoCommandMetrics(new ExternalCallMetrics(meterRegistry));

  @ParameterizedTest
  @ValueSource(strings = {"find", "insert", "update", "aggregate", "createIndexes"})
  void commandSucceeded_shouldRecordASuccessfulCallNamedByTheCommand(String commandName) {
    metrics.commandSucceeded(succeeded(commandName, Duration.ofMillis(12)));

    assertThat(durationOf(commandName).totalTime(TimeUnit.MILLISECONDS)).isEqualTo(12.0);
    assertThat(failuresOf(commandName).count()).isEqualTo(1);
    assertThat(failuresOf(commandName).totalAmount()).isZero();
  }

  @Test
  void commandSucceeded_shouldRecordAnUnknownCommandAsOther() {
    metrics.commandSucceeded(succeeded("endSessions", Duration.ofMillis(1)));

    assertThat(durationOf("other").count()).isEqualTo(1);
  }

  @Test
  void commandFailed_shouldRecordAFailedCallWithItsDuration_andLogItsCause() {
    var logs =
        CapturedLogs.loggedBy(
            ExternalCallMetrics.class,
            () -> metrics.commandFailed(failed("find", Duration.ofMillis(30))));

    assertThat(failuresOf("find").totalAmount()).isEqualTo(1.0);
    assertThat(durationOf("find").totalTime(TimeUnit.MILLISECONDS)).isEqualTo(30.0);
    assertThat(keyValues(logs.getFirst()))
        .containsEntry("event.action", "mongodb/find")
        .containsEntry("event.duration", 30_000_000L);
    assertThat(logs.getFirst().getThrowableProxy().getMessage()).isEqualTo(CONNECTION_RESET);
  }

  private Timer durationOf(String operation) {
    return meterRegistry
        .get("ExternalCallDuration")
        .tags("Dependency", "mongodb", "Operation", operation)
        .timer();
  }

  private DistributionSummary failuresOf(String operation) {
    return meterRegistry
        .get("ExternalCallFailure")
        .tags("Dependency", "mongodb", "Operation", operation)
        .summary();
  }

  private static CommandSucceededEvent succeeded(String commandName, Duration elapsed) {
    return new CommandSucceededEvent(
        null,
        1,
        1,
        CONNECTION,
        "trade-imports-address-book",
        commandName,
        new BsonDocument(),
        elapsed.toNanos());
  }

  private static CommandFailedEvent failed(String commandName, Duration elapsed) {
    return new CommandFailedEvent(
        null,
        1,
        1,
        CONNECTION,
        "trade-imports-address-book",
        commandName,
        elapsed.toNanos(),
        connectionReset());
  }
}
