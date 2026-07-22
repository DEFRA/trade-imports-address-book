package uk.gov.defra.trade.imports.operators.exceptions;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Global exception handler producing RFC 9457 {@code application/problem+json} responses, CDP
 * problem-family type URIs and a camelCase {@code traceId} extension (design §1.4, §5).
 *
 * <p>The two 400 shapes are deliberately distinct and must not be conflated: a field-validation
 * failure ({@link MethodArgumentNotValidException}) carries an {@code errors} map keyed by wire
 * field name; a {@link BadRequestException} (missing identity header, malformed query param)
 * carries <strong>no</strong> {@code errors} key at all. That is why the contract declares POST/PUT
 * 400 as {@code anyOf(ValidationProblem, Problem)} rather than {@code oneOf} (design §1.6).
 *
 * <p>The wire is camelCase (cv-001), so an error-map key is the rejected field's Java name
 * verbatim ({@code addressLine1}) — the same name the frontend error mapping expects.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  private static final String MDC_TRACE_ID = "trace.id";
  private static final String TRACE_ID_PROPERTY = "traceId";
  private static final String PROBLEM_BASE = "https://api.cdp.defra.cloud/problems/";

  /** Field validation failure — 400 validation-error, WITH a per-field camelCase errors map. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidationException(
      MethodArgumentNotValidException ex) {
    String traceId = MDC.get(MDC_TRACE_ID);
    log.warn("Validation error (trace: {}): {}", traceId, ex.getMessage());

    ProblemDetail problem =
        problemDetail(
            HttpStatus.BAD_REQUEST,
            "validation-error",
            "Validation Error",
            "Validation failed for one or more fields",
            traceId);

    Map<String, List<String>> errors = new LinkedHashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors
          .computeIfAbsent(error.getField(), key -> new ArrayList<>())
          .add(error.getDefaultMessage());
    }
    problem.setProperty("errors", errors);

    return problemResponse(HttpStatus.BAD_REQUEST, problem);
  }

  /**
   * A query/path parameter that could not be bound to its target type (e.g. a non-numeric
   * {@code page} or {@code page_size}) — 400 bad-request, NO errors map. Without this, Spring's
   * {@link MethodArgumentTypeMismatchException} would fall through to the 500 handler.
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    String traceId = MDC.get(MDC_TRACE_ID);
    log.warn("Malformed parameter (trace: {}): {}", traceId, ex.getMessage());

    ProblemDetail problem =
        problemDetail(
            HttpStatus.BAD_REQUEST,
            "bad-request",
            "Bad Request",
            "Invalid value for parameter '" + ex.getName() + "'",
            traceId);

    return problemResponse(HttpStatus.BAD_REQUEST, problem);
  }

  /** Malformed request that never reached body validation — 400 bad-request, NO errors map. */
  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ProblemDetail> handleBadRequestException(BadRequestException ex) {
    String traceId = MDC.get(MDC_TRACE_ID);
    log.warn("Bad request (trace: {}): {}", traceId, ex.getMessage());

    ProblemDetail problem =
        problemDetail(
            HttpStatus.BAD_REQUEST, "bad-request", "Bad Request", ex.getMessage(), traceId);

    return problemResponse(HttpStatus.BAD_REQUEST, problem);
  }

  /** Unknown / cross-org id, or a PUT on a tombstone — 404 not-found. */
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFoundException(NotFoundException ex) {
    String traceId = MDC.get(MDC_TRACE_ID);
    log.warn("Resource not found (trace: {}): {}", traceId, ex.getMessage());

    ProblemDetail problem =
        problemDetail(
            HttpStatus.NOT_FOUND, "not-found", "Resource Not Found", ex.getMessage(), traceId);

    return problemResponse(HttpStatus.NOT_FOUND, problem);
  }

  /**
   * Unexpected error — 500 internal-error. Does not catch Spring framework exceptions (e.g. a 404
   * for a missing route), which Spring maps itself.
   */
  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ProblemDetail> handleException(RuntimeException ex) {
    String traceId = MDC.get(MDC_TRACE_ID);
    log.error("Unexpected error (trace: {}): {}", traceId, ex.getMessage(), ex);

    ProblemDetail problem =
        problemDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal-error",
            "Internal Server Error",
            "An unexpected error occurred. Please try again later.",
            traceId);

    return problemResponse(HttpStatus.INTERNAL_SERVER_ERROR, problem);
  }

  private ProblemDetail problemDetail(
      HttpStatus status, String typeSlug, String title, String detail, String traceId) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(PROBLEM_BASE + typeSlug));
    problem.setTitle(title);
    if (traceId != null) {
      problem.setProperty(TRACE_ID_PROPERTY, traceId);
    }
    return problem;
  }

  private ResponseEntity<ProblemDetail> problemResponse(HttpStatus status, ProblemDetail problem) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }
}
