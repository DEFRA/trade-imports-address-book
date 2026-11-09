package uk.gov.defra.trade.imports.addressbook.transporter;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.defra.trade.imports.addressbook.ValidatorSupport.repeatedText;
import static uk.gov.defra.trade.imports.addressbook.ValidatorSupport.validator;
import static uk.gov.defra.trade.imports.addressbook.transporter.TransporterTestSupport.NORTHERN_IRELAND_COUNTRY_CODE;
import static uk.gov.defra.trade.imports.addressbook.transporter.TransporterTestSupport.validCommercialRequest;
import static uk.gov.defra.trade.imports.addressbook.transporter.TransporterTestSupport.validPrivateRequest;

import jakarta.validation.ConstraintViolation;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TransporterRequestValidationTest {

  @Test
  void aFullyPopulatedValidPrivateRequestHasNoViolations() {
    assertThat(validator().validate(validPrivateRequest().build())).isEmpty();
  }

  @Test
  void aFullyPopulatedValidCommercialRequestHasNoViolations() {
    assertThat(validator().validate(validCommercialRequest().build())).isEmpty();
  }

  static Stream<Arguments> invalidFields() {
    return Stream.of(
        Arguments.of(
            "blank name", validPrivateRequest().name("").build(), "name", "Enter a name or organisation"),
        Arguments.of(
            "blank addressLine1",
            validPrivateRequest().addressLine1("").build(),
            "addressLine1",
            "Enter address line 1"),
        Arguments.of(
            "blank townOrCity",
            validPrivateRequest().townOrCity("").build(),
            "townOrCity",
            "Enter a town or city"),
        Arguments.of(
            "blank postcode",
            validPrivateRequest().postcode("").build(),
            "postcode",
            "Enter a postcode or Zip code"),
        Arguments.of(
            "blank countryCode",
            validPrivateRequest().countryCode("").build(),
            "countryCode",
            "Select a country"),
        Arguments.of(
            "blank phone", validPrivateRequest().phone("").build(), "phone", "Enter a phone number"),
        Arguments.of(
            "blank email", validPrivateRequest().email("").build(), "email", "Enter an email address"),
        Arguments.of(
            "over-length name",
            validPrivateRequest().name(repeatedText(256)).build(),
            "name",
            "Name must be 255 characters or less"),
        Arguments.of(
            "over-length addressLine1",
            validPrivateRequest().addressLine1(repeatedText(256)).build(),
            "addressLine1",
            "Address line 1 must be 255 characters or less"),
        Arguments.of(
            "over-length townOrCity",
            validPrivateRequest().townOrCity(repeatedText(101)).build(),
            "townOrCity",
            "Town or city must be 100 characters or less"),
        Arguments.of(
            "over-length postcode",
            validPrivateRequest().postcode(repeatedText(13)).build(),
            "postcode",
            "Postcode must be 12 characters or less"),
        Arguments.of(
            "over-length phone",
            validPrivateRequest().phone(repeatedText(21)).build(),
            "phone",
            "Telephone number must be 20 characters or less"),
        Arguments.of(
            "malformed email",
            validPrivateRequest().email("not-an-email").build(),
            "email",
            "Enter an email address in the correct format"),
        Arguments.of(
            "missing type",
            validPrivateRequest().type(null).build(),
            "type",
            "Select what kind of transporter this is"),
        Arguments.of(
            "stray roles",
            validPrivateRequest().roles(List.of("CONSIGNOR")).build(),
            "roles",
            "roles is not a supported field"),
        Arguments.of(
            "stray role",
            validPrivateRequest().role("CONSIGNOR").build(),
            "role",
            "role is not a supported field"),
        Arguments.of(
            "over-length approval number",
            validCommercialRequest().approvalNumber(repeatedText(51)).build(),
            "approvalNumber",
            "Approval number must be 50 characters or less"));
  }

  @ParameterizedTest(name = "{0} -> only violation on {2}: {3}")
  @MethodSource("invalidFields")
  void validate_producesOnlyTheDeclaredMessageOnAnInvalidField(
      String description,
      TransporterRequest request,
      String expectedProperty,
      String expectedMessage) {
    Set<ConstraintViolation<TransporterRequest>> violations = validator().validate(request);

    assertThat(violations)
        .filteredOn(violation -> violation.getPropertyPath().toString().equals(expectedProperty))
        .extracting(ConstraintViolation::getMessage)
        .containsExactly(expectedMessage);
  }

  @Test
  void aCommercialTransporterWithNoApprovalNumberIsRefusedOnApprovalNumber() {
    TransporterRequest request = validCommercialRequest().approvalNumber(null).build();

    assertThat(validator().validate(request))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactly("approvalNumber");
  }

  @Test
  void aPrivateTransporterWithAnApprovalNumberIsRefusedOnApprovalNumber() {
    TransporterRequest request = validPrivateRequest().approvalNumber("UK/BELF/T2/00104115").build();

    assertThat(validator().validate(request))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactly("approvalNumber");
  }

  @Test
  void aCommercialTransporterNotInNorthernIrelandIsRefusedOnCountryCode() {
    TransporterRequest request = validCommercialRequest().countryCode("GB").build();

    assertThat(validator().validate(request))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactly("countryCode");
  }

  @Test
  void aPrivateTransporterMayBeInAnyCountry() {
    TransporterRequest request = validPrivateRequest().countryCode("ES").build();

    assertThat(validator().validate(request)).isEmpty();
  }

  @Test
  void aCommercialTransporterInNorthernIrelandIsNotRefused() {
    TransporterRequest request =
        validCommercialRequest().countryCode(NORTHERN_IRELAND_COUNTRY_CODE).build();

    assertThat(validator().validate(request)).isEmpty();
  }
}
