package uk.gov.defra.trade.imports.addressbook.service;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Measurement;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;

@Service
@Slf4j
@ConditionalOnProperty(name = "aws.emf.enabled", havingValue = "true", matchIfMissing = false)
public class EmfMetricsPublisher {

  private final String namespace;
  private final MeterRegistry meterRegistry;
  private final Supplier<MetricsLogger> metricsLoggerSupplier;

  @Autowired
  EmfMetricsPublisher(
      @Value("${aws.emf.namespace}") String namespace, MeterRegistry meterRegistry) {
    this(namespace, meterRegistry, MetricsLogger::new);
  }

  EmfMetricsPublisher(
      String namespace,
      MeterRegistry meterRegistry,
      Supplier<MetricsLogger> metricsLoggerSupplier) {
    this.namespace = namespace;
    this.meterRegistry = meterRegistry;
    this.metricsLoggerSupplier = metricsLoggerSupplier;
  }

  @Scheduled(fixedRate = 60000)
  public void publishMetrics() {
    try {
      MetricsLogger metricsLogger = metricsLoggerSupplier.get();
      metricsLogger.setNamespace(namespace);
      for (Meter meter : meterRegistry.getMeters()) {
        for (Measurement measurement : meter.measure()) {
          double value = measurement.getValue();
          if (!Double.isFinite(value)) {
            continue;
          }
          String metricKey =
              meter.getId().getName() + "." + measurement.getStatistic().getTagValueRepresentation();
          log.trace("Publishing metric {} with value {}", metricKey, value);
          metricsLogger.putMetric(metricKey, value);
        }
      }
      metricsLogger.flush();
    } catch (RuntimeException ex) {
      log.warn("Failed to publish EMF metrics", ex);
    }
  }
}
