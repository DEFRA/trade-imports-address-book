package uk.gov.defra.trade.imports.addressbook.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepTimer;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** A step timer that also keeps every duration it records, in milliseconds, until taken. */
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
        false);
  }

  @Override
  protected void recordNonNegative(long amount, TimeUnit unit) {
    super.recordNonNegative(amount, unit);
    recordedMilliseconds.add(unit.toNanos(amount) / 1_000_000.0);
  }

  List<Double> takeRecordedMilliseconds() {
    return recordedMilliseconds.takeAll();
  }
}
