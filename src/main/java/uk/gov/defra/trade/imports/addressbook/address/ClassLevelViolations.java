package uk.gov.defra.trade.imports.addressbook.address;

import jakarta.validation.ConstraintValidatorContext;

final class ClassLevelViolations {

  private ClassLevelViolations() {}

  static boolean reportOn(ConstraintValidatorContext context, String property) {
    context.disableDefaultConstraintViolation();
    context
        .buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
        .addPropertyNode(property)
        .addConstraintViolation();
    return false;
  }
}
