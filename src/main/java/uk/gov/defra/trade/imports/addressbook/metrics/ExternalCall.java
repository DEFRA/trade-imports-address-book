package uk.gov.defra.trade.imports.addressbook.metrics;

import java.util.Objects;

/**
 * One call to a system the service depends on. Building one with a null {@code dependency} or
 * {@code operation} throws {@link NullPointerException}.
 *
 * @param dependency the system called, for example {@code mongodb}; must not be null
 * @param operation the operation on that system, for example {@code find}; must not be null
 */
public record ExternalCall(String dependency, String operation) {

  public static final String MONGODB = "mongodb";

  public ExternalCall {
    Objects.requireNonNull(dependency, "dependency must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
  }
}
