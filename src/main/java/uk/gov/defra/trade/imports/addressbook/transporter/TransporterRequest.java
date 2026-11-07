package uk.gov.defra.trade.imports.addressbook.transporter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Create/replace request body for a transporter. Every field is camelCase on the wire, under its
 * Java name.
 *
 * <p>Server-assigned fields ({@code id}, {@code organisationId}, {@code deleted}, timestamps) are
 * not components, so a body that echoes them is accepted and they are dropped.
 */
@Builder
@TransporterApprovalRequirement
@CommercialTransporterCountry
@Schema(description = "Create or replace a transporter in the caller's address book")
public record TransporterRequest(
    @NotBlank(message = "Enter a name or organisation")
        @Size(max = 255, message = "Name must be 255 characters or less")
        @Schema(
            description = "Display name for the transporter",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 255,
            example = "Highland Haulage Ltd")
        String name,
    @NotBlank(message = "Enter address line 1")
        @Size(max = 255, message = "Address line 1 must be 255 characters or less")
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
        @Size(max = 100, message = "Town or city must be 100 characters or less")
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
        @Size(max = 12, message = "Postcode must be 12 characters or less")
        @Schema(
            description = "Postal or ZIP code",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 12,
            example = "IV2 3JH")
        String postcode,
    @NotBlank(message = "Select a country")
        @Schema(
            description =
                "Country code (stored as given). Fixed to GB-NIR when type is COMMERCIAL",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "GB-NIR")
        String countryCode,
    @NotBlank(message = "Enter a phone number")
        @Size(max = 20, message = "Telephone number must be 20 characters or less")
        @Schema(
            description = "Contact telephone number",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 20,
            example = "+44 1463 234567")
        String phone,
    @NotBlank(message = "Enter an email address")
        @Email(message = "Enter an email address in the correct format")
        @Size(max = 254, message = "Email address must be 254 characters or less")
        @Schema(
            description = "Contact email address",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 254,
            example = "haulage@highland.example.com")
        String email,
    @NotNull(message = "Select what kind of transporter this is")
        @Schema(
            description = "Whether this transporter is privately operated or a commercial haulier",
            requiredMode = Schema.RequiredMode.REQUIRED)
        TransporterType type,
    @Size(max = 50, message = "Approval number must be 50 characters or less")
        @Schema(
            description = "A commercial transporter's approval number; absent for a private one",
            maxLength = 50,
            example = "UK/BELF/T2/00104115")
        String approvalNumber,
    @Null(message = "roles is not a supported field")
        @Schema(hidden = true)
        Object roles,
    @Null(message = "role is not a supported field")
        @Schema(hidden = true)
        Object role) {}
