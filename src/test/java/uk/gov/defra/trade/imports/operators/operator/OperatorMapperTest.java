package uk.gov.defra.trade.imports.operators.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OperatorMapperTest {

  private final ObjectMapper mapper =
      new ObjectMapper()
          .findAndRegisterModules()
          .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private Operator sampleEntity() {
    return Operator.builder()
        .id("665f1c2ab3e4d51a2c9d0e77")
        .operatorType(OperatorType.TRANSPORTER)
        .name("Acme Livestock Haulage")
        .addressLine1("1 Market Street")
        .addressLine2("Docklands")
        .town("Hull")
        .county("East Riding")
        .postcode("HU1 1AA")
        .country("United Kingdom")
        .telephone("+441482000000")
        .email("ops@acme.example")
        .approvalNumber("APR-123")
        .transporterCategory(TransporterCategory.COMMERCIAL)
        .crn("CRN-001")
        .organisationId("ORG-001")
        .status(OperatorStatus.ACTIVE)
        .createdAt(Instant.parse("2026-07-01T09:00:00Z"))
        .modifiedAt(Instant.parse("2026-07-02T10:30:00Z"))
        .build();
  }

  @Test
  void toResponse_copiesEveryFieldFromTheEntity() {
    OperatorResponse response = OperatorMapper.toResponse(sampleEntity());

    assertThat(response.id()).isEqualTo("665f1c2ab3e4d51a2c9d0e77");
    assertThat(response.operatorType()).isEqualTo(OperatorType.TRANSPORTER);
    assertThat(response.name()).isEqualTo("Acme Livestock Haulage");
    assertThat(response.addressLine1()).isEqualTo("1 Market Street");
    assertThat(response.addressLine2()).isEqualTo("Docklands");
    assertThat(response.town()).isEqualTo("Hull");
    assertThat(response.county()).isEqualTo("East Riding");
    assertThat(response.postcode()).isEqualTo("HU1 1AA");
    assertThat(response.country()).isEqualTo("United Kingdom");
    assertThat(response.telephone()).isEqualTo("+441482000000");
    assertThat(response.email()).isEqualTo("ops@acme.example");
    assertThat(response.approvalNumber()).isEqualTo("APR-123");
    assertThat(response.transporterCategory()).isEqualTo(TransporterCategory.COMMERCIAL);
    assertThat(response.crn()).isEqualTo("CRN-001");
    assertThat(response.organisationId()).isEqualTo("ORG-001");
    assertThat(response.status()).isEqualTo(OperatorStatus.ACTIVE);
    assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-07-01T09:00:00Z"));
    assertThat(response.modifiedAt()).isEqualTo(Instant.parse("2026-07-02T10:30:00Z"));
  }

  @Test
  void response_serialisesEntirelyInCamelCaseWithUpperSnakeEnums() throws Exception {
    OperatorResponse response = OperatorMapper.toResponse(sampleEntity());

    JsonNode json = mapper.valueToTree(response);

    // Every field serialises under its Java name — the digit-bearing fields are addressLine1 /
    // addressLine2, never the snake_case address_line_1.
    assertThat(json.has("addressLine1")).isTrue();
    assertThat(json.has("addressLine2")).isTrue();
    assertThat(json.has("address_line_1")).isFalse();
    assertThat(json.has("address_line1")).isFalse();

    assertThat(json.has("operatorType")).isTrue();
    assertThat(json.has("transporterCategory")).isTrue();
    assertThat(json.has("approvalNumber")).isTrue();
    assertThat(json.has("organisationId")).isTrue();
    assertThat(json.has("createdAt")).isTrue();
    assertThat(json.has("modifiedAt")).isTrue();

    assertThat(json.get("operatorType").asText()).isEqualTo("TRANSPORTER");
    assertThat(json.get("transporterCategory").asText()).isEqualTo("COMMERCIAL");
    assertThat(json.get("status").asText()).isEqualTo("ACTIVE");
  }

  @Test
  void request_deserialisesFromCamelCaseAndMapsOntoAnEntity() throws Exception {
    String body =
        """
        {
          "operatorType": "IMPORTER",
          "name": "Port Importers Ltd",
          "addressLine1": "7 Quay Road",
          "addressLine2": "Berth 4",
          "town": "Dover",
          "county": "Kent",
          "postcode": "CT16 1AA",
          "country": "United Kingdom",
          "telephone": "+441304000000",
          "email": "imports@port.example",
          "id": "ignored-server-field",
          "status": "DELETED"
        }
        """;

    OperatorRequest request = mapper.readValue(body, OperatorRequest.class);
    Operator entity = OperatorMapper.toEntity(request);

    assertThat(entity.getOperatorType()).isEqualTo(OperatorType.IMPORTER);
    assertThat(entity.getName()).isEqualTo("Port Importers Ltd");
    assertThat(entity.getAddressLine1()).isEqualTo("7 Quay Road");
    assertThat(entity.getAddressLine2()).isEqualTo("Berth 4");
    assertThat(entity.getTown()).isEqualTo("Dover");
    assertThat(entity.getCounty()).isEqualTo("Kent");
    assertThat(entity.getPostcode()).isEqualTo("CT16 1AA");
    assertThat(entity.getCountry()).isEqualTo("United Kingdom");
    assertThat(entity.getTelephone()).isEqualTo("+441304000000");
    assertThat(entity.getEmail()).isEqualTo("imports@port.example");
    // Server-assigned fields in the body are ignored, never mapped onto the entity.
    assertThat(entity.getId()).isNull();
    assertThat(entity.getStatus()).isNull();
    assertThat(entity.getCrn()).isNull();
  }

  @Test
  void request_rejectsAnUnknownEnumValue() {
    String body =
        """
        {
          "operatorType": "NOT_A_REAL_TYPE",
          "name": "x",
          "addressLine1": "y",
          "town": "z",
          "postcode": "p",
          "country": "c",
          "telephone": "t",
          "email": "e"
        }
        """;

    assertThatThrownBy(() -> mapper.readValue(body, OperatorRequest.class))
        .isInstanceOf(InvalidFormatException.class);
  }
}
