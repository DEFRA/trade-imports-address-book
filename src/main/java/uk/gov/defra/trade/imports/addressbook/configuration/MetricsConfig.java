package uk.gov.defra.trade.imports.addressbook.configuration;

import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;
import uk.gov.defra.trade.imports.addressbook.metrics.EmfMeterRegistry;

/**
 * Micrometer AOP aspects for {@code @Timed} and {@code @Counted}, and, when {@code
 * aws.emf.enabled} is true, the registry that publishes every meter to CloudWatch.
 */
@Slf4j
@Configuration
public class MetricsConfig {

  @Bean
  TimedAspect timedAspect(MeterRegistry meterRegistry) {
    log.debug("Creating TimedAspect for {}", meterRegistry.getClass().getSimpleName());
    return new TimedAspect(meterRegistry);
  }

  @Bean
  CountedAspect countedAspect(MeterRegistry registry) {
    return new CountedAspect(registry);
  }

  @Bean
  @ConditionalOnProperty(name = "aws.emf.enabled", havingValue = "true")
  EmfMeterRegistry emfMeterRegistry(@Value("${aws.emf.namespace}") String namespace, Clock clock) {
    EmfMeterRegistry registry = new EmfMeterRegistry(namespace, clock, MetricsLogger::new);
    registry.start(new NamedThreadFactory("emf-metrics-publisher"));
    return registry;
  }
}
