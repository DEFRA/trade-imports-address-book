package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Field-table validation matrix for {@link AddressRequest}. Pins the Bean Validation contract of
 * the create/update body against the Standard Address Block field table: every mandatory field,
 * every max-length, and the email format leg.
 *
 * <p>{@code countryCode} is ISO 3166-1 alpha-2 ({@code @Size(max = 2)}).
 *
 * <p>{@code type} and {@code role} are {@code @Null}: the book is untyped/unroled, so a supplied
 * value is a violation on that field (cv-044), while their absence (the normal case) is valid.
 *
 * <p>Keys here are the Java property paths (e.g. {@code addressLine1}); the wire keys are identical
 * (camelCase, cv-001) and asserted end-to-end through the real handler in {@code OperatorCrudIT}.
 */
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

  private static AddressRequest.AddressRequestBuilder validRequest() {
    return AddressRequest.builder()
        .name("Highland Livestock Ltd")
        .addressLine1("14 Drover's Way")
        .townOrCity("Inverness")
        .postcode("IV2 3JH")
        .countryCode("GB")
        .phone("+44 1463 234567")
        .email("exports@highlandlivestock.example.com");
  }

  private static String repeat(int length) {
    return "a".repeat(length);
  }

  /** Format-valid email one character over the 254-char @Size limit (local part within RFC 64). */
  private static String overLengthValidEmail() {
    return repeat(64) + "@" + repeat(63) + "." + repeat(63) + "." + repeat(62);
  }

  /** Format-valid email at exactly the 254-char @Size limit. */
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
        // missing mandatory
        Arguments.of("blank name", validRequest().name("").build(), "name", "Enter a name"),
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
            "blank postcode", validRequest().postcode("").build(), "postcode", "Enter a postcode"),
        Arguments.of(
            "blank countryCode",
            validRequest().countryCode("").build(),
            "countryCode",
            "Enter a country"),
        Arguments.of("blank phone", validRequest().phone("").build(), "phone", "Enter a telephone number"),
        Arguments.of(
            "blank email", validRequest().email("").build(), "email", "Enter an email address"),
        // over max length
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
        // format
        Arguments.of(
            "malformed email",
            validRequest().email("not-an-email").build(),
            "email",
            "Enter an email address in the correct format"),
        // untyped/unroled — a stray type/role is a per-field violation (cv-044)
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

  @ParameterizedTest(name = "{0} -> violation on {2}: {3}")
  @MethodSource("invalidFields")
  void invalidFieldProducesAViolationOnThatFieldWithTheDeclaredMessage(
      String description, AddressRequest request, String expectedProperty, String expectedMessage) {
    Set<ConstraintViolation<AddressRequest>> violations = validator.validate(request);

    assertThat(violations)
        .anySatisfy(
            violation -> {
              assertThat(violation.getPropertyPath().toString()).isEqualTo(expectedProperty);
              assertThat(violation.getMessage()).isEqualTo(expectedMessage);
            });
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
        Arguments.of("countryCode at max", validRequest().countryCode("GB").build()),
        Arguments.of("phone at max", validRequest().phone(repeat(20)).build()),
        Arguments.of("email at max", validRequest().email(maxLengthValidEmail()).build()));
  }

  @ParameterizedTest(name = "{0} has no violations")
  @MethodSource("fieldsAtMaximumLength")
  void fieldAtMaximumLengthHasNoViolations(String description, AddressRequest request) {
    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void countryCodeRejectsValuesLongerThanTwoCharacters() {
    AddressRequest request = validRequest().countryCode(repeat(3)).build();

    Set<String> violatedProperties =
        validator.validate(request).stream()
            .map(v -> v.getPropertyPath().toString())
            .collect(Collectors.toSet());

    assertThat(violatedProperties).contains("countryCode");
  }

  @Test
  void phoneIsNotFormatValidatedSoAFreeStringIsAccepted() {
    // cv-044: phone keeps @NotBlank/@Size only — no format check.
    AddressRequest request = validRequest().phone("call the office").build();

    assertThat(validator.validate(request)).isEmpty();
  }
}
