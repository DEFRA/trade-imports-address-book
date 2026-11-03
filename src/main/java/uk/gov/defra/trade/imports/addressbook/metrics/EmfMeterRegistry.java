package uk.gov.defra.trade.imports.addressbook.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import software.amazon.cloudwatchlogs.emf.exception.InvalidNamespaceException;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;
import software.amazon.cloudwatchlogs.emf.model.DimensionSet;
import software.amazon.cloudwatchlogs.emf.model.Unit;
import software.amazon.cloudwatchlogs.emf.util.Validator;

/**
 * A Micrometer registry that publishes the service's meters to CloudWatch as Embedded Metric
 * Format documents, once a minute, in the service's namespace.
 *
 * <p>Each meter is one document per publish, with its tags as the document's only dimensions.
 * Timers publish every duration they recorded since the last publish, in milliseconds, and
 * distribution summaries every amount, so CloudWatch computes the count, sum, average, maximum
 * and percentiles itself. Counters publish their count for the last minute, and gauges their
 * current value when it is finite. Long task timers, function meters and time gauges are not
 * published. A failure to publish one meter is logged as a warning and the others are still
 * published.
 */
@Slf4j
public class EmfMeterRegistry extends StepMeterRegistry {

  private final String namespace;
  private final Supplier<MetricsLogger> metricsLoggers;

  /**
   * Builds the registry. It publishes nothing until started.
   *
   * @param namespace the CloudWatch namespace
   * @param clock the clock that steps are measured by
   * @param metricsLoggers supplies a fresh logger for each document, so a test can capture them
   * @throws InvalidNamespaceException when the namespace is blank or invalid, so a bad namespace
   *     stops start-up instead of dropping every metric
   */
  public EmfMeterRegistry(String namespace, Clock clock, Supplier<MetricsLogger> metricsLoggers) {
    super(EmfRegistryConfig.INSTANCE, clock);
    Validator.validateNamespace(namespace);
    this.namespace = namespace;
    this.metricsLoggers = metricsLoggers;
  }

  @Override
  protected TimeUnit getBaseTimeUnit() {
    return TimeUnit.MILLISECONDS;
  }

  @Override
  protected Timer newTimer(
      Meter.Id id,
      DistributionStatisticConfig distributionStatisticConfig,
      PauseDetector pauseDetector) {
    return new ValueRecordingTimer(
        id, clock, distributionStatisticConfig, pauseDetector, stepMillis());
  }

  @Override
  protected DistributionSummary newDistributionSummary(
      Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
    return new ValueRecordingDistributionSummary(
        id, clock, distributionStatisticConfig, scale, stepMillis());
  }

  @Override
  protected void publish() {
    getMeters().forEach(this::publishMeter);
  }

  private void publishMeter(Meter meter) {
    try {
      meter.use(
          this::publishGauge,
          this::publishCounter,
          this::publishTimer,
          this::publishSummary,
          longTaskTimer -> {},
          timeGauge -> {},
          functionCounter -> {},
          functionTimer -> {},
          other -> {});
    } catch (RuntimeException e) {
      log.warn("Could not publish metric {}", meter.getId().getName(), e);
    }
  }

  private void publishGauge(Gauge gauge) {
    double value = gauge.value();
    if (Double.isFinite(value)) {
      write(gauge.getId(), List.of(value), Unit.COUNT);
    }
  }

  private void publishCounter(Counter counter) {
    write(counter.getId(), List.of(counter.count()), Unit.COUNT);
  }

  private void publishTimer(Timer timer) {
    if (timer instanceof ValueRecordingTimer recording) {
      write(timer.getId(), recording.takeRecordedMilliseconds(), Unit.MILLISECONDS);
    }
  }

  private void publishSummary(DistributionSummary summary) {
    if (summary instanceof ValueRecordingDistributionSummary recording) {
      write(summary.getId(), recording.takeRecordedAmounts(), Unit.COUNT);
    }
  }

  private void write(Meter.Id id, List<Double> values, Unit unit) {
    if (values.isEmpty()) {
      return;
    }
    MetricsLogger metricsLogger = metricsLoggers.get();
    metricsLogger.setNamespace(namespace);
    metricsLogger.setDimensions(dimensionsOf(id));
    values.forEach(value -> metricsLogger.putMetric(id.getName(), value, unit));
    metricsLogger.flush();
  }

  private static DimensionSet dimensionsOf(Meter.Id id) {
    DimensionSet dimensions = new DimensionSet();
    for (Tag tag : id.getTagsAsIterable()) {
      dimensions.addDimension(tag.getKey(), tag.getValue());
    }
    return dimensions;
  }

  private long stepMillis() {
    return EmfRegistryConfig.INSTANCE.step().toMillis();
  }

  private enum EmfRegistryConfig implements StepRegistryConfig {
    INSTANCE;

    @Override
    public String prefix() {
      return "emf";
    }

    @Override
    public String get(String key) {
      return null;
    }
  }
}
