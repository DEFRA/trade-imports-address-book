package uk.gov.defra.trade.imports.operators.operator;

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
 * unroled, so they are modelled as explicit {@code @Null} components. A supplied value binds and
 * fails {@code @Null}, landing in the same Bean-Validation {@code errors} map keyed {@code type} /
 * {@code role} — a per-field 400, not a silent drop and not a deserialization failure. They are
 * never mapped onto the entity.
 *
 * <p>This record carries <strong>no</strong> null guards: it is client-supplied and its non-null
 * enforcement is Bean Validation's job, which must collect every field's error into the
 * {@code errors} map rather than fail fast on the first null.
 *
 * <p>{@code countryCode} is validated for presence only ({@code @NotBlank}, no length or list
 * check): it is stored exactly as given (cv-011).
 */
@Builder
public record AddressRequest(
    @NotBlank(message = "Enter a name")
        @Size(max = 255, message = "Name must be 255 characters or less")
        String name,
    @NotBlank(message = "Enter address line 1")
        @Size(max = 255, message = "Address line 1 must be 255 characters or less")
        String addressLine1,
    @Size(max = 255, message = "Address line 2 must be 255 characters or less")
        String addressLine2,
    @NotBlank(message = "Enter a town or city")
        @Size(max = 100, message = "Town or city must be 100 characters or less")
        String townOrCity,
    @Size(max = 100, message = "County must be 100 characters or less") String county,
    @NotBlank(message = "Enter a postcode")
        @Size(max = 12, message = "Postcode must be 12 characters or less")
        String postcode,
    @NotBlank(message = "Enter a country") String countryCode,
    @NotBlank(message = "Enter a telephone number")
        @Size(max = 20, message = "Telephone number must be 20 characters or less")
        String phone,
    @NotBlank(message = "Enter an email address")
        @Email(message = "Enter an email address in the correct format")
        @Size(max = 254, message = "Email address must be 254 characters or less")
        String email,
    @Null(message = "type is not a supported field") Object type,
    @Null(message = "role is not a supported field") Object role) {}
