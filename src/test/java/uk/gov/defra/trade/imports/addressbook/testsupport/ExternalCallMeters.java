package uk.gov.defra.trade.imports.addressbook.testsupport;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class ExternalCallMeters {

  private ExternalCallMeters() {}

  public static long durationCount(MeterRegistry registry, String dependency, String operation) {
    return duration(registry, dependency, operation).map(Timer::count).orElse(0L);
  }

  public static double durationMillis(
      MeterRegistry registry, String dependency, String operation) {
    return duration(registry, dependency, operation)
        .map(timer -> timer.totalTime(TimeUnit.MILLISECONDS))
        .orElse(0.0);
  }

  public static long failureCount(MeterRegistry registry, String dependency, String operation) {
    return failure(registry, dependency, operation).map(DistributionSummary::count).orElse(0L);
  }

  public static double failureTotal(MeterRegistry registry, String dependency, String operation) {
    return failure(registry, dependency, operation)
        .map(DistributionSummary::totalAmount)
        .orElse(0.0);
  }

  private static Optional<Timer> duration(
      MeterRegistry registry, String dependency, String operation) {
    return Optional.ofNullable(
        registry
            .find("external.call.duration")
            .tags("dependency", dependency, "operation", operation)
            .timer());
  }

  private static Optional<DistributionSummary> failure(
      MeterRegistry registry, String dependency, String operation) {
    return Optional.ofNullable(
        registry
            .find("external.call.failure")
            .tags("dependency", dependency, "operation", operation)
            .summary());
  }
}
