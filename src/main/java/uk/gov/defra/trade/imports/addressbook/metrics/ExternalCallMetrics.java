package uk.gov.defra.trade.imports.addressbook.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

/**
 * Records each call to a system the service depends on, as two meters tagged {@code Dependency}
 * and {@code Operation}: the timer {@code ExternalCallDuration} and the distribution summary
 * {@code ExternalCallFailure}, which records 1 for a failed call and 0 for a successful one.
 *
 * <p>Each call is also logged at info with the key-value pairs {@code event.category}, {@code
 * event.action}, {@code event.outcome} and {@code event.duration} in nanoseconds. A failed call's
 * line also carries {@code event.reason}, the simple class name of its exception, and the
 * exception itself.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalCallMetrics {

  static final String DURATION_METRIC = "ExternalCallDuration";
  static final String FAILURE_METRIC = "ExternalCallFailure";
  static final String DEPENDENCY_TAG = "Dependency";
  static final String EVENT_CATEGORY = "external-call";

  private final MeterRegistry meterRegistry;

  public void recordSuccess(ExternalCall call, Duration elapsed) {
    logCall(call, elapsed, "success", null);
    recordCall(call, elapsed, 0);
  }

  public void recordFailure(ExternalCall call, Duration elapsed, Throwable cause) {
    logCall(call, elapsed, "failure", cause);
    recordCall(call, elapsed, 1);
  }

  private void recordCall(ExternalCall call, Duration elapsed, int failures) {
    Tags tags = Tags.of(DEPENDENCY_TAG, call.dependency(), "Operation", call.operation());
    meterRegistry.timer(DURATION_METRIC, tags).record(elapsed);
    DistributionSummary.builder(FAILURE_METRIC).tags(tags).register(meterRegistry).record(failures);
  }

  private static void logCall(
      ExternalCall call, Duration elapsed, String outcome, Throwable cause) {
    String action = call.dependency() + "/" + call.operation();
    LoggingEventBuilder line =
        log.atInfo()
            .addKeyValue("event.category", EVENT_CATEGORY)
            .addKeyValue("event.action", action)
            .addKeyValue("event.outcome", outcome)
            .addKeyValue("event.duration", elapsed.toNanos());
    if (cause != null) {
      line =
          line.addKeyValue("event.reason", cause.getClass().getSimpleName()).setCause(cause);
    }
    line.log("External call {} {} in {} ms", action, outcome, elapsed.toMillis());
  }
}
