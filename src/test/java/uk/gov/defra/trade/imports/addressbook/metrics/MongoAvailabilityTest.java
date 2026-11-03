package uk.gov.defra.trade.imports.addressbook.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoFailures.CONNECTION_RESET;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoHeartbeats.failedFrom;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoHeartbeats.succeededFrom;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs;

class MongoAvailabilityTest {

  private final MongoAvailability availability = new MongoAvailability();

  @Test
  void isAvailable_shouldBeEmpty_beforeAnyHeartbeat() {
    assertThat(availability.isAvailable()).isEmpty();
  }

  @Test
  void isAvailable_shouldBeTrue_afterAHeartbeatSucceeds() {
    // When
    availability.serverHeartbeatSucceeded(succeededFrom("mongo-1"));

    // Then
    assertThat(availability.isAvailable()).contains(true);
  }

  @Test
  void isAvailable_shouldStayTrue_whileAnyServersLatestHeartbeatSucceeded() {
    // Given
    availability.serverHeartbeatSucceeded(succeededFrom("mongo-1"));
    availability.serverHeartbeatSucceeded(succeededFrom("mongo-2"));

    // When
    availability.serverHeartbeatFailed(failedFrom("mongo-1"));

    // Then
    assertThat(availability.isAvailable()).contains(true);
  }

  @Test
  void isAvailable_shouldBeFalse_whenEveryServersLatestHeartbeatFailed() {
    // Given
    availability.serverHeartbeatSucceeded(succeededFrom("mongo-1"));
    availability.serverHeartbeatFailed(failedFrom("mongo-2"));

    // When
    availability.serverHeartbeatFailed(failedFrom("mongo-1"));

    // Then
    assertThat(availability.isAvailable()).contains(false);
  }

  @Test
  void isAvailable_shouldBeTrueAgain_whenAHeartbeatSucceedsAfterAnOutage() {
    // Given
    availability.serverHeartbeatFailed(failedFrom("mongo-1"));

    // When
    availability.serverHeartbeatSucceeded(succeededFrom("mongo-1"));

    // Then
    assertThat(availability.isAvailable()).contains(true);
  }

  @Test
  void heartbeats_shouldLogOnlyEachChange_withTheFailureWhenMongoGoesDown() {
    // When
    var logs =
        CapturedLogs.loggedBy(
            MongoAvailability.class,
            () -> {
              availability.serverHeartbeatSucceeded(succeededFrom("mongo-1"));
              availability.serverHeartbeatSucceeded(succeededFrom("mongo-1"));
              availability.serverHeartbeatFailed(failedFrom("mongo-1"));
              availability.serverHeartbeatFailed(failedFrom("mongo-1"));
              availability.serverHeartbeatSucceeded(succeededFrom("mongo-1"));
            });

    // Then
    assertThat(logs)
        .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
        .containsExactly(
            tuple(Level.INFO, "MongoDB is available"),
            tuple(
                Level.WARN, "MongoDB is unavailable: heartbeat to mongo-1:27017 failed"),
            tuple(Level.INFO, "MongoDB is available"));
    assertThat(logs.get(1).getThrowableProxy().getMessage()).isEqualTo(CONNECTION_RESET);
  }
}
