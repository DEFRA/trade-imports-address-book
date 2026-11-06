package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport.validRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AddressRequestValidationTest {

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

  private static String repeat(int length) {
    return "a".repeat(length);
  }

  private static String overLengthValidEmail() {
    return repeat(64) + "@" + repeat(63) + "." + repeat(63) + "." + repeat(62);
  }

  private static String maxLengthValidEmail() {
    return repeat(64) + "@" + repeat(63) + "." + repeat(63) + "." + repeat(61);
  }

  @Test
  void aFullyPopulatedValidRequestHasNoViolations() {
    AddressRequest request = validRequest().addressLine2("Unit 3").county("Highland").build();

    assertThat(validator.validate(request)).isEmpty();
  }

  static Stream<Arguments> invalidFields() {
    return Stream.of(
        Arguments.of(
            "blank name",
            validRequest().name("").build(),
            "name",
            "Enter a name or organisation"),
        Arguments.of(
            "blank addressLine1",
            validRequest().addressLine1("").build(),
            "addressLine1",
            "Enter address line 1"),
        Arguments.of(
            "blank townOrCity",
            validRequest().townOrCity("").build(),
            "townOrCity",
            "Enter a town or city"),
        Arguments.of(
            "blank postcode",
            validRequest().postcode("").build(),
            "postcode",
            "Enter a postcode or Zip code"),
        Arguments.of(
            "blank countryCode",
            validRequest().countryCode("").build(),
            "countryCode",
            "Select a country"),
        Arguments.of(
            "blank phone", validRequest().phone("").build(), "phone", "Enter a phone number"),
        Arguments.of(
            "blank email", validRequest().email("").build(), "email", "Enter an email address"),
        Arguments.of(
            "over-length name",
            validRequest().name(repeat(256)).build(),
            "name",
            "Name must be 255 characters or less"),
        Arguments.of(
            "over-length addressLine1",
            validRequest().addressLine1(repeat(256)).build(),
            "addressLine1",
            "Address line 1 must be 255 characters or less"),
        Arguments.of(
            "over-length addressLine2",
            validRequest().addressLine2(repeat(256)).build(),
            "addressLine2",
            "Address line 2 must be 255 characters or less"),
        Arguments.of(
            "over-length townOrCity",
            validRequest().townOrCity(repeat(101)).build(),
            "townOrCity",
            "Town or city must be 100 characters or less"),
        Arguments.of(
            "over-length county",
            validRequest().county(repeat(101)).build(),
            "county",
            "County must be 100 characters or less"),
        Arguments.of(
            "over-length postcode",
            validRequest().postcode(repeat(13)).build(),
            "postcode",
            "Postcode must be 12 characters or less"),
        Arguments.of(
            "over-length phone",
            validRequest().phone(repeat(21)).build(),
            "phone",
            "Telephone number must be 20 characters or less"),
        Arguments.of(
            "over-length email",
            validRequest().email(overLengthValidEmail()).build(),
            "email",
            "Email address must be 254 characters or less"),
        Arguments.of(
            "malformed email",
            validRequest().email("not-an-email").build(),
            "email",
            "Enter an email address in the correct format"),
        Arguments.of(
            "missing roles",
            validRequest().roles(null).build(),
            "roles",
            "Select what this address can be used for"),
        Arguments.of(
            "empty roles",
            validRequest().roles(List.of()).build(),
            "roles",
            "Select what this address can be used for"),
        Arguments.of(
            "unknown role",
            validRequest().roles(List.of("CONSIGNOR", "TRANSPORTER")).build(),
            "roles",
            "Select valid roles"),
        Arguments.of(
            "lower-case role",
            validRequest().roles(List.of("consignor")).build(),
            "roles",
            "Select valid roles"),
        Arguments.of(
            "null role",
            validRequest().roles(Arrays.asList("CONSIGNOR", null)).build(),
            "roles",
            "Select valid roles"),
        Arguments.of(
            "blank role",
            validRequest().roles(List.of("CONSIGNOR", " ")).build(),
            "roles",
            "Select valid roles"),
        Arguments.of(
            "sending and receiving roles",
            validRequest().roles(List.of("PLACE_OF_ORIGIN", "CONSIGNEE")).build(),
            "roles",
            "An address can only have roles from one group"),
        Arguments.of(
            "receiving and branch roles",
            validRequest().roles(List.of("IMPORTER", "BRANCH_ADDRESS")).build(),
            "roles",
            "An address can only have roles from one group"),
        Arguments.of(
            "sending and branch roles",
            validRequest().roles(List.of("CONSIGNOR", "BRANCH_ADDRESS")).build(),
            "roles",
            "An address can only have roles from one group"),
        Arguments.of(
            "receiving role outside the United Kingdom",
            validRequest().roles(List.of("PLACE_OF_DESTINATION")).countryCode("FR").build(),
            "countryCode",
            "A consignee, importer or place of destination must be in the United Kingdom"),
        Arguments.of(
            "receiving role with a blank countryCode",
            validRequest().roles(List.of("PLACE_OF_DESTINATION")).countryCode("").build(),
            "countryCode",
            "Select a country"),
        Arguments.of(
            "receiving role with no countryCode",
            validRequest().roles(List.of("PLACE_OF_DESTINATION")).countryCode(null).build(),
            "countryCode",
            "Select a country"),
        Arguments.of(
            "stray type",
            validRequest().type("IMPORTER").build(),
            "type",
            "type is not a supported field"),
        Arguments.of(
            "stray role",
            validRequest().role("consignor").build(),
            "role",
            "role is not a supported field"));
  }

  @ParameterizedTest(name = "{0} -> only violation on {2}: {3}")
  @MethodSource("invalidFields")
  void validate_producesOnlyTheDeclaredMessageOnAnInvalidField(
      String description, AddressRequest request, String expectedProperty, String expectedMessage) {
    Set<ConstraintViolation<AddressRequest>> violations = validator.validate(request);

    assertThat(violations)
        .filteredOn(violation -> violation.getPropertyPath().toString().equals(expectedProperty))
        .extracting(ConstraintViolation::getMessage)
        .containsExactly(expectedMessage);
  }

  static Stream<Arguments> fieldsAtMaximumLength() {
    return Stream.of(
        Arguments.of("name at max", validRequest().name(repeat(255)).build()),
        Arguments.of("addressLine1 at max", validRequest().addressLine1(repeat(255)).build()),
        Arguments.of(
            "addressLine2 at max", validRequest().addressLine2(repeat(255)).county("Highland").build()),
        Arguments.of("townOrCity at max", validRequest().townOrCity(repeat(100)).build()),
        Arguments.of(
            "county at max", validRequest().addressLine2("Unit 3").county(repeat(100)).build()),
        Arguments.of("postcode at max", validRequest().postcode(repeat(12)).build()),
        Arguments.of("phone at max", validRequest().phone(repeat(20)).build()),
        Arguments.of("email at max", validRequest().email(maxLengthValidEmail()).build()));
  }

  @ParameterizedTest(name = "{0} has no violations")
  @MethodSource("fieldsAtMaximumLength")
  void fieldAtMaximumLengthHasNoViolations(String description, AddressRequest request) {
    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void countryCodeOfASendingAddressIsPresenceOnlyAndAcceptsAnyNonBlankValue() {
    AddressRequest request = validRequest().countryCode(repeat(50)).build();

    assertThat(validator.validate(request)).isEmpty();
  }

  static Stream<Arguments> rolesHeldTogetherInTheirOwnCountries() {
    return Stream.of(
        Arguments.of(List.of("PLACE_OF_ORIGIN", "CONSIGNOR"), "FR"),
        Arguments.of(List.of("CONSIGNEE", "IMPORTER", "PLACE_OF_DESTINATION"), "GB"),
        Arguments.of(List.of("BRANCH_ADDRESS"), "GB"),
        Arguments.of(List.of("BRANCH_ADDRESS"), "IE"));
  }

  @ParameterizedTest(name = "{0} in {1} has no violations")
  @MethodSource("rolesHeldTogetherInTheirOwnCountries")
  void validate_findsNoViolationForRolesOneAddressCanHoldTogether(
      List<String> roles, String countryCode) {
    AddressRequest request = validRequest().roles(roles).countryCode(countryCode).build();

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void validate_reportsNoRoleGroupOrCountryViolationForAnUnknownRoleAlone() {
    AddressRequest request =
        validRequest().roles(List.of("TRANSPORTER")).countryCode("FR").build();

    assertThat(validator.validate(request))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactly("roles");
  }

  @Test
  void roles_collapseRepeatsToTheFirstOfEach() {
    AddressRequest request =
        validRequest().roles(List.of("CONSIGNOR", "PLACE_OF_ORIGIN", "CONSIGNOR")).build();

    assertThat(request.roles()).containsExactly("CONSIGNOR", "PLACE_OF_ORIGIN");
  }

  @Test
  void phoneIsNotFormatValidatedSoAFreeStringIsAccepted() {
    AddressRequest request = validRequest().phone("call the office").build();

    assertThat(validator.validate(request)).isEmpty();
  }
}
