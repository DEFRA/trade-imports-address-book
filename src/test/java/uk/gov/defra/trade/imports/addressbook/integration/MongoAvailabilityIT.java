package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import uk.gov.defra.trade.imports.addressbook.metrics.MongoAvailability;

class MongoAvailabilityIT extends IntegrationBase {

  @Autowired private MongoAvailability mongoAvailability;

  @Test
  void isAvailable_shouldBeTrue_fromTheApplicationClientsClusterDescription() {
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(mongoAvailability.isAvailable()).contains(true));
  }

  @Test
  void isAvailable_shouldBecomeFalse_whenARunningMongoStops() {
    var availability = new MongoAvailability();
    try (var mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"))) {
      mongo.start();
      MongoClientSettings settings =
          MongoClientSettings.builder()
              .applyConnectionString(new ConnectionString(mongo.getConnectionString()))
              .applyToClusterSettings(cluster -> cluster.addClusterListener(availability))
              .build();
      try (MongoClient client = MongoClients.create(settings)) {
        await()
            .atMost(Duration.ofSeconds(30))
            .untilAsserted(() -> assertThat(availability.isAvailable()).contains(true));

        mongo.stop();

        await()
            .atMost(Duration.ofSeconds(30))
            .untilAsserted(() -> assertThat(availability.isAvailable()).contains(false));
      }
    }
  }
}
