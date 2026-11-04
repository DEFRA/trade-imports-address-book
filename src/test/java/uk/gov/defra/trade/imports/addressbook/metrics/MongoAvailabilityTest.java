package uk.gov.defra.trade.imports.addressbook.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoClusters.down;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoClusters.monitoring;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoClusters.notYetChecked;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoClusters.up;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoFailures.CONNECTION_RESET;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs;

class MongoAvailabilityTest {

  private final MongoAvailability availability = new MongoAvailability();

  @Test
  void isAvailable_shouldBeEmpty_beforeTheClusterIsDescribed() {
    assertThat(availability.isAvailable()).isEmpty();
  }

  @Test
  void isAvailable_shouldBeTrue_whenAMonitoredServerIsUp() {
    availability.clusterDescriptionChanged(monitoring(up("mongo-1")));

    assertThat(availability.isAvailable()).contains(true);
  }

  @Test
  void isAvailable_shouldStayTrue_whileAnyMonitoredServerIsUp() {
    availability.clusterDescriptionChanged(monitoring(up("mongo-1"), up("mongo-2")));

    availability.clusterDescriptionChanged(monitoring(down("mongo-1"), up("mongo-2")));

    assertThat(availability.isAvailable()).contains(true);
  }

  @Test
  void isAvailable_shouldBeFalse_whenEveryMonitoredServerIsDown() {
    availability.clusterDescriptionChanged(monitoring(up("mongo-1"), down("mongo-2")));

    availability.clusterDescriptionChanged(monitoring(down("mongo-1"), down("mongo-2")));

    assertThat(availability.isAvailable()).contains(false);
  }

  @Test
  void isAvailable_shouldBeFalse_whenAnUpServerIsRemovedAndTheRemainingOneIsDown() {
    availability.clusterDescriptionChanged(monitoring(up("mongo-1"), up("mongo-2")));

    availability.clusterDescriptionChanged(monitoring(down("mongo-2")));

    assertThat(availability.isAvailable()).contains(false);
  }

  @Test
  void isAvailable_shouldBeEmpty_whileAServerNotYetCheckedMayStillBeUp() {
    availability.clusterDescriptionChanged(monitoring(down("mongo-1"), notYetChecked("mongo-2")));

    assertThat(availability.isAvailable()).isEmpty();
  }

  @Test
  void isAvailable_shouldBeTrueAgain_whenAServerComesBackUpAfterAnOutage() {
    availability.clusterDescriptionChanged(monitoring(down("mongo-1")));

    availability.clusterDescriptionChanged(monitoring(up("mongo-1")));

    assertThat(availability.isAvailable()).contains(true);
  }

  @Test
  void clusterChanges_shouldLogOnlyEachChange_withTheFailureWhenMongoGoesDown() {
    var logs =
        CapturedLogs.loggedBy(
            MongoAvailability.class,
            () -> {
              availability.clusterDescriptionChanged(monitoring(up("mongo-1")));
              availability.clusterDescriptionChanged(monitoring(up("mongo-1")));
              availability.clusterDescriptionChanged(monitoring(down("mongo-1")));
              availability.clusterDescriptionChanged(monitoring(down("mongo-1")));
              availability.clusterDescriptionChanged(monitoring(up("mongo-1")));
            });

    assertThat(logs)
        .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
        .containsExactly(
            tuple(Level.INFO, "MongoDB is available"),
            tuple(Level.WARN, "MongoDB is unavailable: heartbeats to mongo-1:27017 failed"),
            tuple(Level.INFO, "MongoDB is available"));
    assertThat(logs.get(1).getThrowableProxy().getMessage()).isEqualTo(CONNECTION_RESET);
  }

  @Test
  void clusterChanges_shouldLogNoOutage_whenOneServerGoesDownWhileAnotherIsUp() {
    var logs =
        CapturedLogs.loggedBy(
            MongoAvailability.class,
            () -> {
              availability.clusterDescriptionChanged(monitoring(up("mongo-1"), up("mongo-2")));
              availability.clusterDescriptionChanged(monitoring(down("mongo-1"), up("mongo-2")));
            });

    assertThat(logs)
        .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
        .containsExactly(tuple(Level.INFO, "MongoDB is available"));
  }
}
