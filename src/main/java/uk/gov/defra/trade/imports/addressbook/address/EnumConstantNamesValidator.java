package uk.gov.defra.trade.imports.addressbook.address;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class EnumConstantNamesValidator
    implements ConstraintValidator<EnumConstantNames, Collection<String>> {

  private Set<String> constantNames;

  @Override
  public void initialize(EnumConstantNames constraint) {
    constantNames =
        Arrays.stream(constraint.of().getEnumConstants())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public boolean isValid(Collection<String> names, ConstraintValidatorContext context) {
    return names == null
        || names.stream().allMatch(name -> name != null && constantNames.contains(name));
  }
}
