package uk.gov.defra.trade.imports.operators.operator;

import static org.assertj.core.api.Assertions.assertThat;

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
 * <p>{@code countryCode} is presence-only ({@code @NotBlank}, no length or list check — cv-011), so
 * there is no over-length country case.
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

  @Test
  void aFullyPopulatedValidRequestHasNoViolations() {
    AddressRequest request = validRequest().addressLine2("Unit 3").county("Highland").build();

    assertThat(validator.validate(request)).isEmpty();
  }

  static Stream<Arguments> invalidFields() {
    return Stream.of(
        // missing mandatory
        Arguments.of("blank name", validRequest().name("").build(), "name"),
        Arguments.of("blank addressLine1", validRequest().addressLine1("").build(), "addressLine1"),
        Arguments.of("blank townOrCity", validRequest().townOrCity("").build(), "townOrCity"),
        Arguments.of("blank postcode", validRequest().postcode("").build(), "postcode"),
        Arguments.of("blank countryCode", validRequest().countryCode("").build(), "countryCode"),
        Arguments.of("blank phone", validRequest().phone("").build(), "phone"),
        Arguments.of("blank email", validRequest().email("").build(), "email"),
        // over max length
        Arguments.of("over-length name", validRequest().name(repeat(256)).build(), "name"),
        Arguments.of(
            "over-length addressLine1", validRequest().addressLine1(repeat(256)).build(), "addressLine1"),
        Arguments.of(
            "over-length addressLine2", validRequest().addressLine2(repeat(256)).build(), "addressLine2"),
        Arguments.of("over-length townOrCity", validRequest().townOrCity(repeat(101)).build(), "townOrCity"),
        Arguments.of("over-length county", validRequest().county(repeat(101)).build(), "county"),
        Arguments.of("over-length postcode", validRequest().postcode(repeat(13)).build(), "postcode"),
        Arguments.of("over-length phone", validRequest().phone(repeat(21)).build(), "phone"),
        Arguments.of(
            "over-length email",
            validRequest().email(repeat(243) + "@example.com").build(),
            "email"),
        // format
        Arguments.of("malformed email", validRequest().email("not-an-email").build(), "email"),
        // untyped/unroled — a stray type/role is a per-field violation (cv-044)
        Arguments.of("stray type", validRequest().type("IMPORTER").build(), "type"),
        Arguments.of("stray role", validRequest().role("consignor").build(), "role"));
  }

  @ParameterizedTest(name = "{0} -> violation on {2}")
  @MethodSource("invalidFields")
  void invalidFieldProducesAViolationOnThatField(
      String description, AddressRequest request, String expectedProperty) {
    Set<String> violatedProperties =
        validator.validate(request).stream()
            .map(v -> v.getPropertyPath().toString())
            .collect(Collectors.toSet());

    assertThat(violatedProperties).contains(expectedProperty);
  }

  @Test
  void countryCodeIsPresenceOnlyAndAcceptsAnyNonBlankValueRegardlessOfLength() {
    // cv-011: countryCode is stored as-given with no @Size/list check — a long value is accepted.
    AddressRequest request = validRequest().countryCode(repeat(300)).build();

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void phoneIsNotFormatValidatedSoAFreeStringIsAccepted() {
    // cv-044: phone keeps @NotBlank/@Size only — no format check.
    AddressRequest request = validRequest().phone("call the office").build();

    assertThat(validator.validate(request)).isEmpty();
  }
}
