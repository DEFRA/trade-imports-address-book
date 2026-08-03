package uk.gov.defra.trade.imports.addressbook.service;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Measurement;
import lombok.extern.slf4j.Slf4j;
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

  EmfMetricsPublisher(
      @Value("${aws.emf.namespace}") String namespace, MeterRegistry meterRegistry) {
    this.namespace = namespace;
    this.meterRegistry = meterRegistry;
  }

  @Scheduled(fixedRate = 60000)
  public void publishMetrics() {
    try {
      MetricsLogger metricsLogger = new MetricsLogger();
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
