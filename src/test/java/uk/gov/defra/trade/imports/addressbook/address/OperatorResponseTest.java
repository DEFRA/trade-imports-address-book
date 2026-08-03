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

  private static OperatorResponse response(
      String id,
      String name,
      String addressLine1,
      String townOrCity,
      String postcode,
      String countryCode,
      String phone,
      String email,
      String organisationId,
      Instant createdAt,
      Instant modifiedAt) {
    return new OperatorResponse(
        id,
        name,
        addressLine1,
        null,
        townOrCity,
        null,
        postcode,
        countryCode,
        phone,
        email,
        organisationId,
        false,
        createdAt,
        modifiedAt);
  }

  private static OperatorResponse validResponse() {
    return response(
        "665f1c2ab3e4d51a2c9d0e77",
        "Acme Livestock",
        "1 Market Street",
        "Hull",
        "HU1 1AA",
        "GB",
        "+441482000000",
        "ops@acme.example",
        "ORG-001",
        NOW,
        NOW);
  }

  static Stream<Arguments> guardedFieldsRejectNull() {
    return Stream.of(
        Arguments.of("id", (Runnable) () -> response(null, "Acme", "line1", "Hull", "HU1", "GB", "phone", "email", "ORG", NOW, NOW)),
        Arguments.of("name", (Runnable) () -> response("id", null, "line1", "Hull", "HU1", "GB", "phone", "email", "ORG", NOW, NOW)),
        Arguments.of("addressLine1", (Runnable) () -> response("id", "Acme", null, "Hull", "HU1", "GB", "phone", "email", "ORG", NOW, NOW)),
        Arguments.of("townOrCity", (Runnable) () -> response("id", "Acme", "line1", null, "HU1", "GB", "phone", "email", "ORG", NOW, NOW)),
        Arguments.of("postcode", (Runnable) () -> response("id", "Acme", "line1", "Hull", null, "GB", "phone", "email", "ORG", NOW, NOW)),
        Arguments.of("countryCode", (Runnable) () -> response("id", "Acme", "line1", "Hull", "HU1", null, "phone", "email", "ORG", NOW, NOW)),
        Arguments.of("phone", (Runnable) () -> response("id", "Acme", "line1", "Hull", "HU1", "GB", null, "email", "ORG", NOW, NOW)),
        Arguments.of("email", (Runnable) () -> response("id", "Acme", "line1", "Hull", "HU1", "GB", "phone", null, "ORG", NOW, NOW)),
        Arguments.of("organisationId", (Runnable) () -> response("id", "Acme", "line1", "Hull", "HU1", "GB", "phone", "email", null, NOW, NOW)),
        Arguments.of("createdAt", (Runnable) () -> response("id", "Acme", "line1", "Hull", "HU1", "GB", "phone", "email", "ORG", null, NOW)),
        Arguments.of("modifiedAt", (Runnable) () -> response("id", "Acme", "line1", "Hull", "HU1", "GB", "phone", "email", "ORG", NOW, null)));
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
        new OperatorResponse(
            "665f1c2ab3e4d51a2c9d0e77",
            "Acme Livestock",
            "1 Market Street",
            null,
            "Hull",
            null,
            "HU1 1AA",
            "GB",
            "+441482000000",
            "ops@acme.example",
            "ORG-001",
            false,
            NOW,
            NOW);

    assertThat(response.addressLine2()).isNull();
    assertThat(response.county()).isNull();
  }
}
