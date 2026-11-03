package uk.gov.defra.trade.imports.addressbook.metrics;

import java.util.Objects;

/**
 * One call to a system the service depends on, named as the external-call metric contract names
 * it.
 *
 * @param dependency the system called, for example {@code mongodb}
 * @param operation the operation on that system, for example {@code find}
 */
public record ExternalCall(String dependency, String operation) {

  public static final String MONGODB = "mongodb";

  public ExternalCall {
    Objects.requireNonNull(dependency, "dependency must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
  }
}
