package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedConsole.captureSystemOut;
import static uk.gov.defra.trade.imports.addressbook.testsupport.ExternalCallMeters.durationCount;
import static uk.gov.defra.trade.imports.addressbook.testsupport.ExternalCallMeters.failureTotal;

import com.fasterxml.jackson.databind.JsonNode;
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

  @Autowired private MeterRegistry meterRegistry;
  @Autowired private MongoTemplate mongoTemplate;

  @Test
  void aCreateRecordsAMongodbInsert() throws Exception {
    long before = durationCount(meterRegistry, "mongodb", "insert");

    createdResourceId(postAddress(addressBody()));

    assertThat(durationCount(meterRegistry, "mongodb", "insert")).isEqualTo(before + 1);
  }

  @Test
  void aFailedCommandIsRecordedAsAFailureOfItsOperation() {
    double before = failureTotal(meterRegistry, "mongodb", "find");

    assertThatExceptionOfType(DataAccessException.class)
        .isThrownBy(
            () ->
                mongoTemplate.executeCommand(
                    "{find: 'address', filter: {$notAnOperator: 1}}"));

    assertThat(failureTotal(meterRegistry, "mongodb", "find")).isEqualTo(before + 1);
  }

  @Test
  void aDuplicateKeyInsertIsRecordedAsAFailureOfInsert() {
    Document address = new Document("_id", new ObjectId());
    mongoTemplate.insert(address, "address");
    double before = failureTotal(meterRegistry, "mongodb", "insert");

    assertThatExceptionOfType(DuplicateKeyException.class)
        .isThrownBy(() -> mongoTemplate.insert(address, "address"));

    assertThat(failureTotal(meterRegistry, "mongodb", "insert")).isEqualTo(before + 1);
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
  void theConsoleWritesAFailedCallLineWithTheErrorTypeAndMessageButNoStackTrace() {
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

  private static JsonNode consoleLineWithAction(String action, CapturedConsole console) {
    return console.requiredFirstJsonLine(
        line -> action.equals(line.path("event").path("action").asText()),
        "has event.action " + action);
  }
}
