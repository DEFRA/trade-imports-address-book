package uk.gov.defra.trade.imports.addressbook.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricsConfigTest {

  private MetricsConfig metricsConfig;
  private MeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    metricsConfig = new MetricsConfig();
    meterRegistry = new SimpleMeterRegistry();
  }

  @Test
  void timedAspect_recordsTimedMetricWhenIntercepted() throws Throwable {
    // Given
    TimedAspect timedAspect = metricsConfig.timedAspect(meterRegistry);
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    MethodSignature signature = mock(MethodSignature.class);
    Method method = TimedTarget.class.getDeclaredMethod("timedOperation");
    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getMethod()).thenReturn(method);
    when(joinPoint.proceed()).thenReturn("done");

    // When
    timedAspect.timedMethod(joinPoint);

    // Then
    assertThat(meterRegistry.find("test.timed.operation").timer()).isNotNull();
  }

  @Test
  void countedAspect_isCreatedWithTheSuppliedRegistry() {
    // When
    CountedAspect result = metricsConfig.countedAspect(meterRegistry);

    // Then
    assertThat(result).isNotNull();
  }

  private static final class TimedTarget {
    @Timed("test.timed.operation")
    void timedOperation() {}
  }
}
