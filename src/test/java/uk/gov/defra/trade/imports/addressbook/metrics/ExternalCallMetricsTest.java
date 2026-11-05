package uk.gov.defra.trade.imports.addressbook.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs.keyValues;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoFailures.CONNECTION_RESET;
import static uk.gov.defra.trade.imports.addressbook.testsupport.MongoFailures.connectionReset;

import com.mongodb.MongoSocketReadException;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs;

class ExternalCallMetricsTest {

  private static final ExternalCall MONGODB_FIND = new ExternalCall("mongodb", "find");

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final ExternalCallMetrics metrics = new ExternalCallMetrics(meterRegistry);

  @Test
  void recordSuccess_shouldTimeTheCallAndRecordNoFailure_taggedByDependencyAndOperation() {
    metrics.recordSuccess(MONGODB_FIND, Duration.ofMillis(250));

    Timer duration = durationOf(MONGODB_FIND);
    assertThat(duration.count()).isEqualTo(1);
    assertThat(duration.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(250.0);
    DistributionSummary failure = failureOf(MONGODB_FIND);
    assertThat(failure.count()).isEqualTo(1);
    assertThat(failure.totalAmount()).isZero();
  }

  @Test
  void recordFailure_shouldRecordOneFailure() {
    metrics.recordFailure(MONGODB_FIND, Duration.ofMillis(5), connectionReset());

    assertThat(durationOf(MONGODB_FIND).count()).isEqualTo(1);
    assertThat(failureOf(MONGODB_FIND).totalAmount()).isEqualTo(1.0);
  }

  @Test
  void recordSuccess_shouldLogTheCallWithItsOutcomeAndDuration() {
    var logs =
        CapturedLogs.loggedBy(
            ExternalCallMetrics.class,
            () -> metrics.recordSuccess(MONGODB_FIND, Duration.ofMillis(250)));

    assertThat(logs).hasSize(1);
    var event = logs.getFirst();
    assertThat(event.getFormattedMessage())
        .isEqualTo("External call mongodb/find success in 250 ms");
    assertThat(keyValues(event))
        .containsEntry("event.category", "external-call")
        .containsEntry("event.action", "mongodb/find")
        .containsEntry("event.outcome", "success")
        .containsEntry("event.duration", 250_000_000L)
        .doesNotContainKey("event.reason");
    assertThat(event.getThrowableProxy()).isNull();
  }

  @Test
  void recordFailure_shouldLogTheCallAsAFailure_withTheExceptionAndItsTypeAsTheReason() {
    var logs =
        CapturedLogs.loggedBy(
            ExternalCallMetrics.class,
            () -> metrics.recordFailure(MONGODB_FIND, Duration.ofMillis(5), connectionReset()));

    var event = logs.getFirst();
    assertThat(event.getFormattedMessage()).isEqualTo("External call mongodb/find failure in 5 ms");
    assertThat(keyValues(event))
        .containsEntry("event.outcome", "failure")
        .containsEntry("event.reason", "MongoSocketReadException");
    assertThat(event.getThrowableProxy().getClassName())
        .isEqualTo(MongoSocketReadException.class.getName());
    assertThat(event.getThrowableProxy().getMessage()).isEqualTo(CONNECTION_RESET);
  }

  private Timer durationOf(ExternalCall call) {
    return meterRegistry
        .get("external.call.duration")
        .tags("dependency", call.dependency(), "operation", call.operation())
        .timer();
  }

  private DistributionSummary failureOf(ExternalCall call) {
    return meterRegistry
        .get("external.call.failure")
        .tags("dependency", call.dependency(), "operation", call.operation())
        .summary();
  }
}
