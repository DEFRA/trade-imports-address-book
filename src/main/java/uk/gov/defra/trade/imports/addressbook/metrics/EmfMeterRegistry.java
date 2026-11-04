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
 * A Micrometer registry that writes the service's meters as Embedded Metric Format documents, once
 * a minute, in one namespace.
 *
 * <p>Each publish writes a meter's values with its tags as the documents' only dimensions, at most
 * 100 values to a document, so a meter with more values is written as several documents. Timers
 * write every duration they recorded since the last publish, in milliseconds, and distribution
 * summaries every amount. Counters write their count for the last minute, and gauges their
 * current value when it is finite. Long task timers, function meters and time gauges are not
 * written. A failure to write one meter is logged as a warning and the others are still written.
 */
@Slf4j
public class EmfMeterRegistry extends StepMeterRegistry {

  static final boolean NO_AGGREGABLE_PERCENTILES = false;

  private final String namespace;
  private final Supplier<MetricsLogger> metricsLoggerFactory;

  /**
   * Builds the registry. It publishes nothing until started.
   *
   * @param namespace the CloudWatch namespace
   * @param clock the clock that steps are measured by
   * @param metricsLoggerFactory makes a new logger on each call, one for each meter written
   * @throws InvalidNamespaceException when the namespace is blank or not a valid CloudWatch
   *     namespace
   */
  public EmfMeterRegistry(
      String namespace, Clock clock, Supplier<MetricsLogger> metricsLoggerFactory) {
    super(EmfRegistryConfig.INSTANCE, clock);
    Validator.validateNamespace(namespace);
    this.namespace = namespace;
    this.metricsLoggerFactory = metricsLoggerFactory;
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
    MetricsLogger metricsLogger = metricsLoggerFactory.get();
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
