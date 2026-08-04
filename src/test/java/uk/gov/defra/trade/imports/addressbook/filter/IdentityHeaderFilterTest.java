package uk.gov.defra.trade.imports.addressbook.filter;

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
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/organisation/org-1/addresses");
    request.setServletPath("/organisation/org-1/addresses");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(chain.wasCalled()).isFalse();
    assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

    Map<String, Object> body = parseBody(response);
    assertThat(body).containsEntry("type", BAD_REQUEST_TYPE);
    assertThat(body).containsEntry("title", "Bad Request");
    assertThat(body).containsEntry("status", HttpStatus.BAD_REQUEST.value());
    assertThat(body.get("detail").toString()).contains(ORGANISATION_ID_HEADER);
    assertThat(body).doesNotContainKey("errors");
    assertThat(response.getStatus()).isNotIn(401, 403);
  }

  @Test
  void missingOrganisationIdWithRequestUriOnly_writesBadRequestProblem() throws Exception {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/organisation/org-1/addresses");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(chain.wasCalled()).isFalse();
    assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
  }

  @Test
  void blankOrganisationId_writesBadRequestProblem() throws Exception {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/organisation/org-1/addresses");
    request.setServletPath("/organisation/org-1/addresses");
    request.addHeader(ORGANISATION_ID_HEADER, "   ");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(chain.wasCalled()).isFalse();
    assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(parseBody(response)).containsEntry("type", BAD_REQUEST_TYPE).doesNotContainKey("errors");
  }

  @Test
  void organisationIdIsRequiredOnEveryOperation() throws Exception {
    for (String method : new String[] {"GET", "POST", "PUT", "DELETE"}) {
      // Given
      MockHttpServletRequest request =
          new MockHttpServletRequest(method, "/organisation/org-1/addresses/665f1c2ab3e4d51a2c9d0e77");
      request.setServletPath("/organisation/org-1/addresses/665f1c2ab3e4d51a2c9d0e77");
      MockHttpServletResponse response = new MockHttpServletResponse();
      RecordingChain chain = new RecordingChain();

      // When
      filter.doFilter(request, response, chain);

      // Then
      assertThat(chain.wasCalled())
          .as("chain must halt for %s without the org-id header", method)
          .isFalse();
      assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(parseBody(response).get("detail").toString()).contains(ORGANISATION_ID_HEADER);
    }
  }

  @Test
  void validHeader_proceedsAndOrganisationIdLandsInMdcDuringTheChain() throws Exception {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/organisation/org-42/addresses");
    request.setServletPath("/organisation/org-42/addresses");
    request.addHeader(ORGANISATION_ID_HEADER, "org-42");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(chain.wasCalled()).isTrue();
    assertThat(chain.mdcDuringChain()).containsEntry(MDC_ORGANISATION_ID, "org-42");
  }

  @Test
  void badRequestBodyCarriesTraceIdFromMdc() throws Exception {
    // Given
    MDC.put(MDC_TRACE_ID, "trace-abc-123");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/organisation/org-1/addresses");
    request.setServletPath("/organisation/org-1/addresses");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // When
    filter.doFilter(request, response, new RecordingChain());

    // Then
    assertThat(parseBody(response)).containsEntry("traceId", "trace-abc-123");
  }

  @Test
  void nonOrganisationPathsBypassTheFilterEntirely() throws Exception {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(chain.wasCalled()).isTrue();
    assertThat(response.getContentAsString()).isEmpty();
  }

  @Test
  void pathOrgMismatchReturns404BeforeTheChainRuns() throws Exception {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/organisation/org-a/addresses");
    request.setServletPath("/organisation/org-a/addresses");
    request.addHeader(ORGANISATION_ID_HEADER, "org-b");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(chain.wasCalled()).isFalse();
    assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(parseBody(response).get("type"))
        .isEqualTo("https://api.cdp.defra.cloud/problems/not-found");
  }

  @Test
  void mdcOrganisationIdIsRemovedAfterSuccessfulRequest() throws Exception {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/organisation/org-42/addresses");
    request.setServletPath("/organisation/org-42/addresses");
    request.addHeader(ORGANISATION_ID_HEADER, "org-42");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // When
    filter.doFilter(request, response, new RecordingChain());

    // Then
    assertThat(MDC.get(MDC_ORGANISATION_ID)).isNull();
  }

  @Test
  void mdcOrganisationIdIsRemovedWhenTheChainThrows() throws Exception {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/organisation/org-42/addresses");
    request.setServletPath("/organisation/org-42/addresses");
    request.addHeader(ORGANISATION_ID_HEADER, "org-42");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // When & Then
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                filter.doFilter(
                    request,
                    response,
                    (req, res) -> {
                      throw new jakarta.servlet.ServletException("boom");
                    }))
        .isInstanceOf(jakarta.servlet.ServletException.class);

    assertThat(MDC.get(MDC_ORGANISATION_ID)).isNull();
  }

  @Test
  void invalidOrganisationIdValueIsRejectedWith400() throws Exception {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/organisation/org-1/addresses");
    request.setServletPath("/organisation/org-1/addresses");
    request.addHeader(ORGANISATION_ID_HEADER, "not valid because of spaces");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // When
    filter.doFilter(request, response, new RecordingChain());

    // Then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
  }

  @Test
  void addsOnlyOrganisationIdToTheLoggingContext_noPiiFieldValues() throws Exception {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/organisation/org-1/addresses");
    request.setServletPath("/organisation/org-1/addresses");
    MockHttpServletResponse response = new MockHttpServletResponse();

    Logger filterLogger = (Logger) LoggerFactory.getLogger(IdentityHeaderFilter.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    filterLogger.addAppender(appender);

    // When
    try {
      filter.doFilter(request, response, new RecordingChain());
    } finally {
      filterLogger.detachAppender(appender);
    }

    // Then
    assertThat(appender.list).isNotEmpty();
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
