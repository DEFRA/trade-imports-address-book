package uk.gov.defra.trade.imports.addressbook;

import jakarta.validation.ConstraintValidatorContext;

public final class ClassLevelViolations {

  private ClassLevelViolations() {}

  public static boolean reportOn(ConstraintValidatorContext context, String property) {
    return reportOn(context, property, context.getDefaultConstraintMessageTemplate());
  }

  public static boolean reportOn(
      ConstraintValidatorContext context, String property, String message) {
    context.disableDefaultConstraintViolation();
    context
        .buildConstraintViolationWithTemplate(message)
        .addPropertyNode(property)
        .addConstraintViolation();
    return false;
  }
}
