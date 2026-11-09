package uk.gov.defra.trade.imports.addressbook.address;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import uk.gov.defra.trade.imports.addressbook.ClassLevelViolations;

public class ReceivingRolesInTheUnitedKingdomValidator
    implements ConstraintValidator<ReceivingRolesInTheUnitedKingdom, AddressRequest> {

  @Override
  public boolean isValid(AddressRequest request, ConstraintValidatorContext context) {
    String countryCode = request.countryCode();
    if (countryCode == null || countryCode.isBlank()) {
      return true;
    }
    if (isTheUnitedKingdom(countryCode) || !holdsAReceivingRole(request)) {
      return true;
    }
    return ClassLevelViolations.reportOn(context, "countryCode");
  }

  private static boolean isTheUnitedKingdom(String countryCode) {
    String unitedKingdom = "GB";
    return unitedKingdom.equals(countryCode);
  }

  private static boolean holdsAReceivingRole(AddressRequest request) {
    return request.knownRoles().stream().anyMatch(role -> role.group() == RoleGroup.RECEIVING);
  }
}
