package uk.gov.defra.trade.imports.addressbook.exceptions;

/** A request parameter that is well formed but fails a service-level check. */
public class BadRequestException extends RuntimeException {

  public BadRequestException(String message) {
    super(message);
  }
}
