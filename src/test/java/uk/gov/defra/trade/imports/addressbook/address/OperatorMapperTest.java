package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class OperatorMapperTest {

  private final OperatorMapper operatorMapper = Mappers.getMapper(OperatorMapper.class);

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    // Mirrors JacksonConfig.camelCaseNamingStrategy() — the production ObjectMapper contract.
    Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
    builder
        .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
        .failOnUnknownProperties(false)
        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper = builder.build().findAndRegisterModules();
  }

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
    OperatorResponse response = operatorMapper.toResponse(sampleEntity(AddressStatus.ACTIVE));

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
    OperatorResponse response = operatorMapper.toResponse(sampleEntity(AddressStatus.DELETED));

    assertThat(response.deleted()).isTrue();
  }

  @Test
  void toResponse_mapsOptionalFieldsWhenAbsent() {
    Address entity =
        Address.builder()
            .id("665f1c2ab3e4d51a2c9d0e77")
            .name("Acme Livestock")
            .addressLine1("1 Market Street")
            .townOrCity("Hull")
            .postcode("HU1 1AA")
            .countryCode("GB")
            .phone("+441482000000")
            .email("ops@acme.example")
            .organisationId("ORG-001")
            .status(AddressStatus.ACTIVE)
            .createdAt(Instant.parse("2026-07-01T09:00:00Z"))
            .modifiedAt(Instant.parse("2026-07-02T10:30:00Z"))
            .build();

    OperatorResponse response = operatorMapper.toResponse(entity);

    assertThat(response.addressLine2()).isNull();
    assertThat(response.county()).isNull();
    assertThat(response.name()).isEqualTo("Acme Livestock");
  }

  @Test
  void toResponse_failsLoudWhenARequiredFieldIsNull() {
    Address entity =
        Address.builder()
            .id("665f1c2ab3e4d51a2c9d0e77")
            .name("Acme Livestock")
            .addressLine1("1 Market Street")
            .townOrCity("Hull")
            .postcode("HU1 1AA")
            .countryCode("GB")
            .phone(null)
            .email("ops@acme.example")
            .organisationId("ORG-001")
            .status(AddressStatus.ACTIVE)
            .createdAt(Instant.parse("2026-07-01T09:00:00Z"))
            .modifiedAt(Instant.parse("2026-07-02T10:30:00Z"))
            .build();

    assertThatThrownBy(() -> operatorMapper.toResponse(entity))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("phone");
  }

  @Test
  void response_serialisesEntirelyInCamelCaseWithADerivedDeletedBooleanAndNoStatusEnum()
      throws Exception {
    OperatorResponse response = operatorMapper.toResponse(sampleEntity(AddressStatus.ACTIVE));

    JsonNode json = mapper.valueToTree(response);

    assertThat(json.get("addressLine1").asText()).isEqualTo("1 Market Street");
    assertThat(json.get("addressLine2").asText()).isEqualTo("Docklands");
    assertThat(json.has("address_line_1")).isFalse();

    assertThat(json.get("townOrCity").asText()).isEqualTo("Hull");
    assertThat(json.get("countryCode").asText()).isEqualTo("GB");
    assertThat(json.get("phone").asText()).isEqualTo("+441482000000");
    assertThat(json.get("organisationId").asText()).isEqualTo("ORG-001");
    assertThat(json.get("createdAt").asText()).isEqualTo("2026-07-01T09:00:00Z");
    assertThat(json.get("modifiedAt").asText()).isEqualTo("2026-07-02T10:30:00Z");

    assertThat(json.get("deleted").asBoolean()).isFalse();
    assertThat(json.has("status")).isFalse();

    assertThat(json.has("operatorType")).isFalse();
    assertThat(json.has("transporterCategory")).isFalse();
    assertThat(json.has("approvalNumber")).isFalse();
    assertThat(json.has("crn")).isFalse();
  }

  @Test
  void response_serialisesInstantFieldsAsIso8601NotEpochMillis() throws Exception {
    OperatorResponse response = operatorMapper.toResponse(sampleEntity(AddressStatus.ACTIVE));

    JsonNode json = mapper.valueToTree(response);

    assertThat(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();
    assertThat(json.get("createdAt").isTextual()).isTrue();
    assertThat(json.get("modifiedAt").isTextual()).isTrue();
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
          "organisationId": "OTHER-ORG",
          "status": "DELETED",
          "deleted": true,
          "createdAt": "2026-01-01T00:00:00Z",
          "modifiedAt": "2026-01-02T00:00:00Z"
        }
        """;

    AddressRequest request = mapper.readValue(body, AddressRequest.class);
    Address entity = operatorMapper.toEntity(request);

    assertThat(entity.getName()).isEqualTo("Port Importers Ltd");
    assertThat(entity.getAddressLine1()).isEqualTo("7 Quay Road");
    assertThat(entity.getAddressLine2()).isEqualTo("Berth 4");
    assertThat(entity.getTownOrCity()).isEqualTo("Dover");
    assertThat(entity.getCounty()).isEqualTo("Kent");
    assertThat(entity.getPostcode()).isEqualTo("CT16 1AA");
    assertThat(entity.getCountryCode()).isEqualTo("GB");
    assertThat(entity.getPhone()).isEqualTo("+441304000000");
    assertThat(entity.getEmail()).isEqualTo("imports@port.example");
    assertThat(entity.getId()).isNull();
    assertThat(entity.getStatus()).isNull();
    assertThat(entity.getOrganisationId()).isNull();
    assertThat(entity.getCreatedAt()).isNull();
    assertThat(entity.getModifiedAt()).isNull();
  }
}
