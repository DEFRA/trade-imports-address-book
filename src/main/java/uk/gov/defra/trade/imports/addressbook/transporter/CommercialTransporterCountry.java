package uk.gov.defra.trade.imports.addressbook.transporter;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A commercial transporter's country is the fixed Northern Ireland code; a private transporter's
 * need not be. A violation is reported on {@code countryCode}.
 */
@Documented
@Constraint(validatedBy = CommercialTransporterCountryValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommercialTransporterCountry {

  String message() default "A commercial transporter must be in Northern Ireland";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
