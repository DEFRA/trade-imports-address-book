package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.defra.trade.imports.addressbook.testsupport.CapturedConsole.captureSystemOut;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import uk.gov.defra.trade.imports.addressbook.testsupport.CapturedConsole;

class EcsLoggingIT extends IntegrationBase {

  @Test
  void requestLogging_shouldProduceEcsJsonFormat() throws Exception {
    CapturedConsole console = captureSystemOut();
    try (console) {
      mockMvc.perform(get("/actuator/info")
              .header("x-cdp-request-id", "test-trace-123"))
          .andExpect(status().isNotFound());
    }

    JsonNode logJson = requestLogLine(console);

    assertThat(logJson.has("@timestamp")).isTrue();
    assertThat(logJson.has("log.level")).isTrue();
    assertThat(logJson.has("message")).isTrue();

    assertThat(logJson.has("trace.id")).isTrue();
    assertThat(logJson.get("trace.id").asText()).isEqualTo("test-trace-123");

    assertThat(logJson.has("http.request.method")).isTrue();
    assertThat(logJson.get("http.request.method").asText()).isEqualTo("GET");

    assertThat(logJson.has("url.full")).isTrue();
    assertThat(logJson.get("url.full").asText()).contains("/actuator/info");

    assertThat(logJson.has("service.name")).isTrue();
    assertThat(logJson.has("service.version")).isTrue();
  }

  @Test
  void healthEndpoint_shouldBeFilteredFromLogs() throws Exception {
    CapturedConsole console = captureSystemOut();
    try (console) {
      mockMvc.perform(get("/health"))
          .andExpect(status().isOk());
    }

    assertThat(console.text()).doesNotContain("/health");
  }

  @Test
  void missingTraceIdHeader_shouldLogTheRequestWithoutTraceId() throws Exception {
    CapturedConsole console = captureSystemOut();
    try (console) {
      mockMvc.perform(get("/actuator/info"))
          .andExpect(status().isNotFound());
    }

    JsonNode logJson = requestLogLine(console);

    assertThat(logJson.get("url.full").asText()).contains("/actuator/info");
    assertThat(logJson.has("trace.id")).isFalse();
  }

  private static JsonNode requestLogLine(CapturedConsole console) {
    return console.requiredFirstJsonLine(line -> line.has("url.full"), "has url.full");
  }
}
