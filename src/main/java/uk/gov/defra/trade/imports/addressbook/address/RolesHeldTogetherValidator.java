package uk.gov.defra.trade.imports.addressbook.address;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;
import uk.gov.defra.trade.imports.addressbook.ClassLevelViolations;

public class RolesHeldTogetherValidator
    implements ConstraintValidator<RolesHeldTogether, AddressRequest> {

  @Override
  public boolean isValid(AddressRequest request, ConstraintValidatorContext context) {
    if (shareOneGroup(request.knownRoles())) {
      return true;
    }
    return ClassLevelViolations.reportOn(context, "roles");
  }

  private static boolean shareOneGroup(List<AddressRole> roles) {
    return roles.stream().map(AddressRole::group).distinct().count() <= 1;
  }
}
