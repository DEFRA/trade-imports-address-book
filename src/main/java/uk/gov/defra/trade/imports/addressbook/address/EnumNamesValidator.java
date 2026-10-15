package uk.gov.defra.trade.imports.addressbook.address;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates {@link EnumNames}: exact, case-sensitive constant names. */
public class EnumNamesValidator implements ConstraintValidator<EnumNames, Object> {

  private Set<String> names = Set.of();

  @Override
  public void initialize(EnumNames annotation) {
    names =
        Arrays.stream(annotation.value().getEnumConstants())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public boolean isValid(Object value, ConstraintValidatorContext context) {
    return switch (value) {
      case null -> true;
      case String name -> names.contains(name);
      case Collection<?> values ->
          values.stream().allMatch(element -> element instanceof String s && names.contains(s));
      default -> false;
    };
  }
}
