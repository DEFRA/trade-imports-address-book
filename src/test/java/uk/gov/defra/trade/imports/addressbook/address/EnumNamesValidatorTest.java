package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Pins the {@link EnumNames} rules: exact names, optional when absent, no null elements. */
class EnumNamesValidatorTest {

  private record Probe(
      @EnumNames(value = AddressUse.class, message = "bad") List<String> uses,
      @EnumNames(value = AddressType.class, message = "bad") String addressType) {}

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  private static Set<String> violatedProperties(Probe probe) {
    return validator.validate(probe).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .collect(Collectors.toSet());
  }

  @Test
  void nullValuesAreValidBecauseTheFieldsAreOptional() {
    assertThat(violatedProperties(new Probe(null, null))).isEmpty();
  }

  @Test
  void anEmptyListIsValid() {
    assertThat(violatedProperties(new Probe(List.of(), null))).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(AddressUse.class)
  void everyOneOfTheSixUsesIsValid(AddressUse use) {
    assertThat(violatedProperties(new Probe(List.of(use.name()), null))).isEmpty();
  }

  @Test
  void aListHoldingAnUnknownUseIsInvalidOnUses() {
    assertThat(violatedProperties(new Probe(List.of("CONSIGNOR", "TRANSPORTER"), null)))
        .containsExactly("uses");
  }

  @Test
  void aListHoldingANullElementIsInvalidOnUses() {
    assertThat(violatedProperties(new Probe(Arrays.asList("CONSIGNOR", null), null)))
        .containsExactly("uses");
  }

  @Test
  void aLowerCaseUseIsInvalidBecauseTheWireIsUpperSnakeCase() {
    assertThat(violatedProperties(new Probe(List.of("consignor"), null)))
        .containsExactly("uses");
  }

  @Test
  void aKnownAddressTypeIsValidAndAnUnknownOneIsNot() {
    assertThat(violatedProperties(new Probe(null, "BRANCH"))).isEmpty();
    assertThat(violatedProperties(new Probe(null, "TRANSPORTER"))).containsExactly("addressType");
  }
}
