package uk.gov.defra.trade.imports.operators.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint enforcing c-007: {@code approval_number} and {@code transporter_category}
 * are TRANSPORTER-only. Supplying either on any other operator type is a 400 that lands in the same
 * per-field {@code errors} map (keyed by {@code approval_number} / {@code transporter_category}),
 * not a separate error shape — the constraint enforces the TRANSPORTER-only semantics inside the
 * ruled validation-error status.
 *
 * <p>The write side of the same rule is closed on the frontend by c-019 (the add/edit form renders
 * the two fields for TRANSPORTER only), so the API constraint and the form agree.
 */
@Target(java.lang.annotation.ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TransporterFieldsValidator.class)
@Documented
public @interface ValidTransporterFields {

  String message() default "Only allowed for transporter operators";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
