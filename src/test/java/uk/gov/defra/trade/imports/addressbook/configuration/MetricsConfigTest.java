package uk.gov.defra.trade.imports.addressbook.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs.loggedBy;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.convert.ConversionService;
import uk.gov.defra.trade.imports.addressbook.metrics.EmfMeterRegistry;

@ExtendWith(MockitoExtension.class)
class MetricsConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(MetricsConfig.class)
          .withBean(
              ConfigurableApplicationContext.CONVERSION_SERVICE_BEAN_NAME,
              ConversionService.class,
              ApplicationConversionService::getSharedInstance)
          .withBean(
              SimpleMeterRegistry.class, SimpleMeterRegistry::new, bean -> bean.setPrimary(true))
          .withPropertyValues("aws.emf.namespace=test-namespace", "aws.emf.step=1m");

  private MetricsConfig metricsConfig;
  private MeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    metricsConfig = new MetricsConfig();
    meterRegistry = new SimpleMeterRegistry();
  }

  @Test
  void emfMeterRegistry_isCreatedWhenEmfIsEnabled() {
    contextRunner
        .withPropertyValues("aws.emf.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(EmfMeterRegistry.class));
  }

  @Test
  void emfMeterRegistry_isLeftOutWhenEmfIsDisabled() {
    contextRunner
        .withPropertyValues("aws.emf.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(EmfMeterRegistry.class);
            });
  }

  @Test
  void emfPublishingOffLog_logsThatMetricsAreNotPublished_whenEmfIsNotEnabled() {
    contextRunner.run(
        context -> {
          ApplicationRunner offLog = context.getBean(ApplicationRunner.class);

          List<ILoggingEvent> events =
              loggedBy(MetricsConfig.class, () -> runWithNoArguments(offLog));

          assertThat(events)
              .extracting(ILoggingEvent::getFormattedMessage)
              .containsExactly("Not publishing metrics to CloudWatch: aws.emf.enabled is not true");
        });
  }

  @Test
  void emfPublishingOffLog_isLeftOutWhenEmfIsEnabled() {
    contextRunner
        .withPropertyValues("aws.emf.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(ApplicationRunner.class));
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

  private static void runWithNoArguments(ApplicationRunner runner) {
    try {
      runner.run(new DefaultApplicationArguments());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static final class TimedTarget {
    @Timed("test.timed.operation")
    void timedOperation() {}
  }
}
