package uk.gov.defra.trade.imports.operators.exceptions;

import java.util.List;
import java.util.Map;

/**
 * A field-validation failure raised by the service layer rather than by Bean Validation — the
 * {@code operator_type} immutability rule on PUT (design §1.5: a body type differing from the stored
 * value is rejected, not silently ignored). Mapped by {@link GlobalExceptionHandler} to the same 400
 * <em>validation-error</em> problem as a {@link org.springframework.web.bind.MethodArgumentNotValidException},
 * carrying a per-field {@code errors} map keyed by the snake_case wire field name — so the frontend
 * error mapping treats a business-rule rejection identically to a bean-validation one.
 */
public class ValidationException extends RuntimeException {

  private final transient Map<String, List<String>> errors;

  public ValidationException(Map<String, List<String>> errors) {
    super("Validation failed for one or more fields");
    this.errors = Map.copyOf(errors);
  }

  public Map<String, List<String>> getErrors() {
    return errors;
  }
}
