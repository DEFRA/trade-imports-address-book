package uk.gov.defra.trade.imports.operators.exceptions;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.NamingBase;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
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
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Global exception handler producing RFC 9457 {@code application/problem+json} responses, CDP
 * problem-family type URIs and a snake_case {@code trace_id} extension (design §1.4, §5).
 *
 * <p>The two 400 shapes are deliberately distinct and must not be conflated: a field-validation
 * failure ({@link MethodArgumentNotValidException}) carries an {@code errors} map keyed by wire
 * field name; a {@link BadRequestException} (missing identity header, malformed query param)
 * carries <strong>no</strong> {@code errors} key at all. That is why the contract declares POST/PUT
 * 400 as {@code anyOf(ValidationProblem, Problem)} rather than {@code oneOf} (design §1.6).
 *
 * <p>Error-map keys are resolved to their <em>wire</em> name through the configured
 * {@link ObjectMapper}, not a blind snake-case of the Java identifier: the naming strategy renders
 * {@code addressLine1} as {@code address_line1}, but the contract — and the frontend error mapping —
 * require {@code address_line_1}, which the DTO pins with an explicit {@code @JsonProperty}.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  private static final String MDC_TRACE_ID = "trace.id";
  private static final String TRACE_ID_PROPERTY = "trace_id";
  private static final String PROBLEM_BASE = "https://api.cdp.defra.cloud/problems/";
  private static final NamingBase SNAKE_CASE =
      (NamingBase) PropertyNamingStrategies.SNAKE_CASE;

  private final ObjectMapper objectMapper;

  public GlobalExceptionHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Field validation failure — 400 validation-error, WITH a per-field snake_case errors map. */
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

    BindingResult bindingResult = ex.getBindingResult();
    Map<String, List<String>> errors = new LinkedHashMap<>();
    for (FieldError error : bindingResult.getFieldErrors()) {
      errors
          .computeIfAbsent(wireFieldName(bindingResult, error.getField()), key -> new ArrayList<>())
          .add(error.getDefaultMessage());
    }
    problem.setProperty("errors", errors);

    return problemResponse(HttpStatus.BAD_REQUEST, problem);
  }

  /**
   * Service-layer field-validation failure — 400 validation-error, WITH a per-field errors map. The
   * {@code operator_type} immutability rejection on PUT lands here; the map is keyed by the
   * snake_case wire field name already (the service supplies wire keys), producing an identical
   * shape to the bean-validation 400.
   */
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ProblemDetail> handleServiceValidationException(ValidationException ex) {
    String traceId = MDC.get(MDC_TRACE_ID);
    log.warn("Validation error (trace: {}): {}", traceId, ex.getMessage());

    ProblemDetail problem =
        problemDetail(
            HttpStatus.BAD_REQUEST,
            "validation-error",
            "Validation Error",
            "Validation failed for one or more fields",
            traceId);
    problem.setProperty("errors", ex.getErrors());

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

  /** Unknown / cross-crn id, or a PUT on a tombstone — 404 not-found. */
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFoundException(NotFoundException ex) {
    String traceId = MDC.get(MDC_TRACE_ID);
    log.warn("Resource not found (trace: {}): {}", traceId, ex.getMessage());

    ProblemDetail problem =
        problemDetail(
            HttpStatus.NOT_FOUND, "not-found", "Resource Not Found", ex.getMessage(), traceId);

    return problemResponse(HttpStatus.NOT_FOUND, problem);
  }

  /** Name-clash conflict (Example CRUD only — removed with the controller increment). */
  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ProblemDetail> handleConflictException(ConflictException ex) {
    String traceId = MDC.get(MDC_TRACE_ID);
    log.warn("Resource conflict (trace: {}): {}", traceId, ex.getMessage());

    ProblemDetail problem =
        problemDetail(
            HttpStatus.CONFLICT, "conflict", "Resource Conflict", ex.getMessage(), traceId);

    return problemResponse(HttpStatus.CONFLICT, problem);
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

  /**
   * Resolves a rejected Java field name to its serialised wire name through the configured
   * {@link ObjectMapper} — honouring both the global snake_case strategy and any explicit
   * {@code @JsonProperty} override. Falls back to a plain snake-case when the target bean is
   * unavailable.
   */
  private String wireFieldName(BindingResult bindingResult, String field) {
    Object target = bindingResult.getTarget();
    if (target != null) {
      JavaType javaType = objectMapper.getTypeFactory().constructType(target.getClass());
      BeanDescription description = objectMapper.getSerializationConfig().introspect(javaType);
      for (BeanPropertyDefinition property : description.findProperties()) {
        if (field.equals(property.getInternalName())) {
          return property.getName();
        }
      }
    }
    return SNAKE_CASE.translate(field);
  }
}
