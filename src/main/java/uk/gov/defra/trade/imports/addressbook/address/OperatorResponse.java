package uk.gov.defra.trade.imports.addressbook.address;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/**
 * The address as it appears on the wire. Every field is camelCase, under its Java name.
 *
 * <p>The soft-delete tombstone is a derived {@code deleted} boolean, never the internal status
 * enum.
 *
 * <p>Every field except {@code addressLine2} and {@code county} is required: the compact
 * constructor throws {@link NullPointerException} when one is null.
 */
@Builder
@Schema(description = "An address in the caller's organisation-scoped address book")
public record OperatorResponse(
    @Schema(
            description = "Opaque address identifier",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "665f1c2ab3e4d51a2c9d0e77")
        String id,
    @Schema(description = "Display name", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
    @Schema(description = "First line of the postal address", requiredMode = Schema.RequiredMode.REQUIRED)
        String addressLine1,
    @Schema(description = "Second line of the postal address") String addressLine2,
    @Schema(description = "Town or city", requiredMode = Schema.RequiredMode.REQUIRED)
        String townOrCity,
    @Schema(description = "County or region") String county,
    @Schema(description = "Postal or ZIP code", requiredMode = Schema.RequiredMode.REQUIRED)
        String postcode,
    @Schema(
            description = "ISO 3166-1 alpha-2 country code",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "GB")
        String countryCode,
    @Schema(description = "Contact telephone number", requiredMode = Schema.RequiredMode.REQUIRED)
        String phone,
    @Schema(description = "Contact email address", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
    @Schema(
            description = "Organisation that owns this address",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String organisationId,
    @Schema(
            description = "True when the address has been soft-deleted",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean deleted,
    @Schema(
            description = "Creation timestamp (UTC)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            format = "date-time")
        Instant createdAt,
    @Schema(
            description = "Last modification timestamp (UTC)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            format = "date-time")
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
