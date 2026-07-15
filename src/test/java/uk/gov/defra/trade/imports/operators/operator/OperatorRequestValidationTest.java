package uk.gov.defra.trade.imports.operators.operator;

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
 * Field-table validation matrix for {@link OperatorRequest}. Pins the Bean Validation contract of
 * the create/update body against the EUDPA field table (design §1.5): every mandatory field, every
 * max-length, the email format leg, and the class-level {@code @ValidTransporterFields} cross-field
 * rule both ways (c-007).
 *
 * <p>Keys here are the Java property paths (e.g. {@code addressLine1}); the snake_case wire keys
 * ({@code address_line_1}) are asserted end-to-end through the real handler in {@code OperatorCrudIT}.
 */
class OperatorRequestValidationTest {

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

  private static OperatorRequest.OperatorRequestBuilder validRequest() {
    return OperatorRequest.builder()
        .operatorType(OperatorType.CONSIGNOR)
        .name("Highland Livestock Ltd")
        .addressLine1("14 Drover's Way")
        .town("Inverness")
        .postcode("IV2 3JH")
        .country("United Kingdom")
        .telephone("+44 1463 234567")
        .email("exports@highlandlivestock.example.com");
  }

  private static String repeat(int length) {
    return "a".repeat(length);
  }

  @Test
  void aFullyPopulatedValidRequestHasNoViolations() {
    OperatorRequest request =
        validRequest().addressLine2("Unit 3").county("Highland").build();

    assertThat(validator.validate(request)).isEmpty();
  }

  static Stream<Arguments> invalidFields() {
    return Stream.of(
        // missing mandatory
        Arguments.of("missing operatorType", validRequest().operatorType(null).build(), "operatorType"),
        Arguments.of("blank name", validRequest().name("").build(), "name"),
        Arguments.of("blank addressLine1", validRequest().addressLine1("").build(), "addressLine1"),
        Arguments.of("blank town", validRequest().town("").build(), "town"),
        Arguments.of("blank postcode", validRequest().postcode("").build(), "postcode"),
        Arguments.of("blank country", validRequest().country("").build(), "country"),
        Arguments.of("blank telephone", validRequest().telephone("").build(), "telephone"),
        Arguments.of("blank email", validRequest().email("").build(), "email"),
        // over max length
        Arguments.of("over-length name", validRequest().name(repeat(256)).build(), "name"),
        Arguments.of(
            "over-length addressLine1", validRequest().addressLine1(repeat(256)).build(), "addressLine1"),
        Arguments.of(
            "over-length addressLine2", validRequest().addressLine2(repeat(256)).build(), "addressLine2"),
        Arguments.of("over-length town", validRequest().town(repeat(101)).build(), "town"),
        Arguments.of("over-length county", validRequest().county(repeat(101)).build(), "county"),
        Arguments.of("over-length postcode", validRequest().postcode(repeat(13)).build(), "postcode"),
        Arguments.of("over-length country", validRequest().country(repeat(256)).build(), "country"),
        Arguments.of("over-length telephone", validRequest().telephone(repeat(21)).build(), "telephone"),
        Arguments.of(
            "over-length email",
            validRequest().email(repeat(243) + "@example.com").build(),
            "email"),
        Arguments.of(
            "over-length approvalNumber",
            validRequest().operatorType(OperatorType.TRANSPORTER).approvalNumber(repeat(256)).build(),
            "approvalNumber"),
        // format
        Arguments.of("malformed email", validRequest().email("not-an-email").build(), "email"));
  }

  @ParameterizedTest(name = "{0} -> violation on {2}")
  @MethodSource("invalidFields")
  void invalidFieldProducesAViolationOnThatField(
      String description, OperatorRequest request, String expectedProperty) {
    Set<String> violatedProperties =
        validator.validate(request).stream()
            .map(v -> v.getPropertyPath().toString())
            .collect(Collectors.toSet());

    assertThat(violatedProperties).contains(expectedProperty);
  }

  @Test
  void approvalNumberOnANonTransporterTypeIsRejected() {
    OperatorRequest request = validRequest().approvalNumber("APR-123").build();

    Set<ConstraintViolation<OperatorRequest>> violations = validator.validate(request);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString()).isEqualTo("approvalNumber");
              assertThat(v.getMessage()).isEqualTo("Only allowed for transporter operators");
            });
  }

  @Test
  void transporterCategoryOnANonTransporterTypeIsRejected() {
    OperatorRequest request =
        validRequest().transporterCategory(TransporterCategory.PRIVATE).build();

    Set<ConstraintViolation<OperatorRequest>> violations = validator.validate(request);

    assertThat(violations)
        .anySatisfy(
            v -> {
              assertThat(v.getPropertyPath().toString()).isEqualTo("transporterCategory");
              assertThat(v.getMessage()).isEqualTo("Only allowed for transporter operators");
            });
  }

  @Test
  void transporterExtrasOnATransporterTypeAreAccepted() {
    OperatorRequest request =
        validRequest()
            .operatorType(OperatorType.TRANSPORTER)
            .approvalNumber("APR-123")
            .transporterCategory(TransporterCategory.COMMERCIAL)
            .build();

    assertThat(validator.validate(request)).isEmpty();
  }
}
