package uk.gov.defra.trade.imports.operators.operator;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

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
@Builder
public record OperatorRequest(
    OperatorType operatorType,
    String name,
    @JsonProperty("address_line_1") String addressLine1,
    @JsonProperty("address_line_2") String addressLine2,
    String town,
    String county,
    String postcode,
    String country,
    String telephone,
    String email,
    String approvalNumber,
    TransporterCategory transporterCategory) {}
