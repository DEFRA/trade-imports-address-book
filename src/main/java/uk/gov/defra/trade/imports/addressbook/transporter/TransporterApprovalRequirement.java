package uk.gov.defra.trade.imports.addressbook.transporter;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A commercial transporter carries an approval number; a private one must not. A violation is
 * reported on {@code approvalNumber}.
 */
@Documented
@Constraint(validatedBy = TransporterApprovalRequirementValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TransporterApprovalRequirement {

  String message() default "Enter the approval number";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
