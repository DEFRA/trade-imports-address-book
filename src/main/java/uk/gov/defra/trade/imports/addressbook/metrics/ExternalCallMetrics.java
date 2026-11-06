package uk.gov.defra.trade.imports.addressbook.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

/**
 * Records each call to a dependency as the timer {@code external.call.duration} and the
 * distribution summary {@code external.call.failure} (1 for a failure, 0 for a success), both
 * tagged {@code dependency} and {@code operation}, and logs it with the ECS {@code event.*} fields.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalCallMetrics {

  static final String DURATION_METRIC = "external.call.duration";
  static final String FAILURE_METRIC = "external.call.failure";

  private final MeterRegistry meterRegistry;

  /**
   * Records a call that succeeded.
   *
   * @param dependency the system called
   * @param operation what was asked of it, from a fixed set
   * @param elapsed how long the call took
   */
  public void recordSuccess(String dependency, String operation, Duration elapsed) {
    record(dependency, operation, elapsed, 0);
    logCall(Level.INFO, dependency + "/" + operation, "success", elapsed, null);
  }

  /**
   * Records a call that failed.
   *
   * @param dependency the system called
   * @param operation what was asked of it, from a fixed set
   * @param elapsed how long the call took
   * @param cause why it failed
   */
  public void recordFailure(
      String dependency, String operation, Duration elapsed, Throwable cause) {
    record(dependency, operation, elapsed, 1);
    logCall(Level.WARN, dependency + "/" + operation, "failure", elapsed, cause);
  }

  private void record(String dependency, String operation, Duration elapsed, int failure) {
    Timer.builder(DURATION_METRIC)
        .tag("dependency", dependency)
        .tag("operation", operation)
        .register(meterRegistry)
        .record(elapsed);
    DistributionSummary.builder(FAILURE_METRIC)
        .tag("dependency", dependency)
        .tag("operation", operation)
        .register(meterRegistry)
        .record(failure);
  }

  private static void logCall(
      Level level, String action, String outcome, Duration elapsed, Throwable cause) {
    log.atLevel(level)
        .addKeyValue("event.category", "external-call")
        .addKeyValue("event.action", action)
        .addKeyValue("event.outcome", outcome)
        .addKeyValue("event.duration", elapsed.toNanos())
        .setCause(cause)
        .log("External call {} {} in {} ms", action, outcome, elapsed.toMillis());
  }
}
