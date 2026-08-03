package uk.gov.defra.trade.imports.addressbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;

@ExtendWith(MockitoExtension.class)
class EmfMetricsPublisherTest {

  private static final String TEST_NAMESPACE = "test-namespace";

  @Mock private MeterRegistry meterRegistry;

  @Test
  void publishMetrics_shouldHandleEmptyMeterRegistry() {
    when(meterRegistry.getMeters()).thenReturn(Collections.emptyList());
    AtomicReference<MetricsLogger> capturedLogger = new AtomicReference<>();
    Supplier<MetricsLogger> supplier =
        () -> {
          MetricsLogger logger = mock(MetricsLogger.class);
          capturedLogger.set(logger);
          return logger;
        };

    new EmfMetricsPublisher(TEST_NAMESPACE, meterRegistry, supplier).publishMetrics();

    verify(meterRegistry).getMeters();
    verify(capturedLogger.get()).setNamespace(TEST_NAMESPACE);
    verify(capturedLogger.get()).flush();
  }

  @Test
  void publishMetrics_shouldSkipNonFiniteValues() {
    Meter mockMeter = mock(Meter.class);
    Id meterId = mock(Id.class);
    Measurement finite = new Measurement(() -> 42.0, Statistic.COUNT);
    Measurement nonFinite = new Measurement(() -> Double.NaN, Statistic.MAX);
    MetricsLogger metricsLogger = mock(MetricsLogger.class);

    when(meterId.getName()).thenReturn("test.metric");
    when(mockMeter.getId()).thenReturn(meterId);
    when(mockMeter.measure()).thenReturn(Arrays.asList(finite, nonFinite));
    when(meterRegistry.getMeters()).thenReturn(Arrays.asList(mockMeter));

    new EmfMetricsPublisher(TEST_NAMESPACE, meterRegistry, () -> metricsLogger).publishMetrics();

    verify(mockMeter).measure();
    verify(metricsLogger).putMetric("test.metric.count", 42.0);
  }

  @Test
  void publishMetrics_shouldNotRemoveMetersFromRegistry() {
    SimpleMeterRegistry realRegistry = new SimpleMeterRegistry();
    realRegistry.counter("controller.test").increment();

    new EmfMetricsPublisher(TEST_NAMESPACE, realRegistry).publishMetrics();

    assertThat(realRegistry.getMeters()).hasSize(1);
  }
}
