package uk.gov.defra.trade.imports.addressbook.address;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The request's roles can all be held by one address. A violation is reported on {@code roles}.
 */
@Documented
@Constraint(validatedBy = RolesHeldTogetherValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RolesHeldTogether {

  String message() default "An address can only have roles from one group";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
