package uk.gov.defra.trade.imports.addressbook.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.step.StepDistributionSummary;

/** A step distribution summary that also keeps each recorded amount until it is taken. */
public class ValueRecordingDistributionSummary extends StepDistributionSummary {

  private final RecordedValues recordedAmounts = new RecordedValues();

  ValueRecordingDistributionSummary(
      Id id,
      Clock clock,
      DistributionStatisticConfig distributionStatisticConfig,
      double scale,
      long stepMillis) {
    super(id, clock, distributionStatisticConfig, scale, stepMillis, false);
  }

  @Override
  protected void recordNonNegative(double amount) {
    super.recordNonNegative(amount);
    recordedAmounts.add(amount);
  }

  /**
   * The amounts recorded, already scaled.
   *
   * @return the recorded amounts
   */
  public RecordedValues recordedAmounts() {
    return recordedAmounts;
  }
}
