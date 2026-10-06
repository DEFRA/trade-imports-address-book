package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import uk.gov.defra.trade.imports.addressbook.configuration.AddressTtlConfig;

class AddressExpiryIndexTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TtlProperties.class, AddressExpiryIndex.class)
          .withBean(MongoTemplate.class, () -> mock(MongoTemplate.class))
          .withPropertyValues("address-book.ttl.environment=dev");

  @Configuration
  @EnableConfigurationProperties(AddressTtlConfig.class)
  static class TtlProperties {}

  @Test
  void bean_isAbsentWhenExpiryIsNotConfigured() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(AddressExpiryIndex.class));
  }

  @Test
  void bean_isAbsentWhenExpiryIsDisabled() {
    contextRunner
        .withPropertyValues("address-book.ttl.expiry.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(AddressExpiryIndex.class));
  }

  @Test
  void bean_existsWhenExpiryIsEnabled() {
    contextRunner
        .withPropertyValues("address-book.ttl.expiry.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(AddressExpiryIndex.class));
  }

  @Test
  void applicationReady_createsTheTtlIndexWhenExpiryIsEnabled() {
    // Given
    MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    IndexOperations indexOps = mock(IndexOperations.class);
    when(mongoTemplate.indexOps(Address.class)).thenReturn(indexOps);

    new ApplicationContextRunner()
        .withUserConfiguration(TtlProperties.class, AddressExpiryIndex.class)
        .withBean(MongoTemplate.class, () -> mongoTemplate)
        .withPropertyValues(
            "address-book.ttl.expiry.enabled=true", "address-book.ttl.environment=dev")
        .run(
            context -> {
              ConfigurableApplicationContext source =
                  context.getSourceApplicationContext(ConfigurableApplicationContext.class);

              // When
              source.publishEvent(
                  new ApplicationReadyEvent(
                      new SpringApplication(), new String[0], source, Duration.ZERO));

              // Then
              ArgumentCaptor<IndexDefinition> captor =
                  ArgumentCaptor.forClass(IndexDefinition.class);
              verify(indexOps).createIndex(captor.capture());
              assertThat(captor.getValue().getIndexOptions().getString("name"))
                  .isEqualTo("address_expire_at_ttl");
            });
  }

  @Test
  void createIndex_outsideProdCreatesATtlIndexThatExpiresAtExpireAt() {
    // Given
    MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    IndexOperations indexOps = mock(IndexOperations.class);
    when(mongoTemplate.indexOps(Address.class)).thenReturn(indexOps);
    AddressExpiryIndex expiryIndex =
        new AddressExpiryIndex(mongoTemplate, new AddressTtlConfig(7, "dev"));

    // When
    expiryIndex.createIndex();

    // Then
    ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);
    verify(indexOps).createIndex(captor.capture());
    assertThat(captor.getValue().getIndexKeys()).isEqualTo(new Document("expireAt", 1));
    assertThat(captor.getValue().getIndexOptions())
        .isEqualTo(new Document("name", "address_expire_at_ttl").append("expireAfterSeconds", 0L));
  }

  @Test
  void createIndex_inProdCreatesNothingEvenWhenExpiryIsEnabled() {
    // Given
    MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    AddressExpiryIndex expiryIndex =
        new AddressExpiryIndex(mongoTemplate, new AddressTtlConfig(7, "prod"));

    // When
    expiryIndex.createIndex();

    // Then
    verifyNoInteractions(mongoTemplate);
  }
}
