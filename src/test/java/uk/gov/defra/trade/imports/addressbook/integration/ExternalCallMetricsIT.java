package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs.keyValues;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedLogs.loggedBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.Encoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import uk.gov.defra.trade.imports.addressbook.metrics.ExternalCallMetrics;

class ExternalCallMetricsIT extends IntegrationBase {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private MeterRegistry meterRegistry;
  @Autowired private MongoTemplate mongoTemplate;

  @Test
  void aCreateRecordsAMongodbInsert() throws Exception {
    long before = durationCount("insert");

    createdAddressId(postAddress(addressBody()));

    assertThat(durationCount("insert")).isEqualTo(before + 1);
  }

  @Test
  void aFailedCommandIsRecordedAsAFailureOfItsOperation() {
    double before = failureTotal("find");

    assertThatExceptionOfType(DataAccessException.class)
        .isThrownBy(
            () ->
                mongoTemplate.executeCommand(
                    "{find: 'address', filter: {$notAnOperator: 1}}"));

    assertThat(failureTotal("find")).isEqualTo(before + 1);
  }

  @Test
  void theConsoleWritesTheCallLineWithTheTraceIdAndANestedEventObject() throws Exception {
    List<ILoggingEvent> lines =
        loggedBy(
            ExternalCallMetrics.class,
            () -> {
              try {
                mockMvc.perform(
                    postAddressRequest(addressBody().json())
                        .header("x-cdp-request-id", "trace-from-the-caller"));
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            });
    ILoggingEvent insert =
        lines.stream()
            .filter(line -> "mongodb/insert".equals(keyValues(line).get("event.action")))
            .findFirst()
            .orElseThrow();

    JsonNode encoded = JSON.readTree(consoleEncoder().encode(insert));

    assertThat(encoded.path("trace.id").asText()).isEqualTo("trace-from-the-caller");
    assertThat(encoded.path("event").path("category").asText()).isEqualTo("external-call");
    assertThat(encoded.path("event").path("action").asText()).isEqualTo("mongodb/insert");
    assertThat(encoded.path("event").path("outcome").asText()).isEqualTo("success");
    assertThat(encoded.path("event").path("duration").isIntegralNumber()).isTrue();
  }

  @SuppressWarnings("unchecked")
  private static Encoder<ILoggingEvent> consoleEncoder() {
    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    return ((OutputStreamAppender<ILoggingEvent>) root.getAppender("CONSOLE")).getEncoder();
  }

  private long durationCount(String operation) {
    var timer =
        meterRegistry
            .find("external.call.duration")
            .tags("dependency", "mongodb", "operation", operation)
            .timer();
    return timer == null ? 0 : timer.count();
  }

  private double failureTotal(String operation) {
    DistributionSummary summary =
        meterRegistry
            .find("external.call.failure")
            .tags("dependency", "mongodb", "operation", operation)
            .summary();
    return summary == null ? 0 : summary.totalAmount();
  }
}
