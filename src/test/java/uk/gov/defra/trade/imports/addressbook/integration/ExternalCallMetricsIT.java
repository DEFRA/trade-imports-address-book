package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs.keyValues;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.Encoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.metrics.ExternalCallMetrics;
import uk.gov.defra.trade.imports.addressbook.metrics.MongoAvailability;
import uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs;

class ExternalCallMetricsIT extends IntegrationBase {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private MongoTemplate mongoTemplate;

  @Autowired private MongoAvailability mongoAvailability;

  @Test
  void listAddresses_shouldWriteTheFindLineWithTheCallersTraceIdAndANestedEventObject()
      throws IOException {
    // Given
    saveAddress(addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE));
    HttpHeaders headers = new HttpHeaders();
    headers.set("x-cdp-request-id", "trace-from-the-caller");
    headers.set(ORG_HEADER, ORGANISATION_ID);

    // When
    List<ILoggingEvent> logs =
        CapturedLogs.loggedBy(
            ExternalCallMetrics.class,
            () -> {
              ResponseEntity<String> response =
                  restTemplate.exchange(
                      "/organisation/" + ORGANISATION_ID + "/addresses",
                      HttpMethod.GET,
                      new HttpEntity<>(headers),
                      String.class);
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            });

    // Then
    ILoggingEvent findLine =
        logs.stream()
            .filter(event -> "mongodb/find".equals(keyValues(event).get("event.action")))
            .findFirst()
            .orElseThrow();
    JsonNode line = new ObjectMapper().readTree(consoleEncoder().encode(findLine));
    assertThat(line.path("trace.id").asText()).isEqualTo("trace-from-the-caller");
    assertThat(line.path("service.name").asText()).isEqualTo("trade-imports-address-book");
    JsonNode eventObject = line.path("event");
    assertThat(eventObject.isObject()).as("a nested event object").isTrue();
    assertThat(eventObject.path("category").asText()).isEqualTo("external-call");
    assertThat(eventObject.path("action").asText()).isEqualTo("mongodb/find");
    assertThat(eventObject.path("outcome").asText()).isEqualTo("success");
    assertThat(eventObject.path("duration").isIntegralNumber()).isTrue();
    assertThat(line.has("event.action")).isFalse();
    assertThat(line.has("event.duration")).isFalse();
  }

  @Test
  void aCommandTheServerRejects_shouldBeRecordedAsAFailedOtherCall() {
    // When
    List<ILoggingEvent> logs =
        CapturedLogs.loggedBy(
            ExternalCallMetrics.class,
            () ->
                assertThatThrownBy(() -> mongoTemplate.executeCommand("{ notACommand: 1 }"))
                    .isInstanceOf(DataAccessException.class));

    // Then
    assertThat(logs)
        .singleElement()
        .satisfies(
            event ->
                assertThat(keyValues(event))
                    .containsEntry("event.action", "mongodb/other")
                    .containsEntry("event.outcome", "failure"));
  }

  @Test
  void mongoAvailability_shouldReportAvailable_fromTheApplicationClientsHeartbeats() {
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(mongoAvailability.isAvailable()).contains(true));
  }

  @Test
  void mongoAvailability_shouldReportUnavailable_whenARunningMongoStops() {
    // Given
    var availability = new MongoAvailability();
    try (var mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"))) {
      mongo.start();
      MongoClientSettings settings =
          MongoClientSettings.builder()
              .applyConnectionString(new ConnectionString(mongo.getConnectionString()))
              .applyToServerSettings(server -> server.addServerMonitorListener(availability))
              .build();
      try (MongoClient client = MongoClients.create(settings)) {
        await()
            .atMost(Duration.ofSeconds(30))
            .untilAsserted(() -> assertThat(availability.isAvailable()).contains(true));

        // When
        mongo.stop();

        // Then
        await()
            .atMost(Duration.ofSeconds(30))
            .untilAsserted(() -> assertThat(availability.isAvailable()).contains(false));
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Encoder<ILoggingEvent> consoleEncoder() {
    var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    var console =
        (OutputStreamAppender<ILoggingEvent>)
            loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE");
    return console.getEncoder();
  }
}
