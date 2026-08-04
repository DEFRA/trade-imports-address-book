package uk.gov.defra.trade.imports.addressbook.address;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Create/update request body. The wire is camelCase (global {@code JacksonConfig}), so every field
 * serialises under its Java name.
 *
 * <p>Server-assigned fields ({@code id}, {@code organisationId}, {@code deleted}, timestamps) are
 * not components here, so they are ignored if supplied — Spring Boot leaves
 * {@code fail-on-unknown-properties} off (Zalando default): a body echoing a read-only field from a
 * prior GET is accepted and the field dropped, never a 400.
 *
 * <p>{@code type} and {@code role} are the one exception (cv-044): the address book is untyped and
 * unroled, so they are modelled as explicit {@code @Null} components with {@code @Schema(hidden =
 * true)} — present for Bean Validation and absent from the generated OpenAPI request schema. A
 * supplied value binds and fails {@code @Null}, landing in the same Bean-Validation {@code errors}
 * map keyed {@code type} / {@code role} — a per-field 400, not a silent drop and not a
 * deserialization failure. They are never mapped onto the entity.
 *
 * <p>This record carries <strong>no</strong> null guards: it is client-supplied and its non-null
 * enforcement is Bean Validation's job, which must collect every field's error into the
 * {@code errors} map rather than fail fast on the first null.
 *
 * <p>{@code countryCode} is validated for presence only ({@code @NotBlank}, cv-011): it is stored
 * exactly as given with no length, list, or ISO pattern check.
 */
@Builder
@Schema(description = "Create or replace an address in the caller's address book")
public record AddressRequest(
    @NotBlank(message = "Enter a name")
        @Size(min = 1, max = 255, message = "Name must be 255 characters or less")
        @Schema(
            description = "Display name for the address",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 255,
            example = "Highland Livestock Ltd")
        String name,
    @NotBlank(message = "Enter address line 1")
        @Size(min = 1, max = 255, message = "Address line 1 must be 255 characters or less")
        @Schema(
            description = "First line of the postal address",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 255,
            example = "14 Drover's Way")
        String addressLine1,
    @Size(max = 255, message = "Address line 2 must be 255 characters or less")
        @Schema(description = "Second line of the postal address", maxLength = 255)
        String addressLine2,
    @NotBlank(message = "Enter a town or city")
        @Size(min = 1, max = 100, message = "Town or city must be 100 characters or less")
        @Schema(
            description = "Town or city",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 100,
            example = "Inverness")
        String townOrCity,
    @Size(max = 100, message = "County must be 100 characters or less")
        @Schema(description = "County or region", maxLength = 100)
        String county,
    @NotBlank(message = "Enter a postcode")
        @Size(min = 1, max = 12, message = "Postcode must be 12 characters or less")
        @Schema(
            description = "Postal or ZIP code",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 12,
            example = "IV2 3JH")
        String postcode,
    @NotBlank(message = "Enter a country")
        @Schema(
            description = "Country code (stored as-given, cv-011)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "GB")
        String countryCode,
    @NotBlank(message = "Enter a telephone number")
        @Size(min = 1, max = 20, message = "Telephone number must be 20 characters or less")
        @Schema(
            description = "Contact telephone number",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 20,
            example = "+44 1463 234567")
        String phone,
    @NotBlank(message = "Enter an email address")
        @Email(message = "Enter an email address in the correct format")
        @Size(min = 1, max = 254, message = "Email address must be 254 characters or less")
        @Schema(
            description = "Contact email address",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 254,
            example = "exports@highlandlivestock.example.com")
        String email,
    @Null(message = "type is not a supported field")
        @Schema(hidden = true)
        Object type,
    @Null(message = "role is not a supported field")
        @Schema(hidden = true)
        Object role) {}
