package uk.gov.defra.trade.imports.addressbook.address;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class EnumConstantNameValidator implements ConstraintValidator<EnumConstantName, Object> {

  private Set<String> constantNames;

  @Override
  public void initialize(EnumConstantName constraint) {
    constantNames =
        Arrays.stream(constraint.of().getEnumConstants())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public boolean isValid(Object value, ConstraintValidatorContext context) {
    return switch (value) {
      case null -> true;
      case String name -> constantNames.contains(name);
      case Collection<?> names -> names.stream().allMatch(this::isConstantName);
      default -> false;
    };
  }

  private boolean isConstantName(Object candidate) {
    return candidate instanceof String name && constantNames.contains(name);
  }
}
