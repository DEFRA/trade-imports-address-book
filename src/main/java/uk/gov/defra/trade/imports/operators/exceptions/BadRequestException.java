package uk.gov.defra.trade.imports.operators.exceptions;

/**
 * Thrown for a malformed request that never reaches body validation — a missing identity header or
 * a malformed query parameter. Mapped by {@link GlobalExceptionHandler} to a 400
 * <em>bad-request</em> problem with <strong>no</strong> {@code errors} map, the distinct sibling of
 * the field-validation 400 (which does carry one).
 */
public class BadRequestException extends RuntimeException {

  public BadRequestException(String message) {
    super(message);
  }
}
