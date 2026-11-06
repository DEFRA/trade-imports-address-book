package uk.gov.defra.trade.imports.addressbook.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepTimer;
import java.util.concurrent.TimeUnit;

/** A step timer that also keeps each recorded duration, in nanoseconds, until it is taken. */
public class ValueRecordingTimer extends StepTimer {

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

  /**
   * The durations recorded.
   *
   * @return the recorded durations, in nanoseconds
   */
  public RecordedValues recordedNanos() {
    return recordedNanos;
  }
}
