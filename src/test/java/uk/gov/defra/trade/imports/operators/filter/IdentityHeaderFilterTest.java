package uk.gov.defra.trade.imports.operators.filter;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.servlet.FilterChain;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link IdentityHeaderFilter} (cv-010).
 *
 * <p>The filter runs before {@code DispatcherServlet}, so {@code GlobalExceptionHandler}
 * (a {@code @RestControllerAdvice}) cannot format its failures — the filter must write the RFC 9457
 * {@code application/problem+json} bad-request body itself. These tests pin that self-written body,
 * the {@code Trade-Imports-Organisation-Id} required-on-every-operation rule, MDC scoping and the
 * no-PII-in-logs guarantee.
 */
class IdentityHeaderFilterTest {

  private static final String ORGANISATION_ID_HEADER = "Trade-Imports-Organisation-Id";
  private static final String MDC_ORGANISATION_ID = "organisationId";
  private static final String MDC_TRACE_ID = "trace.id";
  private static final String BAD_REQUEST_TYPE =
      "https://api.cdp.defra.cloud/problems/bad-request";

  private IdentityHeaderFilter filter;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper =
        new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
    filter = new IdentityHeaderFilter(objectMapper);
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void missingOrganisationId_writesBadRequestProblemAndHaltsTheChain() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/organisation/org-1/addresses");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.wasCalled()).isFalse();
    assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

    Map<String, Object> body = parseBody(response);
    assertThat(body).containsEntry("type", BAD_REQUEST_TYPE);
    assertThat(body).containsEntry("title", "Bad Request");
    assertThat(body).containsEntry("status", HttpStatus.BAD_REQUEST.value());
    assertThat(body.get("detail").toString()).contains(ORGANISATION_ID_HEADER);
    // The bad-request 400 shape carries NO errors map — a scoping failure is not field validation.
    assertThat(body).doesNotContainKey("errors");
    // Not 401/403 — those are outside the ruled status set.
    assertThat(response.getStatus()).isNotIn(401, 403);
  }

  @Test
  void blankOrganisationId_writesBadRequestProblem() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/organisation/org-1/addresses");
    request.addHeader(ORGANISATION_ID_HEADER, "   ");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.wasCalled()).isFalse();
    assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(parseBody(response)).containsEntry("type", BAD_REQUEST_TYPE).doesNotContainKey("errors");
  }

  @Test
  void organisationIdIsRequiredOnEveryOperation() throws Exception {
    for (String method : new String[] {"GET", "POST", "PUT", "DELETE"}) {
      MockHttpServletRequest request =
          new MockHttpServletRequest(method, "/organisation/org-1/addresses/665f1c2ab3e4d51a2c9d0e77");
      MockHttpServletResponse response = new MockHttpServletResponse();
      RecordingChain chain = new RecordingChain();

      filter.doFilter(request, response, chain);

      assertThat(chain.wasCalled())
          .as("chain must halt for %s without the org-id header", method)
          .isFalse();
      assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(parseBody(response).get("detail").toString()).contains(ORGANISATION_ID_HEADER);
    }
  }

  @Test
  void validHeader_proceedsAndOrganisationIdLandsInMdcDuringTheChain() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/organisation/org-1/addresses");
    request.addHeader(ORGANISATION_ID_HEADER, "org-42");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.wasCalled()).isTrue();
    assertThat(chain.mdcDuringChain()).containsEntry(MDC_ORGANISATION_ID, "org-42");
  }

  @Test
  void badRequestBodyCarriesTraceIdFromMdc() throws Exception {
    MDC.put(MDC_TRACE_ID, "trace-abc-123");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/organisation/org-1/addresses");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new RecordingChain());

    assertThat(parseBody(response)).containsEntry("traceId", "trace-abc-123");
  }

  @Test
  void nonOrganisationPathsBypassTheFilterEntirely() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.wasCalled()).isTrue();
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void addsOnlyOrganisationIdToTheLoggingContext_noPiiFieldValues() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/organisation/org-1/addresses");
    request.addHeader(ORGANISATION_ID_HEADER, "org-42");
    // PII-shaped body must never surface in the logging context.
    request.setContent("{\"name\":\"Highland Livestock Ltd\",\"email\":\"secret@example.com\"}".getBytes());
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    Logger filterLogger = (Logger) LoggerFactory.getLogger(IdentityHeaderFilter.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    filterLogger.addAppender(appender);
    try {
      filter.doFilter(request, response, chain);
    } finally {
      filterLogger.detachAppender(appender);
    }

    // The only diagnostic key the filter contributes is organisationId — never a name, email or
    // address value.
    assertThat(chain.mdcDuringChain()).containsOnlyKeys(MDC_ORGANISATION_ID);
    assertThat(appender.list)
        .allSatisfy(
            event -> {
              assertThat(event.getFormattedMessage()).doesNotContain("Highland Livestock Ltd");
              assertThat(event.getFormattedMessage()).doesNotContain("secret@example.com");
            });
  }

  private Map<String, Object> parseBody(MockHttpServletResponse response) throws Exception {
    @SuppressWarnings("unchecked")
    Map<String, Object> body = objectMapper.readValue(response.getContentAsString(), Map.class);
    return body;
  }

  /** Records whether the chain was invoked and snapshots the MDC at the moment it was. */
  private static final class RecordingChain implements FilterChain {
    private final AtomicBoolean called = new AtomicBoolean(false);
    private final Map<String, String> mdcSnapshot = new HashMap<>();

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
      called.set(true);
      Map<String, String> copy = MDC.getCopyOfContextMap();
      if (copy != null) {
        mdcSnapshot.putAll(copy);
      }
      if (response instanceof MockHttpServletResponse mock) {
        mock.setStatus(HttpStatus.OK.value());
      }
    }

    boolean wasCalled() {
      return called.get();
    }

    Map<String, String> mdcDuringChain() {
      return mdcSnapshot;
    }
  }
}
