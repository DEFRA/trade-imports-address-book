package uk.gov.defra.trade.imports.addressbook.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Records each call to a system the service depends on, as two meters tagged {@code Dependency}
 * and {@code Operation}: the timer {@code ExternalCallDuration} and the distribution summary
 * {@code ExternalCallFailure}, which records 1 for a failed call and 0 for a successful one.
 *
 * <p>Each call is also logged, with the ECS {@code event.*} fields and, where there is one, the
 * request's {@code trace.id}, and the exception when the call failed with one. The {@code event.*}
 * fields are key-value pairs, not MDC entries, so that {@code EcsEventEncoder} can write them as
 * the nested object, with a numeric duration, that CDP keeps.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalCallMetrics {

  static final String DURATION_METRIC = "ExternalCallDuration";
  static final String FAILURE_METRIC = "ExternalCallFailure";
  static final String EVENT_CATEGORY = "external-call";

  private final MeterRegistry meterRegistry;

  /**
   * Logs a successful call and records it.
   *
   * @param call the call that succeeded
   * @param elapsed how long it took
   */
  public void recordSuccess(ExternalCall call, Duration elapsed) {
    logCall(call, elapsed, "success", null);
    record(call, elapsed, 0);
  }

  /**
   * Logs a failed call, with its cause, and records it.
   *
   * @param call the call that failed
   * @param elapsed how long it took
   * @param cause the exception it failed with
   */
  public void recordFailure(ExternalCall call, Duration elapsed, Throwable cause) {
    logCall(call, elapsed, "failure", cause);
    record(call, elapsed, 1);
  }

  private void record(ExternalCall call, Duration elapsed, int failures) {
    Tags tags = Tags.of("Dependency", call.dependency(), "Operation", call.operation());
    meterRegistry.timer(DURATION_METRIC, tags).record(elapsed);
    DistributionSummary.builder(FAILURE_METRIC).tags(tags).register(meterRegistry).record(failures);
  }

  private static void logCall(
      ExternalCall call, Duration elapsed, String outcome, Throwable cause) {
    String action = call.dependency() + "/" + call.operation();
    log.atInfo()
        .addKeyValue("event.category", EVENT_CATEGORY)
        .addKeyValue("event.action", action)
        .addKeyValue("event.outcome", outcome)
        .addKeyValue("event.duration", elapsed.toNanos())
        .setCause(cause)
        .log("External call {} {} in {} ms", action, outcome, elapsed.toMillis());
  }
}
