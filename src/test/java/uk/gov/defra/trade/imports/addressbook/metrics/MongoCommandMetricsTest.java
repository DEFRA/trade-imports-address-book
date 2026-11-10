package uk.gov.defra.trade.imports.addressbook.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs.keyValues;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs.loggedBy;
import static uk.gov.defra.trade.imports.addressbook.testsupport.ExternalCallMeters.durationCount;
import static uk.gov.defra.trade.imports.addressbook.testsupport.ExternalCallMeters.durationMillis;
import static uk.gov.defra.trade.imports.addressbook.testsupport.ExternalCallMeters.failureTotal;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.mongodb.MongoSocketReadException;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ConnectionId;
import com.mongodb.connection.ServerId;
import com.mongodb.connection.ServerType;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.BsonDocument;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MongoCommandMetricsTest {

  private static final ConnectionDescription CONNECTION =
      new ConnectionDescription(new ServerId(new ClusterId(), new ServerAddress()));
  private static final ConnectionDescription SERVER_CONNECTION =
      new ConnectionDescription(
          new ConnectionId(new ServerId(new ClusterId(), new ServerAddress()), 1, 4512L),
          21,
          ServerType.STANDALONE,
          1000,
          16_777_216,
          48_000_000,
          List.of());

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final MongoCommandMetrics listener =
      new MongoCommandMetrics(new ExternalCallRecorder(meterRegistry));

  @ParameterizedTest
  @ValueSource(strings = {"find", "findAndModify", "createIndexes"})
  void commandSucceeded_recordsAKnownCommandUnderItsOwnName(String commandName) {
    listener.commandSucceeded(succeeded(commandName, 5));

    assertThat(durationCount(meterRegistry, "mongodb", commandName)).isEqualTo(1);
    assertThat(failureTotal(meterRegistry, "mongodb", commandName)).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"hello", "killCursors"})
  void commandSucceeded_recordsAnUnknownCommandAsOther(String commandName) {
    listener.commandSucceeded(succeeded(commandName, 5));

    assertThat(durationCount(meterRegistry, "mongodb", "other")).isEqualTo(1);
    assertThat(failureTotal(meterRegistry, "mongodb", "other")).isZero();
  }

  @Test
  void commandSucceeded_recordsTheElapsedTime() {
    listener.commandSucceeded(succeeded("find", 12));

    assertThat(durationMillis(meterRegistry, "mongodb", "find")).isEqualTo(12);
  }

  @Test
  void commandSucceeded_logsTheServerConnectionAndLeavesItOutOfTheMdcAfterwards() {
    List<ILoggingEvent> lines =
        loggedBy(
            ExternalCallRecorder.class,
            () -> listener.commandSucceeded(succeededOn(SERVER_CONNECTION, "find")));

    assertThat(lines.getFirst().getMDCPropertyMap())
        .containsEntry("mongodb.server_connection", "conn4512");
    assertThat(MDC.get("mongodb.server_connection")).isNull();
  }

  @Test
  void commandFailed_logsTheServerConnection() {
    List<ILoggingEvent> lines =
        loggedBy(
            ExternalCallRecorder.class,
            () ->
                listener.commandFailed(
                    new CommandFailedEvent(
                        null,
                        1,
                        1,
                        SERVER_CONNECTION,
                        "trade-imports-address-book",
                        "find",
                        TimeUnit.MILLISECONDS.toNanos(3),
                        new MongoSocketReadException("closed", new ServerAddress()))));

    assertThat(lines.getFirst().getMDCPropertyMap())
        .containsEntry("mongodb.server_connection", "conn4512");
  }

  @Test
  void commandSucceeded_onAConnectionTheServerHasNotNamedLogsNoServerConnection() {
    List<ILoggingEvent> lines =
        loggedBy(
            ExternalCallRecorder.class,
            () -> listener.commandSucceeded(succeeded("find", 5)));

    assertThat(lines.getFirst().getMDCPropertyMap())
        .doesNotContainKey("mongodb.server_connection");
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

    assertThat(durationMillis(meterRegistry, "mongodb", "insert")).isEqualTo(3);
    assertThat(failureTotal(meterRegistry, "mongodb", "insert")).isEqualTo(1);
    assertThat(lines).hasSize(1);
    assertThat(keyValues(lines.getFirst()))
        .containsEntry("event.action", "mongodb/insert")
        .containsEntry("error.type", "com.mongodb.MongoSocketReadException")
        .containsEntry("error.message", "closed");
  }

  @Test
  void commandSucceeded_recordsAReplyWithWriteErrorsAsAFailureNamedByTheirCodes() {
    BsonDocument reply =
        BsonDocument.parse(
            """
            {ok: 1, n: 0, writeErrors: [{index: 0, code: 11000,
              errmsg: "E11000 duplicate key error dup key: { postcode: \\"SW1A 1AA\\" }"}]}
            """);

    List<ILoggingEvent> lines =
        loggedBy(
            ExternalCallRecorder.class,
            () -> listener.commandSucceeded(succeeded("insert", 4, reply)));

    assertThat(durationCount(meterRegistry, "mongodb", "insert")).isEqualTo(1);
    assertThat(failureTotal(meterRegistry, "mongodb", "insert")).isEqualTo(1);
    assertThat(lines).hasSize(1);
    assertThat(keyValues(lines.getFirst()))
        .containsEntry("event.outcome", "failure")
        .containsEntry("error.type", MongoReplyWriteException.class.getName())
        .containsEntry("error.message", "writeErrors codes [11000]");
  }

  @Test
  void commandSucceeded_recordsAReplyWithAWriteConcernErrorAsAFailureNamedByItsCode() {
    BsonDocument reply =
        BsonDocument.parse(
            """
            {ok: 1, n: 1, writeConcernError: {code: 64, codeName: "WriteConcernFailed",
              errmsg: "waiting for replication timed out"}}
            """);

    List<ILoggingEvent> lines =
        loggedBy(
            ExternalCallRecorder.class,
            () -> listener.commandSucceeded(succeeded("update", 4, reply)));

    assertThat(durationCount(meterRegistry, "mongodb", "update")).isEqualTo(1);
    assertThat(failureTotal(meterRegistry, "mongodb", "update")).isEqualTo(1);
    assertThat(lines).hasSize(1);
    assertThat(keyValues(lines.getFirst()))
        .containsEntry("event.outcome", "failure")
        .containsEntry("error.type", MongoReplyWriteException.class.getName())
        .containsEntry("error.message", "writeConcernError code 64 WriteConcernFailed");
  }

  private static CommandSucceededEvent succeeded(String commandName, long elapsedMillis) {
    return succeeded(commandName, elapsedMillis, BsonDocument.parse("{ok: 1}"));
  }

  private static CommandSucceededEvent succeededOn(
      ConnectionDescription connection, String commandName) {
    return new CommandSucceededEvent(
        null,
        1,
        1,
        connection,
        "trade-imports-address-book",
        commandName,
        BsonDocument.parse("{ok: 1}"),
        TimeUnit.MILLISECONDS.toNanos(5));
  }

  private static CommandSucceededEvent succeeded(
      String commandName, long elapsedMillis, BsonDocument reply) {
    return new CommandSucceededEvent(
        null,
        1,
        1,
        CONNECTION,
        "trade-imports-address-book",
        commandName,
        reply,
        TimeUnit.MILLISECONDS.toNanos(elapsedMillis));
  }
}
