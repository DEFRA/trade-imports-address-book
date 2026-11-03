package uk.gov.defra.trade.imports.addressbook.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import co.elastic.logging.logback.EcsEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs;

class EcsEventEncoderTest {

  private static final Logger LOG = LoggerFactory.getLogger(EcsEventEncoderTest.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  private final EcsEventEncoder encoder = new EcsEventEncoder();
  private final EcsEncoder parentEncoder = new EcsEncoder();

  @BeforeEach
  void startEncoders() {
    var context = (LoggerContext) LoggerFactory.getILoggerFactory();
    for (EcsEncoder each : new EcsEncoder[] {encoder, parentEncoder}) {
      each.setContext(context);
      each.setServiceName("trade-imports-address-book");
      each.start();
    }
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void encode_shouldNestEventKeyValuePairs_withANumericDuration() throws IOException {
    // Given
    ILoggingEvent event =
        logged(
            log ->
                log.atInfo()
                    .addKeyValue("event.category", "external-call")
                    .addKeyValue("event.action", "mongodb/find")
                    .addKeyValue("event.outcome", "success")
                    .addKeyValue("event.duration", 203_000_000L)
                    .log("External call mongodb/find success in 203 ms"));

    // When
    JsonNode line = encode(event);

    // Then
    JsonNode eventObject = line.path("event");
    assertThat(eventObject.isObject()).as("a nested event object").isTrue();
    assertThat(eventObject.path("category").asText()).isEqualTo("external-call");
    assertThat(eventObject.path("action").asText()).isEqualTo("mongodb/find");
    assertThat(eventObject.path("outcome").asText()).isEqualTo("success");
    assertThat(eventObject.path("duration").isIntegralNumber()).isTrue();
    assertThat(eventObject.path("duration").asLong()).isEqualTo(203_000_000L);
  }

  @Test
  void encode_shouldWriteNoFlatEventKeys() throws IOException {
    // Given
    ILoggingEvent event =
        logged(
            log ->
                log.atInfo()
                    .addKeyValue("event.category", "external-call")
                    .addKeyValue("event.duration", 5L)
                    .log("External call"));

    // When
    JsonNode line = encode(event);

    // Then
    assertThat(line.has("event.category")).isFalse();
    assertThat(line.has("event.duration")).isFalse();
  }

  @Test
  void encode_shouldKeepTheParentsFields_includingTraceIdAndTheException() throws IOException {
    // Given
    MDC.put("trace.id", "trace-from-the-caller");
    ILoggingEvent event =
        logged(
            log ->
                log.atWarn()
                    .addKeyValue("event.outcome", "failure")
                    .setCause(new IllegalStateException("mongodb unavailable"))
                    .log("External call failed"));

    // When
    JsonNode line = encode(event);

    // Then
    assertThat(line.path("message").asText()).isEqualTo("External call failed");
    assertThat(line.path("log.level").asText()).isEqualTo("WARN");
    assertThat(line.path("service.name").asText()).isEqualTo("trade-imports-address-book");
    assertThat(line.path("trace.id").asText()).isEqualTo("trace-from-the-caller");
    assertThat(line.path("error.type").asText()).isEqualTo("java.lang.IllegalStateException");
    assertThat(line.path("error.message").asText()).isEqualTo("mongodb unavailable");
    assertThat(line.has("error.stack_trace")).isTrue();
    assertThat(line.path("event").path("outcome").asText()).isEqualTo("failure");
  }

  @Test
  void encode_shouldWriteALineWithNoEventKeyValuePairs_exactlyAsTheEcsEncoderDoes() {
    // Given
    MDC.put("trace.id", "trace-from-the-caller");
    ILoggingEvent event = logged(log -> log.info("Started"));

    // When & Then
    assertThat(new String(encoder.encode(event)))
        .isEqualTo(new String(parentEncoder.encode(event)));
  }

  @Test
  void encode_shouldIgnoreKeyValuePairsOutsideEvent() throws IOException {
    // Given
    ILoggingEvent event =
        logged(log -> log.atInfo().addKeyValue("other.key", "value").log("Started"));

    // When
    JsonNode line = encode(event);

    // Then
    assertThat(line.has("event")).isFalse();
    assertThat(line.has("other.key")).isFalse();
  }

  @Test
  void encode_shouldEscapeStringValues() throws IOException {
    // Given
    ILoggingEvent event =
        logged(
            log -> log.atInfo().addKeyValue("event.action", "say \"hi\" \\ there").log("Escaped"));

    // When
    JsonNode line = encode(event);

    // Then
    assertThat(line.path("event").path("action").asText()).isEqualTo("say \"hi\" \\ there");
  }

  @Test
  void encode_shouldWriteAJsonNull_whenAnEventValueIsNull() throws IOException {
    // Given
    ILoggingEvent event =
        logged(log -> log.atInfo().addKeyValue("event.outcome", (Object) null).log("No outcome"));

    // When
    JsonNode line = encode(event);

    // Then
    JsonNode outcome = line.path("event").path("outcome");
    assertThat(outcome.isMissingNode()).as("event.outcome is written").isFalse();
    assertThat(outcome.isNull()).isTrue();
  }

  @Test
  void encode_shouldWriteANonFiniteNumberAsAString() throws IOException {
    // Given
    ILoggingEvent event =
        logged(
            log ->
                log.atInfo()
                    .addKeyValue("event.duration", Double.NaN)
                    .addKeyValue("event.risk_score", Float.POSITIVE_INFINITY)
                    .log("Not a number"));

    // When: JSON has no NaN or Infinity, so a bare one would fail to parse here
    JsonNode line = encode(event);

    // Then
    assertThat(line.path("event").path("duration").asText()).isEqualTo("NaN");
    assertThat(line.path("event").path("risk_score").asText()).isEqualTo("Infinity");
  }

  private static ILoggingEvent logged(Consumer<Logger> logging) {
    return CapturedLogs.loggedBy(EcsEventEncoderTest.class, () -> logging.accept(LOG)).getFirst();
  }

  private JsonNode encode(ILoggingEvent event) throws IOException {
    return JSON.readTree(encoder.encode(event));
  }
}
