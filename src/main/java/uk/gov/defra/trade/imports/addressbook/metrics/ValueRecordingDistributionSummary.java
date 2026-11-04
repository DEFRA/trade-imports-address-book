package uk.gov.defra.trade.imports.addressbook.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.step.StepDistributionSummary;
import java.util.List;

class ValueRecordingDistributionSummary extends StepDistributionSummary {

  private final RecordedValues recordedAmounts = new RecordedValues();

  ValueRecordingDistributionSummary(
      Id id,
      Clock clock,
      DistributionStatisticConfig distributionStatisticConfig,
      double scale,
      long stepMillis) {
    super(
        id,
        clock,
        distributionStatisticConfig,
        scale,
        stepMillis,
        EmfMeterRegistry.NO_AGGREGABLE_PERCENTILES);
  }

  @Override
  protected void recordNonNegative(double amount) {
    super.recordNonNegative(amount);
    recordedAmounts.add(amount);
  }

  List<Double> takeRecordedAmounts() {
    return recordedAmounts.takeAll();
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
