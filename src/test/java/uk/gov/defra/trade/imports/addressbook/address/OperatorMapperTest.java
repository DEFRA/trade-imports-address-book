package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class OperatorMapperTest {

  private final OperatorMapper operatorMapper = Mappers.getMapper(OperatorMapper.class);

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
    builder
        .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
        .failOnUnknownProperties(false)
        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper = builder.build().findAndRegisterModules();
  }

  private static Address.AddressBuilder seededEntity(AddressStatus status) {
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
        .addressType(AddressType.DESTINATION_CONSIGNEE_OR_IMPORTER)
        .uses(List.of(AddressUse.IMPORTER, AddressUse.CONSIGNEE))
        .organisationId("ORG-001")
        .status(status)
        .createdAt(Instant.parse("2026-07-01T09:00:00Z"))
        .modifiedAt(Instant.parse("2026-07-02T10:30:00Z"));
  }

  private static AddressRequest.AddressRequestBuilder replacementRequest() {
    return AddressRequest.builder()
        .name("Lowland Cattle Co")
        .addressLine1("2 Market Street")
        .townOrCity("Perth")
        .postcode("PH1 5AA")
        .countryCode("IE")
        .phone("+44 1738 111222")
        .email("ops@lowlandcattle.example.com");
  }

  private OperatorResponse responseFor(AddressStatus status) {
    return operatorMapper.toResponse(seededEntity(status).build());
  }

  @Test
  void toResponse_copiesEveryFieldFromTheEntityAndDerivesDeletedFalseForAnActiveRow() {
    OperatorResponse response = responseFor(AddressStatus.ACTIVE);

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
    assertThat(response.addressType()).isEqualTo(AddressType.DESTINATION_CONSIGNEE_OR_IMPORTER);
    assertThat(response.uses()).containsExactly(AddressUse.IMPORTER, AddressUse.CONSIGNEE);
    assertThat(response.organisationId()).isEqualTo("ORG-001");
    assertThat(response.deleted()).isFalse();
    assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-07-01T09:00:00Z"));
    assertThat(response.modifiedAt()).isEqualTo(Instant.parse("2026-07-02T10:30:00Z"));
  }

  @Test
  void toResponse_derivesDeletedTrueForATombstone() {
    OperatorResponse response = responseFor(AddressStatus.DELETED);

    assertThat(response.deleted()).isTrue();
  }

  @Test
  void toResponse_mapsOptionalFieldsWhenAbsent() {
    Address entity =
        seededEntity(AddressStatus.ACTIVE)
            .addressLine2(null)
            .county(null)
            .addressType(null)
            .uses(null)
            .build();

    OperatorResponse response = operatorMapper.toResponse(entity);

    assertThat(response.addressLine2()).isNull();
    assertThat(response.county()).isNull();
    assertThat(response.addressType()).isNull();
    assertThat(response.uses()).isNull();
    assertThat(response.name()).isEqualTo("Acme Livestock");
  }

  @Test
  void toResponse_failsLoudWhenARequiredFieldIsNull() {
    Address entity = seededEntity(AddressStatus.ACTIVE).phone(null).build();

    assertThatThrownBy(() -> operatorMapper.toResponse(entity))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("phone");
  }

  @Test
  void response_serialisesEntirelyInCamelCaseWithADerivedDeletedBooleanAndNoStatusEnum() {
    JsonNode json = mapper.valueToTree(responseFor(AddressStatus.ACTIVE));

    assertThat(propertyNames(json))
        .containsExactlyInAnyOrder(
            "id",
            "name",
            "addressLine1",
            "addressLine2",
            "townOrCity",
            "county",
            "postcode",
            "countryCode",
            "phone",
            "email",
            "addressType",
            "uses",
            "organisationId",
            "deleted",
            "createdAt",
            "modifiedAt");
    assertThat(json.get("addressLine1").asText()).isEqualTo("1 Market Street");
    assertThat(json.get("addressLine2").asText()).isEqualTo("Docklands");
    assertThat(json.get("townOrCity").asText()).isEqualTo("Hull");
    assertThat(json.get("countryCode").asText()).isEqualTo("GB");
    assertThat(json.get("phone").asText()).isEqualTo("+441482000000");
    assertThat(json.get("addressType").asText()).isEqualTo("DESTINATION_CONSIGNEE_OR_IMPORTER");
    assertThat(json.get("uses"))
        .extracting(JsonNode::asText)
        .containsExactly("IMPORTER", "CONSIGNEE");
    assertThat(json.get("organisationId").asText()).isEqualTo("ORG-001");
    assertThat(json.get("createdAt").asText()).isEqualTo("2026-07-01T09:00:00Z");
    assertThat(json.get("modifiedAt").asText()).isEqualTo("2026-07-02T10:30:00Z");
    assertThat(json.get("deleted").asBoolean()).isFalse();
  }

  @Test
  void response_serialisesInstantFieldsAsIso8601NotEpochMillis() {
    JsonNode json = mapper.valueToTree(responseFor(AddressStatus.ACTIVE));

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

  @Test
  void toEntity_mapsTheWireAddressTypeAndUsesOntoEnumsInOrder() {
    AddressRequest request =
        replacementRequest()
            .addressType("BRANCH")
            .uses(List.of("BRANCH_ADDRESS", "CONSIGNOR", "PLACE_OF_ORIGIN"))
            .build();

    Address entity = operatorMapper.toEntity(request);

    assertThat(entity.getAddressType()).isEqualTo(AddressType.BRANCH);
    assertThat(entity.getUses())
        .containsExactly(
            AddressUse.BRANCH_ADDRESS, AddressUse.CONSIGNOR, AddressUse.PLACE_OF_ORIGIN);
  }

  @Test
  void updateEntity_replacesAllMutableFieldsAndClearsOmittedOptionals() {
    Address existing = seededEntity(AddressStatus.ACTIVE).version(2L).build();

    operatorMapper.updateEntity(replacementRequest().build(), existing);

    assertThat(existing.getName()).isEqualTo("Lowland Cattle Co");
    assertThat(existing.getAddressLine1()).isEqualTo("2 Market Street");
    assertThat(existing.getAddressLine2()).isNull();
    assertThat(existing.getTownOrCity()).isEqualTo("Perth");
    assertThat(existing.getCounty()).isNull();
    assertThat(existing.getPostcode()).isEqualTo("PH1 5AA");
    assertThat(existing.getCountryCode()).isEqualTo("IE");
    assertThat(existing.getPhone()).isEqualTo("+44 1738 111222");
    assertThat(existing.getEmail()).isEqualTo("ops@lowlandcattle.example.com");
    assertThat(existing.getAddressType()).isNull();
    assertThat(existing.getUses()).isNull();
    assertThat(existing.getId()).isEqualTo("665f1c2ab3e4d51a2c9d0e77");
    assertThat(existing.getOrganisationId()).isEqualTo("ORG-001");
    assertThat(existing.getStatus()).isEqualTo(AddressStatus.ACTIVE);
    assertThat(existing.getCreatedAt()).isEqualTo(Instant.parse("2026-07-01T09:00:00Z"));
    assertThat(existing.getModifiedAt()).isEqualTo(Instant.parse("2026-07-02T10:30:00Z"));
    assertThat(existing.getVersion()).isEqualTo(2L);
  }

  @Test
  void updateEntity_replacesTheStoredAddressTypeAndUsesWithTheRequestsSet() {
    Address existing = seededEntity(AddressStatus.ACTIVE).build();
    AddressRequest request =
        replacementRequest()
            .addressType("ORIGIN_OR_CONSIGNOR")
            .uses(List.of("PLACE_OF_ORIGIN", "CONSIGNOR", "IMPORTER"))
            .build();

    operatorMapper.updateEntity(request, existing);

    assertThat(existing.getAddressType()).isEqualTo(AddressType.ORIGIN_OR_CONSIGNOR);
    assertThat(existing.getUses())
        .containsExactly(AddressUse.PLACE_OF_ORIGIN, AddressUse.CONSIGNOR, AddressUse.IMPORTER);
  }

  private static Set<String> propertyNames(JsonNode json) {
    Set<String> names = new HashSet<>();
    json.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
