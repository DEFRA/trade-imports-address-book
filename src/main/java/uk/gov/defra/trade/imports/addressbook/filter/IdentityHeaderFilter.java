package uk.gov.defra.trade.imports.operators.filter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import uk.gov.defra.trade.imports.operators.exceptions.Problem;

/**
 * Enforces the trusted-forwarded-header identity contract on every {@code /organisation/**} request
 * (cv-010).
 *
 * <p>{@code Trade-Imports-Organisation-Id} is required on every operation — missing or blank fails
 * fast and loud, so a scoping bug can never degrade into "all organisations see all addresses". The
 * failure produces a 400 bad-request problem (RFC 9457 {@code application/problem+json}, no
 * {@code errors} map): the absence of a header is not a body-field validation failure, so there is
 * nothing to key an errors map on.
 *
 * <p>This filter runs before {@code DispatcherServlet}, so {@code GlobalExceptionHandler}
 * (a {@code @RestControllerAdvice}) never sees exceptions thrown here. The filter therefore writes
 * the problem+json body itself rather than delegating to the advice.
 *
 * <p>On success it stashes {@code organisationId} in the MDC so every log line for the request
 * carries {@code organisationId} + {@code trace.id} — the two keys needed to debug any "whose data
 * is this" incident. Address and other PII field values are never logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class IdentityHeaderFilter implements Filter {

  static final String ORGANISATION_ID_HEADER = "Trade-Imports-Organisation-Id";
  static final String MDC_ORGANISATION_ID = "organisationId";

  private static final String MDC_TRACE_ID = "trace.id";
  private static final String PATH_PREFIX = "/organisation";
  private static final String BAD_REQUEST_TYPE =
      "https://api.cdp.defra.cloud/problems/bad-request";
  private static final String BAD_REQUEST_TITLE = "Bad Request";

  private final ObjectWriter problemWriter;

  public IdentityHeaderFilter(ObjectMapper objectMapper) {
    this.problemWriter =
        objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL).writer();
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    if (!(request instanceof HttpServletRequest httpRequest)
        || !(response instanceof HttpServletResponse httpResponse)
        || !httpRequest.getRequestURI().startsWith(PATH_PREFIX)) {
      chain.doFilter(request, response);
      return;
    }

    String organisationId = httpRequest.getHeader(ORGANISATION_ID_HEADER);
    if (isBlank(organisationId)) {
      reject(httpResponse, "Missing required header " + ORGANISATION_ID_HEADER);
      return;
    }

    try {
      MDC.put(MDC_ORGANISATION_ID, organisationId);
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_ORGANISATION_ID);
    }
  }

  private void reject(HttpServletResponse response, String detail) throws IOException {
    String traceId = MDC.get(MDC_TRACE_ID);
    log.warn("Rejected /organisation request: {} (trace: {})", detail, traceId);

    Problem problem =
        new Problem(BAD_REQUEST_TYPE, BAD_REQUEST_TITLE, HttpStatus.BAD_REQUEST.value(), detail, traceId);

    response.setStatus(HttpStatus.BAD_REQUEST.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getOutputStream().write(problemWriter.writeValueAsBytes(problem));
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
