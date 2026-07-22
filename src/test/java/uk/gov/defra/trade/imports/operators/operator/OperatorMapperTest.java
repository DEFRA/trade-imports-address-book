package uk.gov.defra.trade.imports.operators.operator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OperatorMapperTest {

  private final ObjectMapper mapper =
      new ObjectMapper()
          .findAndRegisterModules()
          .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private Address sampleEntity(AddressStatus status) {
    return Address.builder()
        .id("665f1c2ab3e4d51a2c9d0e77")
        .name("Acme Livestock")
        .addressLine1("1 Market Street")
        .addressLine2("Docklands")
        .townOrCity("Hull")
        .county("East Riding")
        .postcode("HU1 1AA")
        .countryCode("GB")
        .phone("+441482000000")
        .email("ops@acme.example")
        .organisationId("ORG-001")
        .status(status)
        .createdAt(Instant.parse("2026-07-01T09:00:00Z"))
        .modifiedAt(Instant.parse("2026-07-02T10:30:00Z"))
        .build();
  }

  @Test
  void toResponse_copiesEveryFieldFromTheEntityAndDerivesDeletedFalseForAnActiveRow() {
    OperatorResponse response = OperatorMapper.toResponse(sampleEntity(AddressStatus.ACTIVE));

    assertThat(response.id()).isEqualTo("665f1c2ab3e4d51a2c9d0e77");
    assertThat(response.name()).isEqualTo("Acme Livestock");
    assertThat(response.addressLine1()).isEqualTo("1 Market Street");
    assertThat(response.addressLine2()).isEqualTo("Docklands");
    assertThat(response.townOrCity()).isEqualTo("Hull");
    assertThat(response.county()).isEqualTo("East Riding");
    assertThat(response.postcode()).isEqualTo("HU1 1AA");
    assertThat(response.countryCode()).isEqualTo("GB");
    assertThat(response.phone()).isEqualTo("+441482000000");
    assertThat(response.email()).isEqualTo("ops@acme.example");
    assertThat(response.organisationId()).isEqualTo("ORG-001");
    assertThat(response.deleted()).isFalse();
    assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-07-01T09:00:00Z"));
    assertThat(response.modifiedAt()).isEqualTo(Instant.parse("2026-07-02T10:30:00Z"));
  }

  @Test
  void toResponse_derivesDeletedTrueForATombstone() {
    OperatorResponse response = OperatorMapper.toResponse(sampleEntity(AddressStatus.DELETED));

    assertThat(response.deleted()).isTrue();
  }

  @Test
  void response_serialisesEntirelyInCamelCaseWithADerivedDeletedBooleanAndNoStatusEnum()
      throws Exception {
    OperatorResponse response = OperatorMapper.toResponse(sampleEntity(AddressStatus.ACTIVE));

    JsonNode json = mapper.valueToTree(response);

    // Every field serialises under its Java name — the digit-bearing fields are addressLine1 /
    // addressLine2, never the snake_case address_line_1.
    assertThat(json.has("addressLine1")).isTrue();
    assertThat(json.has("addressLine2")).isTrue();
    assertThat(json.has("address_line_1")).isFalse();

    assertThat(json.has("townOrCity")).isTrue();
    assertThat(json.has("countryCode")).isTrue();
    assertThat(json.has("phone")).isTrue();
    assertThat(json.has("organisationId")).isTrue();
    assertThat(json.has("createdAt")).isTrue();
    assertThat(json.has("modifiedAt")).isTrue();

    // the tombstone is a derived boolean, never the internal status enum (cv-016)
    assertThat(json.has("deleted")).isTrue();
    assertThat(json.get("deleted").asBoolean()).isFalse();
    assertThat(json.has("status")).isFalse();

    // the untyped model carries no type/transporter fields
    assertThat(json.has("operatorType")).isFalse();
    assertThat(json.has("transporterCategory")).isFalse();
    assertThat(json.has("approvalNumber")).isFalse();
    assertThat(json.has("crn")).isFalse();
  }

  @Test
  void request_deserialisesFromCamelCaseAndMapsOntoAnEntityIgnoringServerFields() throws Exception {
    String body =
        """
        {
          "name": "Port Importers Ltd",
          "addressLine1": "7 Quay Road",
          "addressLine2": "Berth 4",
          "townOrCity": "Dover",
          "county": "Kent",
          "postcode": "CT16 1AA",
          "countryCode": "GB",
          "phone": "+441304000000",
          "email": "imports@port.example",
          "id": "ignored-server-field",
          "deleted": true
        }
        """;

    AddressRequest request = mapper.readValue(body, AddressRequest.class);
    Address entity = OperatorMapper.toEntity(request);

    assertThat(entity.getName()).isEqualTo("Port Importers Ltd");
    assertThat(entity.getAddressLine1()).isEqualTo("7 Quay Road");
    assertThat(entity.getAddressLine2()).isEqualTo("Berth 4");
    assertThat(entity.getTownOrCity()).isEqualTo("Dover");
    assertThat(entity.getCounty()).isEqualTo("Kent");
    assertThat(entity.getPostcode()).isEqualTo("CT16 1AA");
    assertThat(entity.getCountryCode()).isEqualTo("GB");
    assertThat(entity.getPhone()).isEqualTo("+441304000000");
    assertThat(entity.getEmail()).isEqualTo("imports@port.example");
    // Server-assigned fields in the body are ignored, never mapped onto the entity.
    assertThat(entity.getId()).isNull();
    assertThat(entity.getStatus()).isNull();
    assertThat(entity.getOrganisationId()).isNull();
  }
}
