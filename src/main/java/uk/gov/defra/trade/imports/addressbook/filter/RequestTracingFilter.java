package uk.gov.defra.trade.imports.addressbook.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates MDC with request tracing metadata for ECS structured logging.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestTracingFilter extends OncePerRequestFilter {

  private static final String MDC_TRACE_ID = "trace.id";
  private static final String MDC_HTTP_METHOD = "http.request.method";
  private static final String MDC_URL_FULL = "url.full";

  private final String header;

  RequestTracingFilter(@Value("${cdp.tracing.header-name}") String header) {
    this.header = header;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      String traceId = request.getHeader(header);
      if (traceId != null && !traceId.isBlank()) {
        MDC.put(MDC_TRACE_ID, traceId);
      }
      MDC.put(MDC_HTTP_METHOD, request.getMethod());
      MDC.put(MDC_URL_FULL, request.getRequestURL().toString());

      chain.doFilter(request, response);

      log.debug(
          "request completed method={} url={} status={}",
          request.getMethod(),
          request.getRequestURL(),
          response.getStatus());
    } finally {
      MDC.clear();
    }
  }
}
