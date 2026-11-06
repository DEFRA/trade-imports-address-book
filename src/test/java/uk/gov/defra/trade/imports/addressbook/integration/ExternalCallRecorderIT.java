package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedConsole.captureSystemOut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import uk.gov.defra.trade.imports.addressbook.testsupport.CapturedConsole;

class ExternalCallRecorderIT extends IntegrationBase {

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
  void aDuplicateKeyInsertIsRecordedAsAFailureOfInsert() {
    Document address = new Document("_id", new ObjectId());
    mongoTemplate.insert(address, "address");
    double before = failureTotal("insert");

    assertThatExceptionOfType(DuplicateKeyException.class)
        .isThrownBy(() -> mongoTemplate.insert(address, "address"));

    assertThat(failureTotal("insert")).isEqualTo(before + 1);
  }

  @Test
  void theConsoleWritesTheCallLineWithTheTraceIdAndANestedEventObject() throws Exception {
    CapturedConsole console = captureSystemOut();
    try (console) {
      mockMvc.perform(
          postAddressRequest(addressBody().json())
              .header("x-cdp-request-id", "trace-from-the-caller"));
    }

    JsonNode insert = consoleLineWithAction("mongodb/insert", console);

    assertThat(insert.path("trace.id").asText()).isEqualTo("trace-from-the-caller");
    assertThat(insert.path("event").path("category").asText()).isEqualTo("external-call");
    assertThat(insert.path("event").path("outcome").asText()).isEqualTo("success");
    assertThat(insert.path("event").path("duration").isIntegralNumber()).isTrue();
  }

  @Test
  void theConsoleWritesAFailedCallLineWithTheErrorTypeAndMessageButNoStackTrace()
      throws Exception {
    CapturedConsole console = captureSystemOut();
    try (console) {
      assertThatExceptionOfType(DataAccessException.class)
          .isThrownBy(
              () ->
                  mongoTemplate.executeCommand(
                      "{find: 'address', filter: {$notAnOperator: 1}}"));
    }

    JsonNode failedFind = consoleLineWithAction("mongodb/find", console);

    assertThat(failedFind.path("event").path("outcome").asText()).isEqualTo("failure");
    assertThat(failedFind.path("error.type").asText())
        .isEqualTo("com.mongodb.MongoCommandException");
    assertThat(failedFind.path("error.message").asText()).contains("$notAnOperator");
    assertThat(failedFind.has("error.stack_trace")).isFalse();
  }

  private static JsonNode consoleLineWithAction(String action, CapturedConsole console)
      throws Exception {
    for (String line : console.text().split("\n")) {
      if (line.trim().startsWith("{")) {
        JsonNode json = JSON.readTree(line);
        if (action.equals(json.path("event").path("action").asText())) {
          return json;
        }
      }
    }
    throw new AssertionError("No console line has event.action " + action);
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
