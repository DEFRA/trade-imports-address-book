package uk.gov.defra.trade.imports.addressbook.configuration;

import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Micrometer AOP aspects for {@code @Timed} and {@code @Counted} controller metrics, plus scheduling
 * support for {@link uk.gov.defra.trade.imports.addressbook.service.EmfMetricsPublisher}.
 */
@Slf4j
@Configuration
@EnableScheduling
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
}
