package uk.gov.defra.trade.imports.addressbook.address;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated string, or every string in the annotated collection, must equal the name of a
 * constant of {@link #of()}. Null is valid.
 */
@Documented
@Constraint(validatedBy = EnumConstantNameValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface EnumConstantName {

  String message();

  Class<? extends Enum<?>> of();

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
