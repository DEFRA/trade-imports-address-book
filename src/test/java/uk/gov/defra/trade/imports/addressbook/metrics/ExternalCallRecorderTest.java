package uk.gov.defra.trade.imports.addressbook.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs.keyValues;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs.loggedBy;
import static uk.gov.defra.trade.imports.addressbook.testsupport.ExternalCallMeters.durationCount;
import static uk.gov.defra.trade.imports.addressbook.testsupport.ExternalCallMeters.durationMillis;
import static uk.gov.defra.trade.imports.addressbook.testsupport.ExternalCallMeters.failureCount;
import static uk.gov.defra.trade.imports.addressbook.testsupport.ExternalCallMeters.failureTotal;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExternalCallRecorderTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final ExternalCallRecorder recorder = new ExternalCallRecorder(meterRegistry);

  @Test
  void recordSuccess_recordsTheDurationAndAZeroFailure() {
    recorder.recordSuccess("mongodb", "find", Duration.ofMillis(7));

    assertThat(durationCount(meterRegistry, "mongodb", "find")).isEqualTo(1);
    assertThat(durationMillis(meterRegistry, "mongodb", "find")).isEqualTo(7);
    assertThat(failureCount(meterRegistry, "mongodb", "find")).isEqualTo(1);
    assertThat(failureTotal(meterRegistry, "mongodb", "find")).isZero();
  }

  @Test
  void recordFailure_recordsTheDurationAndAFailureOfOne() {
    recorder.recordFailure("mongodb", "insert", Duration.ofMillis(3), new IllegalStateException());

    assertThat(durationCount(meterRegistry, "mongodb", "insert")).isEqualTo(1);
    assertThat(durationMillis(meterRegistry, "mongodb", "insert")).isEqualTo(3);
    assertThat(failureCount(meterRegistry, "mongodb", "insert")).isEqualTo(1);
    assertThat(failureTotal(meterRegistry, "mongodb", "insert")).isEqualTo(1);
  }

  @Test
  void recordSuccess_logsOneInfoLineWithTheEventFieldsAndANanosecondDuration() {
    List<ILoggingEvent> lines =
        loggedBy(
            ExternalCallRecorder.class,
            () -> recorder.recordSuccess("mongodb", "find", Duration.ofMillis(2)));

    assertThat(lines).hasSize(1);
    ILoggingEvent line = lines.getFirst();
    assertThat(line.getLevel()).isEqualTo(Level.INFO);
    assertThat(line.getFormattedMessage()).isEqualTo("External call mongodb/find success in 2 ms");
    assertThat(keyValues(line))
        .containsEntry("event.category", "external-call")
        .containsEntry("event.action", "mongodb/find")
        .containsEntry("event.outcome", "success")
        .containsEntry("event.duration", 2_000_000L);
    assertThat(line.getThrowableProxy()).isNull();
  }

  @Test
  void recordFailure_logsOneWarnLineWithTheErrorTypeAndMessageButNoStackTrace() {
    IllegalStateException cause = new IllegalStateException("not primary");

    List<ILoggingEvent> lines =
        loggedBy(
            ExternalCallRecorder.class,
            () -> recorder.recordFailure("mongodb", "update", Duration.ofMillis(4), cause));

    assertThat(lines).hasSize(1);
    ILoggingEvent line = lines.getFirst();
    assertThat(line.getLevel()).isEqualTo(Level.WARN);
    assertThat(keyValues(line))
        .containsEntry("event.action", "mongodb/update")
        .containsEntry("event.outcome", "failure")
        .containsEntry("error.type", "java.lang.IllegalStateException")
        .containsEntry("error.message", "not primary");
    assertThat(line.getThrowableProxy()).isNull();
  }
}
