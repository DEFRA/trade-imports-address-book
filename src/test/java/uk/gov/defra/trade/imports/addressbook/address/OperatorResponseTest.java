package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OperatorResponseTest {

  private static final Instant NOW = Instant.parse("2026-07-01T09:00:00Z");

  private static OperatorResponse.OperatorResponseBuilder validResponse() {
    return OperatorResponse.builder()
        .id("665f1c2ab3e4d51a2c9d0e77")
        .name("Acme Livestock")
        .addressLine1("1 Market Street")
        .townOrCity("Hull")
        .postcode("HU1 1AA")
        .countryCode("GB")
        .phone("+441482000000")
        .email("ops@acme.example")
        .organisationId("ORG-001")
        .createdAt(NOW)
        .modifiedAt(NOW);
  }

  static Stream<Arguments> guardedFieldsRejectNull() {
    return Stream.of(
        Arguments.of("id", (Runnable) () -> validResponse().id(null).build()),
        Arguments.of("name", (Runnable) () -> validResponse().name(null).build()),
        Arguments.of(
            "addressLine1", (Runnable) () -> validResponse().addressLine1(null).build()),
        Arguments.of("townOrCity", (Runnable) () -> validResponse().townOrCity(null).build()),
        Arguments.of("postcode", (Runnable) () -> validResponse().postcode(null).build()),
        Arguments.of("countryCode", (Runnable) () -> validResponse().countryCode(null).build()),
        Arguments.of("phone", (Runnable) () -> validResponse().phone(null).build()),
        Arguments.of("email", (Runnable) () -> validResponse().email(null).build()),
        Arguments.of(
            "organisationId", (Runnable) () -> validResponse().organisationId(null).build()),
        Arguments.of("createdAt", (Runnable) () -> validResponse().createdAt(null).build()),
        Arguments.of("modifiedAt", (Runnable) () -> validResponse().modifiedAt(null).build()));
  }

  @ParameterizedTest
  @MethodSource("guardedFieldsRejectNull")
  void compactConstructor_rejectsNullOnGuardedFields(String fieldName, Runnable buildResponse) {
    assertThatNullPointerException()
        .isThrownBy(buildResponse::run)
        .withMessage(fieldName);
  }

  @Test
  void compactConstructor_allowsNullOptionalFields() {
    OperatorResponse response =
        validResponse().addressLine2(null).county(null).addressType(null).uses(null).build();

    assertThat(response.addressLine2()).isNull();
    assertThat(response.county()).isNull();
    assertThat(response.addressType()).isNull();
    assertThat(response.uses()).isNull();
  }
}
