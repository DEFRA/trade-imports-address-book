package uk.gov.defra.trade.imports.addressbook.exceptions;

/**
 * Thrown when a request parameter is syntactically valid but fails a service-level constraint before
 * body validation — for example {@code page} less than 1 in {@link
 * uk.gov.defra.trade.imports.addressbook.address.OperatorService#list}. Mapped by {@link
 * GlobalExceptionHandler} to a 400 <em>bad-request</em> problem with <strong>no</strong> {@code
 * errors} map.
 *
 * <p>Missing identity headers are rejected by {@link
 * uk.gov.defra.trade.imports.addressbook.filter.IdentityHeaderFilter} (which writes problem+json
 * directly). Malformed query parameters surface as {@link
 * org.springframework.web.method.annotation.MethodArgumentTypeMismatchException} with their own
 * handler — do not throw this exception from a servlet filter expecting the advice to map it.
 */
public class BadRequestException extends RuntimeException {

  public BadRequestException(String message) {
    super(message);
  }
}
