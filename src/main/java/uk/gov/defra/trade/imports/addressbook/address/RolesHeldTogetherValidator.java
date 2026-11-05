package uk.gov.defra.trade.imports.addressbook.address;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

public class RolesHeldTogetherValidator
    implements ConstraintValidator<RolesHeldTogether, AddressRequest> {

  @Override
  public boolean isValid(AddressRequest request, ConstraintValidatorContext context) {
    if (request == null || shareOneGroup(AddressRole.rolesNamed(request.roles()))) {
      return true;
    }
    context.disableDefaultConstraintViolation();
    context
        .buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
        .addPropertyNode("roles")
        .addConstraintViolation();
    return false;
  }

  private static boolean shareOneGroup(List<AddressRole> roles) {
    return roles.stream().map(AddressRole::group).distinct().count() <= 1;
  }
}
