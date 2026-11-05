package uk.gov.defra.trade.imports.addressbook.address;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A request holding a consignee, importer or place of destination role has the United Kingdom as
 * its country. A violation is reported on {@code countryCode}.
 */
@Documented
@Constraint(validatedBy = ReceivingRolesInTheUnitedKingdomValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReceivingRolesInTheUnitedKingdom {

  String message() default
      "A consignee, importer or place of destination must be in the United Kingdom";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
