package uk.gov.defra.trade.imports.addressbook;

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
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.configuration.AddressTtlConfig;
import uk.gov.defra.trade.imports.addressbook.transporter.Transporter;

class ExpiryIndexesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TtlProperties.class, ExpiryIndexes.class)
          .withBean(MongoTemplate.class, () -> mock(MongoTemplate.class))
          .withPropertyValues("address-book.ttl.environment=dev");

  @Configuration
  @EnableConfigurationProperties(AddressTtlConfig.class)
  static class TtlProperties {}

  @Test
  void bean_isAbsentWhenExpiryIsNotConfigured() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(ExpiryIndexes.class));
  }

  @Test
  void bean_isAbsentWhenExpiryIsDisabled() {
    contextRunner
        .withPropertyValues("address-book.ttl.expiry.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(ExpiryIndexes.class));
  }

  @Test
  void bean_existsWhenExpiryIsEnabled() {
    contextRunner
        .withPropertyValues("address-book.ttl.expiry.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(ExpiryIndexes.class));
  }

  @Test
  void applicationReady_createsTheTtlIndexesOnBothDocumentsWhenExpiryIsEnabled() {
    // Given
    MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    IndexOperations addressIndexOps = mock(IndexOperations.class);
    IndexOperations transporterIndexOps = mock(IndexOperations.class);
    when(mongoTemplate.indexOps(Address.class)).thenReturn(addressIndexOps);
    when(mongoTemplate.indexOps(Transporter.class)).thenReturn(transporterIndexOps);

    new ApplicationContextRunner()
        .withUserConfiguration(TtlProperties.class, ExpiryIndexes.class)
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
              ArgumentCaptor<IndexDefinition> addressCaptor =
                  ArgumentCaptor.forClass(IndexDefinition.class);
              verify(addressIndexOps).createIndex(addressCaptor.capture());
              assertThat(addressCaptor.getValue().getIndexOptions().getString("name"))
                  .isEqualTo("address_expire_at_ttl");

              ArgumentCaptor<IndexDefinition> transporterCaptor =
                  ArgumentCaptor.forClass(IndexDefinition.class);
              verify(transporterIndexOps).createIndex(transporterCaptor.capture());
              assertThat(transporterCaptor.getValue().getIndexOptions().getString("name"))
                  .isEqualTo("transporter_expire_at_ttl");
            });
  }

  @Test
  void createIndexes_outsideProdCreatesTtlIndexesOnBothDocumentsThatExpireAtExpireAt() {
    // Given
    MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    IndexOperations addressIndexOps = mock(IndexOperations.class);
    IndexOperations transporterIndexOps = mock(IndexOperations.class);
    when(mongoTemplate.indexOps(Address.class)).thenReturn(addressIndexOps);
    when(mongoTemplate.indexOps(Transporter.class)).thenReturn(transporterIndexOps);
    ExpiryIndexes expiryIndexes = new ExpiryIndexes(mongoTemplate, new AddressTtlConfig(7, "dev"));

    // When
    expiryIndexes.createIndexes();

    // Then
    ArgumentCaptor<IndexDefinition> addressCaptor = ArgumentCaptor.forClass(IndexDefinition.class);
    verify(addressIndexOps).createIndex(addressCaptor.capture());
    assertThat(addressCaptor.getValue().getIndexKeys()).isEqualTo(new Document("expireAt", 1));
    assertThat(addressCaptor.getValue().getIndexOptions())
        .isEqualTo(new Document("name", "address_expire_at_ttl").append("expireAfterSeconds", 0L));

    ArgumentCaptor<IndexDefinition> transporterCaptor =
        ArgumentCaptor.forClass(IndexDefinition.class);
    verify(transporterIndexOps).createIndex(transporterCaptor.capture());
    assertThat(transporterCaptor.getValue().getIndexKeys()).isEqualTo(new Document("expireAt", 1));
    assertThat(transporterCaptor.getValue().getIndexOptions())
        .isEqualTo(
            new Document("name", "transporter_expire_at_ttl").append("expireAfterSeconds", 0L));
  }

  @Test
  void createIndexes_inProdCreatesNothingEvenWhenExpiryIsEnabled() {
    // Given
    MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    ExpiryIndexes expiryIndexes =
        new ExpiryIndexes(mongoTemplate, new AddressTtlConfig(7, "prod"));

    // When
    expiryIndexes.createIndexes();

    // Then
    verifyNoInteractions(mongoTemplate);
  }
}
