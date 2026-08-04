package uk.gov.defra.trade.imports.addressbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;

class EmfMetricsPublisherTest {

  private static final String TEST_NAMESPACE = "test-namespace";

  @Test
  void publishMetrics_shouldHandleEmptyMeterRegistry() {
    // Given
    MeterRegistry meterRegistry = mock(MeterRegistry.class);
    when(meterRegistry.getMeters()).thenReturn(Collections.emptyList());

    // When
    new EmfMetricsPublisher(TEST_NAMESPACE, meterRegistry).publishMetrics();

    // Then
    verify(meterRegistry).getMeters();
  }

  @Test
  void publishMetrics_shouldSkipNonFiniteValues() {
    // Given
    MeterRegistry meterRegistry = mock(MeterRegistry.class);
    Meter mockMeter = mock(Meter.class);
    Id meterId = mock(Id.class);
    Measurement finite = new Measurement(() -> 42.0, Statistic.COUNT);
    Measurement nonFinite = new Measurement(() -> Double.NaN, Statistic.MAX);
    MetricsLogger metricsLogger = mock(MetricsLogger.class);

    when(meterId.getName()).thenReturn("test.metric");
    when(mockMeter.getId()).thenReturn(meterId);
    when(mockMeter.measure()).thenReturn(Arrays.asList(finite, nonFinite));
    when(meterRegistry.getMeters()).thenReturn(Arrays.asList(mockMeter));

    // When
    new EmfMetricsPublisher(TEST_NAMESPACE, meterRegistry, () -> metricsLogger).publishMetrics();

    // Then
    verify(metricsLogger).putMetric("test.metric.count", 42.0);
    verify(metricsLogger, never()).putMetric("test.metric.max", Double.NaN);
  }

  @Test
  void publishMetrics_publishesFiniteMeasurementsViaMetricsLogger() {
    // Given
    SimpleMeterRegistry realRegistry = new SimpleMeterRegistry();
    realRegistry.counter("test.counter").increment(3);
    MetricsLogger metricsLogger = mock(MetricsLogger.class);
    EmfMetricsPublisher publisher =
        new EmfMetricsPublisher(TEST_NAMESPACE, realRegistry, () -> metricsLogger);

    // When
    publisher.publishMetrics();

    // Then
    verify(metricsLogger).setNamespace(TEST_NAMESPACE);
    verify(metricsLogger).putMetric("test.counter.count", 3.0);
    verify(metricsLogger).flush();
  }

  @Test
  void publishMetrics_shouldNotRemoveMetersFromRegistry() {
    // Given
    SimpleMeterRegistry realRegistry = new SimpleMeterRegistry();
    realRegistry.counter("controller.test").increment();

    // When
    new EmfMetricsPublisher(TEST_NAMESPACE, realRegistry).publishMetrics();

    // Then
    assertThat(realRegistry.getMeters()).hasSize(1);
  }

  @Test
  void emfMetricsPublisher_shouldHaveConditionalOnPropertyAnnotation() {
    // Given
    Class<?> clazz = EmfMetricsPublisher.class;

    // When
    ConditionalOnProperty annotation = clazz.getAnnotation(ConditionalOnProperty.class);

    // Then
    assertThat(annotation).isNotNull();
    assertThat(annotation.name()).containsExactly("aws.emf.enabled");
    assertThat(annotation.havingValue()).isEqualTo("true");
  }

  @Test
  void emfMetricsPublisher_shouldBeAnnotatedAsService() {
    // Given
    Class<?> clazz = EmfMetricsPublisher.class;

    // When
    Service annotation = clazz.getAnnotation(Service.class);

    // Then
    assertThat(annotation).isNotNull();
  }

  @Test
  void publishMetrics_shouldBeScheduled() throws NoSuchMethodException {
    // Given
    Method method = EmfMetricsPublisher.class.getMethod("publishMetrics");

    // When
    Scheduled annotation = method.getAnnotation(Scheduled.class);

    // Then
    assertThat(annotation).isNotNull();
    assertThat(annotation.fixedRate()).isEqualTo(60000);
  }
}
