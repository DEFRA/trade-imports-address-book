package uk.gov.defra.trade.imports.addressbook.address;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class AddressRoleNamesValidator
    implements ConstraintValidator<AddressRoleNames, Collection<String>> {

  private static final Set<String> ROLE_NAMES =
      Arrays.stream(AddressRole.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());

  @Override
  public boolean isValid(Collection<String> names, ConstraintValidatorContext context) {
    return names == null
        || names.stream().allMatch(name -> name != null && ROLE_NAMES.contains(name));
  }
}
