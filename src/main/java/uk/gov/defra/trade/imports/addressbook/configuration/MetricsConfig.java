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
 * The meter registry that publishes to CloudWatch when {@code aws.emf.enabled} is true, and the
 * aspects that record {@code @Timed} and {@code @Counted} methods.
 */
@Slf4j
@Configuration
public class MetricsConfig {

  @Bean
  @ConditionalOnProperty(name = "aws.emf.enabled", havingValue = "true")
  EmfMeterRegistry emfMeterRegistry(@Value("${aws.emf.namespace}") String namespace) {
    EmfMeterRegistry registry = new EmfMeterRegistry(namespace, Clock.SYSTEM, MetricsLogger::new);
    registry.start(new NamedThreadFactory("emf-meter-registry"));
    log.info("Publishing metrics to CloudWatch namespace {}", namespace);
    return registry;
  }

  @Bean
  TimedAspect timedAspect(MeterRegistry meterRegistry) {
    log.debug("Creating TimedAspect for {}", meterRegistry.getClass().getSimpleName());
    return new TimedAspect(meterRegistry);
  }

  @Bean
  CountedAspect countedAspect(MeterRegistry registry) {
    return new CountedAspect(registry);
  }
}
