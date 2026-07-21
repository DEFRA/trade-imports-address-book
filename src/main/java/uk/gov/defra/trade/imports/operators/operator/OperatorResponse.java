package uk.gov.defra.trade.imports.operators.operator;

import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/**
 * Operator wire response — the object the entity is mapped onto so the locked contract is decoupled
 * from persistence (the {@code @Document} entity is never serialised). The wire is camelCase, so
 * every field serialises under its Java name.
 *
 * <p>Server-constructed and always-present fields are null-guarded in the compact constructor
 * (service-boundary rule): a null here is a server bug and fails loud, not a malformed wire value.
 * {@code addressLine2}, {@code county}, {@code approvalNumber} and {@code transporterCategory} are
 * genuinely optional and left unguarded.
 */
@Builder
public record OperatorResponse(
    String id,
    OperatorType operatorType,
    String name,
    String addressLine1,
    String addressLine2,
    String town,
    String county,
    String postcode,
    String country,
    String telephone,
    String email,
    String approvalNumber,
    TransporterCategory transporterCategory,
    String crn,
    String organisationId,
    OperatorStatus status,
    Instant createdAt,
    Instant modifiedAt) {

  public OperatorResponse {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(operatorType, "operatorType");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(addressLine1, "addressLine1");
    Objects.requireNonNull(town, "town");
    Objects.requireNonNull(postcode, "postcode");
    Objects.requireNonNull(country, "country");
    Objects.requireNonNull(telephone, "telephone");
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(crn, "crn");
    Objects.requireNonNull(organisationId, "organisationId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(modifiedAt, "modifiedAt");
  }
}
