package uk.gov.defra.trade.imports.addressbook.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepTimer;
import java.util.concurrent.TimeUnit;

class ValueRecordingTimer extends StepTimer {

  private final RecordedValues recordedNanos = new RecordedValues();

  ValueRecordingTimer(
      Id id,
      Clock clock,
      DistributionStatisticConfig distributionStatisticConfig,
      PauseDetector pauseDetector,
      TimeUnit baseTimeUnit,
      long stepMillis) {
    super(id, clock, distributionStatisticConfig, pauseDetector, baseTimeUnit, stepMillis, false);
  }

  @Override
  protected void recordNonNegative(long amount, TimeUnit unit) {
    super.recordNonNegative(amount, unit);
    recordedNanos.add((double) unit.toNanos(amount));
  }

  @Override
  public boolean equals(Object other) {
    return super.equals(other);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  RecordedValues recordedNanos() {
    return recordedNanos;
  }
}
