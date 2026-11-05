package uk.gov.defra.trade.imports.addressbook.address;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ReceivingRolesInTheUnitedKingdomValidator
    implements ConstraintValidator<ReceivingRolesInTheUnitedKingdom, AddressRequest> {

  @Override
  public boolean isValid(AddressRequest request, ConstraintValidatorContext context) {
    if (inTheUnitedKingdomOrUnchosen(request.countryCode())) {
      return true;
    }
    if (AddressRole.rolesNamed(request.roles()).stream()
        .map(AddressRole::group)
        .noneMatch(RoleGroup::inTheUnitedKingdom)) {
      return true;
    }
    context.disableDefaultConstraintViolation();
    context
        .buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
        .addPropertyNode("countryCode")
        .addConstraintViolation();
    return false;
  }

  private static boolean inTheUnitedKingdomOrUnchosen(String countryCode) {
    String unitedKingdom = "GB";
    return countryCode == null || countryCode.isBlank() || unitedKingdom.equals(countryCode);
  }
}
