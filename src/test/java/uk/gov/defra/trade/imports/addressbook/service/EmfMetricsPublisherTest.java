package uk.gov.defra.trade.imports.addressbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmfMetricsPublisherTest {

  private static final String TEST_NAMESPACE = "test-namespace";

  @Mock private MeterRegistry meterRegistry;

  @Test
  void publishMetrics_shouldHandleEmptyMeterRegistry() {
    when(meterRegistry.getMeters()).thenReturn(Collections.emptyList());

    new EmfMetricsPublisher(TEST_NAMESPACE, meterRegistry).publishMetrics();

    org.mockito.Mockito.verify(meterRegistry).getMeters();
  }

  @Test
  void publishMetrics_shouldSkipNonFiniteValues() {
    Meter mockMeter = mock(Meter.class);
    Id meterId = mock(Id.class);
    Measurement finite = new Measurement(() -> 42.0, Statistic.COUNT);
    Measurement nonFinite = new Measurement(() -> Double.NaN, Statistic.MAX);

    when(meterId.getName()).thenReturn("test.metric");
    when(mockMeter.getId()).thenReturn(meterId);
    when(mockMeter.measure()).thenReturn(Arrays.asList(finite, nonFinite));
    when(meterRegistry.getMeters()).thenReturn(Arrays.asList(mockMeter));

    new EmfMetricsPublisher(TEST_NAMESPACE, meterRegistry).publishMetrics();

    org.mockito.Mockito.verify(mockMeter).measure();
  }

  @Test
  void publishMetrics_shouldNotRemoveMetersFromRegistry() {
    SimpleMeterRegistry realRegistry = new SimpleMeterRegistry();
    realRegistry.counter("controller.test").increment();

    new EmfMetricsPublisher(TEST_NAMESPACE, realRegistry).publishMetrics();

    assertThat(realRegistry.getMeters()).hasSize(1);
  }
}
