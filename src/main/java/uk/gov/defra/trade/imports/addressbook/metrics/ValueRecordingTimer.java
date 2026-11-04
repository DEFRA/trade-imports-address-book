package uk.gov.defra.trade.imports.addressbook.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepTimer;
import java.util.List;
import java.util.concurrent.TimeUnit;

class ValueRecordingTimer extends StepTimer {

  private final RecordedValues recordedMilliseconds = new RecordedValues();

  ValueRecordingTimer(
      Id id,
      Clock clock,
      DistributionStatisticConfig distributionStatisticConfig,
      PauseDetector pauseDetector,
      long stepMillis) {
    super(
        id,
        clock,
        distributionStatisticConfig,
        pauseDetector,
        TimeUnit.MILLISECONDS,
        stepMillis,
        EmfMeterRegistry.NO_AGGREGABLE_PERCENTILES);
  }

  @Override
  protected void recordNonNegative(long amount, TimeUnit unit) {
    super.recordNonNegative(amount, unit);
    recordedMilliseconds.add(unit.toNanos(amount) / 1_000_000.0);
  }

  List<Double> takeRecordedMilliseconds() {
    return recordedMilliseconds.takeAll();
  }

  @Override
  public boolean equals(Object other) {
    return super.equals(other);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
