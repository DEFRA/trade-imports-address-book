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
 * <p>{@code Trade-Imports-Organisation-Id} is the sole tenant key for org-scoped reads and writes.
 * Missing, blank or malformed values fail fast with a 400 bad-request problem. When the path carries
 * an {@code orgId} segment it must equal the header value; mismatch returns 404 (no existence
 * disclosure).
 *
 * <h2>Trust boundary (ADR — no in-service authentication)</h2>
 *
 * <p>This service does <strong>not</strong> implement Spring Security or validate JWTs. It reads
 * {@code Trade-Imports-Organisation-Id} directly from the inbound HTTP request and treats it as the
 * authenticated organisation id. That is safe only when an upstream component has already
 * established caller identity and either stripped any client-supplied copy of the header or
 * overwritten it with the value from the authenticated session.
 *
 * <p><strong>Required upstream behaviour (production):</strong>
 *
 * <ul>
 *   <li>The CDP API gateway / ingress, or the calling BFF (e.g. ins-frontend), MUST authenticate
 *       the caller (Defra ID / OIDC).
 *   <li>That hop MUST remove inbound {@code Trade-Imports-Organisation-Id} values from untrusted
 *       clients and set the header from the verified session's organisation id before forwarding to
 *       this service.
 *   <li>Network policy MUST prevent direct, unauthenticated access to this service's port in
 *       deployed environments — callers that can reach the pod without passing through the gateway
 *       can assert any organisation id.
 * </ul>
 *
 * <p>Filter-layer rejections write {@code application/problem+json} directly because {@link
 * org.springframework.web.bind.annotation.RestControllerAdvice} does not handle exceptions thrown
 * from servlet filters. Do not throw {@link uk.gov.defra.trade.imports.addressbook.exceptions.BadRequestException}
 * here expecting {@link uk.gov.defra.trade.imports.addressbook.exceptions.GlobalExceptionHandler} to
 * map it.
 *
 * <p>See README "Security and trust boundary" for the operational contract and ownership.
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
    return !isOrganisationPath(organisationRequestPath(request));
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

    String pathOrgId = extractPathOrganisationId(organisationRequestPath(request));
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

  static String extractPathOrganisationId(String path) {
    if (path == null || !path.startsWith("/organisation/")) {
      return null;
    }
    int start = "/organisation/".length();
    int slash = path.indexOf('/', start);
    return slash < 0 ? path.substring(start) : path.substring(start, slash);
  }

  /**
   * Resolves the request path for org-scoping. Production requests populate {@code servletPath};
   * MockMvc and some proxies leave it empty and put the path on {@code requestURI} instead.
   */
  static String organisationRequestPath(HttpServletRequest request) {
    String servletPath = request.getServletPath();
    if (servletPath != null && !servletPath.isEmpty()) {
      return servletPath;
    }
    String uri = request.getRequestURI();
    if (uri == null) {
      return "";
    }
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
      return uri.substring(contextPath.length());
    }
    return uri;
  }

  private static boolean isOrganisationPath(String path) {
    return path != null && path.startsWith("/organisation/");
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
