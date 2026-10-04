package uk.gov.defra.trade.imports.addressbook.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AddressTtlConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
          .withUserConfiguration(TtlProperties.class);

  @Configuration
  @EnableConfigurationProperties(AddressTtlConfig.class)
  static class TtlProperties {}

  @Test
  void isProd_isTrueOnlyForTheProdEnvironmentInAnyCase() {
    assertThat(new AddressTtlConfig(7, "prod").isProd()).isTrue();
    assertThat(new AddressTtlConfig(7, "PROD").isProd()).isTrue();
    assertThat(new AddressTtlConfig(7, "perf-test").isProd()).isFalse();
    assertThat(new AddressTtlConfig(7, null).isProd()).isFalse();
  }

  @Test
  void binding_leavesDaysUnsetWhenNotConfigured() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(AddressTtlConfig.class).days()).isNull();
        });
  }

  @Test
  void binding_readsTheConfiguredDays() {
    contextRunner
        .withPropertyValues("address-book.ttl.days=7")
        .run(context -> assertThat(context.getBean(AddressTtlConfig.class).days()).isEqualTo(7));
  }

  @Test
  void binding_rejectsZeroDaysSoNothingIsStampedAlreadyExpired() {
    contextRunner.withPropertyValues("address-book.ttl.days=0").run(context -> assertThat(context).hasFailed());
  }

  @Test
  void binding_rejectsNegativeDays() {
    contextRunner.withPropertyValues("address-book.ttl.days=-7").run(context -> assertThat(context).hasFailed());
  }
}
