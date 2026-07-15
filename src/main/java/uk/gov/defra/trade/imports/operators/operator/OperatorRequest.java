package uk.gov.defra.trade.imports.operators.operator;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import uk.gov.defra.trade.imports.operators.validation.ValidTransporterFields;

/**
 * Create/update request body. The wire is snake_case (global {@code JacksonConfig}); the two
 * digit-bearing fields carry an explicit {@code @JsonProperty} because the naming strategy alone
 * turns {@code addressLine1} into {@code address_line1}, not the contract's {@code address_line_1}.
 *
 * <p>Server-assigned fields ({@code id}, {@code status}, {@code crn}, {@code organisation_id},
 * timestamps) are not components here, so they are ignored if supplied — Spring Boot leaves
 * {@code fail-on-unknown-properties} off (Zalando default; §1.5).
 *
 * <p>This record carries <strong>no</strong> null guards: it is client-supplied and its non-null
 * enforcement is Bean Validation's job (inc-005), which must collect every field's error into the
 * {@code errors} map rather than fail fast on the first null.
 */
@ValidTransporterFields
@Builder
public record OperatorRequest(
    @NotNull(message = "Select an operator type") OperatorType operatorType,
    @NotBlank(message = "Enter a name")
        @Size(max = 255, message = "Name must be 255 characters or less")
        String name,
    @JsonProperty("address_line_1")
        @NotBlank(message = "Enter address line 1")
        @Size(max = 255, message = "Address line 1 must be 255 characters or less")
        String addressLine1,
    @JsonProperty("address_line_2")
        @Size(max = 255, message = "Address line 2 must be 255 characters or less")
        String addressLine2,
    @NotBlank(message = "Enter a town or city")
        @Size(max = 100, message = "Town or city must be 100 characters or less")
        String town,
    @Size(max = 100, message = "County must be 100 characters or less") String county,
    @NotBlank(message = "Enter a postcode")
        @Size(max = 12, message = "Postcode must be 12 characters or less")
        String postcode,
    @NotBlank(message = "Enter a country")
        @Size(max = 255, message = "Country must be 255 characters or less")
        String country,
    @NotBlank(message = "Enter a telephone number")
        @Size(max = 20, message = "Telephone number must be 20 characters or less")
        String telephone,
    @NotBlank(message = "Enter an email address")
        @Email(message = "Enter an email address in the correct format")
        @Size(max = 254, message = "Email address must be 254 characters or less")
        String email,
    @Size(max = 255, message = "Approval number must be 255 characters or less")
        String approvalNumber,
    TransporterCategory transporterCategory) {}
