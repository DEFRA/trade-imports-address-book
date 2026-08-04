package uk.gov.defra.trade.imports.addressbook.exceptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
 * field name; a {@link BadRequestException} (e.g. out-of-range {@code page}) carries
 * carries <strong>no</strong> {@code errors} key at all. That is why the contract declares POST/PUT
 * 400 as {@code anyOf(ValidationProblem, Problem)} rather than {@code oneOf} (design §1.6).
 *
 * <p>Responses use the {@link Problem} / {@link ValidationProblem} records rather than Spring's
 * {@code ProblemDetail} so the wire body omits {@code instance} — a request-scoped URI would leak
 * path ids and break the cross-org 404 indistinguishability contract (cv-040).
 *
 * <p>The wire is camelCase (cv-001), so an error-map key is the rejected field's Java name
 * verbatim ({@code addressLine1}) — the same name the frontend error mapping expects.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  private static final String MDC_TRACE_ID = "trace.id";
  private static final String PROBLEM_BASE = "https://api.cdp.defra.cloud/problems/";

  /** Field validation failure — 400 validation-error, WITH a per-field camelCase errors map. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ValidationProblem> handleValidationException(
      MethodArgumentNotValidException ex) {
    String traceId = MDC.get(MDC_TRACE_ID);
    log.warn("Validation error (trace: {}): {}", traceId, ex.getMessage());

    Map<String, List<String>> errors = new LinkedHashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors
          .computeIfAbsent(error.getField(), key -> new ArrayList<>())
          .add(error.getDefaultMessage());
    }

    return problemResponse(
        HttpStatus.BAD_REQUEST,
        new ValidationProblem(
            PROBLEM_BASE + "validation-error",
            "Validation Error",
            HttpStatus.BAD_REQUEST.value(),
            "Validation failed for one or more fields",
            traceId,
            errors));
  }

  /**
   * A query/path parameter that could not be bound to its target type (e.g. a non-numeric
   * {@code page} or {@code page_size}) — 400 bad-request, NO errors map. Without this, Spring's
   * {@link MethodArgumentTypeMismatchException} would fall through to the 500 handler.
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<Problem> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    String traceId = MDC.get(MDC_TRACE_ID);
    log.warn("Malformed parameter (trace: {}): {}", traceId, ex.getMessage());

    return problemResponse(
        HttpStatus.BAD_REQUEST,
        problem(
            HttpStatus.BAD_REQUEST,
            "bad-request",
            "Bad Request",
            "Invalid value for parameter '" + ex.getName() + "'",
            traceId));
  }

  /** Malformed JSON request body — 400 bad-request, NO errors map. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Problem> handleMessageNotReadable(HttpMessageNotReadableException ex) {
    String traceId = MDC.get(MDC_TRACE_ID);
    log.warn("Malformed request body (trace: {}): {}", traceId, ex.getMessage());

    return problemResponse(
        HttpStatus.BAD_REQUEST,
        problem(
            HttpStatus.BAD_REQUEST,
            "bad-request",
            "Bad Request",
            "Malformed JSON request body",
            traceId));
  }

  /** Concurrent update conflict — 409 conflict. */
  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<Problem> handleOptimisticLocking(OptimisticLockingFailureException ex) {
    String traceId = MDC.get(MDC_TRACE_ID);
    log.warn("Optimistic locking failure (trace: {}): {}", traceId, ex.getMessage());

    return problemResponse(
        HttpStatus.CONFLICT,
        problem(
            HttpStatus.CONFLICT,
            "conflict",
            "Conflict",
            "The resource was modified by another request",
            traceId));
  }

  /** Malformed request that never reached body validation — 400 bad-request, NO errors map. */
  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<Problem> handleBadRequestException(BadRequestException ex) {
    String traceId = MDC.get(MDC_TRACE_ID);
    log.warn("Bad request (trace: {}): {}", traceId, ex.getMessage());

    return problemResponse(
        HttpStatus.BAD_REQUEST,
        problem(HttpStatus.BAD_REQUEST, "bad-request", "Bad Request", ex.getMessage(), traceId));
  }

  /** Unknown / cross-org id, or a PUT on a tombstone — 404 not-found. */
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Problem> handleNotFoundException(NotFoundException ex) {
    String traceId = MDC.get(MDC_TRACE_ID);
    log.warn("Resource not found (trace: {}): {}", traceId, ex.getMessage());

    return problemResponse(
        HttpStatus.NOT_FOUND,
        problem(
            HttpStatus.NOT_FOUND, "not-found", "Resource Not Found", ex.getMessage(), traceId));
  }

  /**
   * Unexpected error — 500 internal-error. Does not catch Spring framework exceptions (e.g. a 404
   * for a missing route), which Spring maps itself.
   */
  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<Problem> handleException(RuntimeException ex) {
    String traceId = MDC.get(MDC_TRACE_ID);
    log.error("Unexpected error (trace: {}): {}", traceId, ex.getMessage(), ex);

    return problemResponse(
        HttpStatus.INTERNAL_SERVER_ERROR,
        problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal-error",
            "Internal Server Error",
            "An unexpected error occurred. Please try again later.",
            traceId));
  }

  private Problem problem(
      HttpStatus status, String typeSlug, String title, String detail, String traceId) {
    return new Problem(PROBLEM_BASE + typeSlug, title, status.value(), detail, traceId);
  }

  private <T> ResponseEntity<T> problemResponse(HttpStatus status, T body) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(body);
  }
}
