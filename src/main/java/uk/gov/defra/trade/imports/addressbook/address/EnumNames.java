package uk.gov.defra.trade.imports.addressbook.address;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated {@code String}, or every element of the annotated {@code Collection}, must be the
 * {@code name()} of a constant of {@link #value()}. {@code null} is valid because the field is
 * optional; a {@code null} element inside a collection is not.
 */
@Documented
@Constraint(validatedBy = EnumNamesValidator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface EnumNames {

  /** The enum whose constant names are allowed. */
  Class<? extends Enum<?>> value();

  /** The violation message. */
  String message();

  /** Validation groups. */
  Class<?>[] groups() default {};

  /** Constraint payload. */
  Class<? extends Payload>[] payload() default {};
}
