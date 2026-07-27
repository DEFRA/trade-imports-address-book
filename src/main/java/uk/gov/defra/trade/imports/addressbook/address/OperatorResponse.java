package uk.gov.defra.trade.imports.operators.operator;

import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/**
 * Address wire response — the object the entity is mapped onto so the locked contract is decoupled
 * from persistence (the {@code @Document} entity is never serialised). The wire is camelCase, so
 * every field serialises under its Java name.
 *
 * <p>The soft-delete tombstone is exposed as a derived {@code deleted} boolean (cv-016), never the
 * internal status enum.
 *
 * <p>Server-constructed and always-present fields are null-guarded in the compact constructor
 * (service-boundary rule): a null here is a server bug and fails loud, not a malformed wire value.
 * {@code addressLine2} and {@code county} are genuinely optional and left unguarded.
 */
@Builder
public record OperatorResponse(
    String id,
    String name,
    String addressLine1,
    String addressLine2,
    String townOrCity,
    String county,
    String postcode,
    String countryCode,
    String phone,
    String email,
    String organisationId,
    boolean deleted,
    Instant createdAt,
    Instant modifiedAt) {

  public OperatorResponse {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(addressLine1, "addressLine1");
    Objects.requireNonNull(townOrCity, "townOrCity");
    Objects.requireNonNull(postcode, "postcode");
    Objects.requireNonNull(countryCode, "countryCode");
    Objects.requireNonNull(phone, "phone");
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(organisationId, "organisationId");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(modifiedAt, "modifiedAt");
  }
}
