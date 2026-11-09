package uk.gov.defra.trade.imports.addressbook.transporter;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/**
 * The transporter as it appears on the wire. Every field is camelCase, under its Java name. It
 * carries no roles and no address type.
 *
 * <p>The soft-delete tombstone is a derived {@code deleted} boolean, never the internal status
 * enum.
 *
 * <p>Every field except {@code addressLine2}, {@code county} and {@code approvalNumber} is
 * required: the compact constructor throws {@link NullPointerException} when one is null.
 */
@Builder
@Schema(description = "A transporter in the caller's organisation-scoped address book")
public record TransporterResponse(
    @Schema(
            description = "Opaque transporter identifier",
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
            description = "Country code. GB-NIR for a commercial transporter",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "GB-NIR")
        String countryCode,
    @Schema(description = "Contact telephone number", requiredMode = Schema.RequiredMode.REQUIRED)
        String phone,
    @Schema(description = "Contact email address", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
    @Schema(
            description = "Whether this transporter is privately operated or a commercial haulier",
            requiredMode = Schema.RequiredMode.REQUIRED)
        TransporterType type,
    @Schema(description = "A commercial transporter's approval number; absent for a private one")
        String approvalNumber,
    @Schema(
            description = "Organisation that owns this transporter",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String organisationId,
    @Schema(
            description = "True when the transporter has been soft-deleted",
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

  public TransporterResponse {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(addressLine1, "addressLine1");
    Objects.requireNonNull(townOrCity, "townOrCity");
    Objects.requireNonNull(postcode, "postcode");
    Objects.requireNonNull(countryCode, "countryCode");
    Objects.requireNonNull(phone, "phone");
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(organisationId, "organisationId");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(modifiedAt, "modifiedAt");
  }
}
