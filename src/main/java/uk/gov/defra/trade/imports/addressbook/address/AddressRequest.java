package uk.gov.defra.trade.imports.addressbook.address;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Create/update request body. Every field is camelCase on the wire, under its Java name.
 *
 * <p>Server-assigned fields ({@code id}, {@code organisationId}, {@code deleted}, timestamps) are
 * not components, so a body that echoes them is accepted and they are dropped.
 *
 * <p>{@code type} and {@code role} must be absent: a supplied value fails {@code @Null} and is
 * reported as a per-field validation error. Both are hidden from the OpenAPI schema and never
 * mapped onto the entity.
 *
 * <p>The record has no null guards, so Bean Validation can report every missing field at once.
 *
 * <p>{@code countryCode} is validated for presence only and stored exactly as given.
 */
@Builder
@Schema(description = "Create or replace an address in the caller's address book")
public record AddressRequest(
    @NotBlank(message = "Enter a name or organisation name")
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
    @NotBlank(message = "Enter a postcode or Zip code")
        @Size(min = 1, max = 12, message = "Postcode must be 12 characters or less")
        @Schema(
            description = "Postal or ZIP code",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 12,
            example = "IV2 3JH")
        String postcode,
    @NotBlank(message = "Select a country")
        @Schema(
            description = "Country code (stored as-given)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "GB")
        String countryCode,
    @NotBlank(message = "Enter a phone number")
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
