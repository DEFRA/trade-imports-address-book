package uk.gov.defra.trade.imports.addressbook.filter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.gov.defra.trade.imports.addressbook.exceptions.Problem;

/**
 * Enforces the trusted-forwarded-header identity contract on every {@code /organisation/**} request
 * (cv-010).
 *
 * <p>{@code Trade-Imports-Organisation-Id} is required on every operation — missing, blank or
 * malformed values fail fast with a 400 bad-request problem. When the path carries an {@code orgId}
 * segment it must equal the header value; mismatch returns 404 (no existence disclosure).
 *
 * <p>The header is trusted only when set or overwritten by the CDP ingress or calling BFF — see
 * README "Identity and trust boundary".
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class IdentityHeaderFilter extends OncePerRequestFilter {

  public static final String ORGANISATION_ID_HEADER = "Trade-Imports-Organisation-Id";
  static final String MDC_ORGANISATION_ID = "organisationId";

  private static final String MDC_TRACE_ID = "trace.id";
  private static final String BAD_REQUEST_TYPE =
      "https://api.cdp.defra.cloud/problems/bad-request";
  private static final String NOT_FOUND_TYPE = "https://api.cdp.defra.cloud/problems/not-found";
  private static final Pattern ORGANISATION_ID_VALUE =
      Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

  private final ObjectWriter problemWriter;

  public IdentityHeaderFilter(ObjectMapper objectMapper) {
    this.problemWriter =
        objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL).writer();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String servletPath = request.getServletPath();
    return servletPath == null || !servletPath.startsWith("/organisation/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String organisationId = request.getHeader(ORGANISATION_ID_HEADER);
    if (isBlank(organisationId)) {
      reject(response, HttpStatus.BAD_REQUEST, BAD_REQUEST_TYPE, "Bad Request", "Missing required header " + ORGANISATION_ID_HEADER);
      return;
    }
    if (!ORGANISATION_ID_VALUE.matcher(organisationId).matches()) {
      reject(
          response,
          HttpStatus.BAD_REQUEST,
          BAD_REQUEST_TYPE,
          "Bad Request",
          "Invalid " + ORGANISATION_ID_HEADER);
      return;
    }

    String pathOrgId = extractPathOrganisationId(request.getServletPath());
    if (pathOrgId != null && !organisationId.equals(pathOrgId)) {
      reject(
          response,
          HttpStatus.NOT_FOUND,
          NOT_FOUND_TYPE,
          "Resource Not Found",
          "Address not found");
      return;
    }

    try {
      MDC.put(MDC_ORGANISATION_ID, organisationId);
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_ORGANISATION_ID);
    }
  }

  static String extractPathOrganisationId(String servletPath) {
    if (servletPath == null || !servletPath.startsWith("/organisation/")) {
      return null;
    }
    int start = "/organisation/".length();
    int slash = servletPath.indexOf('/', start);
    return slash < 0 ? servletPath.substring(start) : servletPath.substring(start, slash);
  }

  private void reject(
      HttpServletResponse response,
      HttpStatus status,
      String type,
      String title,
      String detail)
      throws IOException {
    String traceId = MDC.get(MDC_TRACE_ID);
    log.warn("Rejected /organisation request: {} (trace: {})", detail, traceId);

    Problem problem = new Problem(type, title, status.value(), detail, traceId);

    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getOutputStream().write(problemWriter.writeValueAsBytes(problem));
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
