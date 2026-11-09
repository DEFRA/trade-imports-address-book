package uk.gov.defra.trade.imports.addressbook.transporter;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.defra.trade.imports.addressbook.transporter.TransporterTestSupport.storedTransporter;
import static uk.gov.defra.trade.imports.addressbook.transporter.TransporterTestSupport.validCommercialRequest;
import static uk.gov.defra.trade.imports.addressbook.transporter.TransporterTestSupport.validPrivateRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import uk.gov.defra.trade.imports.addressbook.configuration.JacksonConfig;

class TransporterMapperTest {

  private final TransporterMapper transporterMapper = Mappers.getMapper(TransporterMapper.class);

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
    new JacksonConfig().apiJsonConventions().customize(builder);
    mapper = builder.build().findAndRegisterModules();
  }

  @Test
  void toResponse_copiesEveryFieldFromTheEntityAndDerivesDeletedFalseForAnActiveRow() {
    TransporterResponse response =
        transporterMapper.toResponse(storedTransporter("ORG-001", TransporterStatus.ACTIVE).build());

    assertThat(response.id()).isEqualTo("775f1c2ab3e4d51a2c9d0e88");
    assertThat(response.name()).isEqualTo("Highland Livestock Ltd");
    assertThat(response.type()).isEqualTo(TransporterType.COMMERCIAL);
    assertThat(response.approvalNumber()).isEqualTo("UK/BELF/T2/00104115");
    assertThat(response.countryCode()).isEqualTo("GB-NIR");
    assertThat(response.organisationId()).isEqualTo("ORG-001");
    assertThat(response.deleted()).isFalse();
    assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-07-01T09:00:00Z"));
    assertThat(response.modifiedAt()).isEqualTo(Instant.parse("2026-07-02T10:30:00Z"));
  }

  @Test
  void toResponse_derivesDeletedTrueForATombstone() {
    TransporterResponse response =
        transporterMapper.toResponse(
            storedTransporter("ORG-001", TransporterStatus.DELETED).build());

    assertThat(response.deleted()).isTrue();
  }

  @Test
  void response_serialisesEntirelyInCamelCaseWithADerivedDeletedBooleanAndNoRolesOrAddressType() {
    JsonNode json =
        mapper.valueToTree(
            transporterMapper.toResponse(
                storedTransporter("ORG-001", TransporterStatus.ACTIVE).build()));

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
            "type",
            "approvalNumber",
            "organisationId",
            "deleted",
            "createdAt",
            "modifiedAt");
    assertThat(json.get("type").asText()).isEqualTo("COMMERCIAL");
  }

  @Test
  void toEntity_mapsAPrivateRequestCarryingNoApprovalNumber() {
    Transporter entity = transporterMapper.toEntity(validPrivateRequest().build());

    assertThat(entity.getType()).isEqualTo(TransporterType.PRIVATE);
    assertThat(entity.getApprovalNumber()).isNull();
    assertThat(entity.getId()).isNull();
    assertThat(entity.getOrganisationId()).isNull();
    assertThat(entity.getStatus()).isNull();
  }

  @Test
  void toEntity_mapsACommercialRequestCarryingItsApprovalNumberAndFixedCountry() {
    Transporter entity = transporterMapper.toEntity(validCommercialRequest().build());

    assertThat(entity.getType()).isEqualTo(TransporterType.COMMERCIAL);
    assertThat(entity.getApprovalNumber()).isEqualTo("UK/BELF/T2/00104115");
    assertThat(entity.getCountryCode()).isEqualTo("GB-NIR");
  }

  @Test
  void updateEntity_replacesAllMutableFieldsAndPreservesServerOwnedFields() {
    Transporter existing =
        storedTransporter("ORG-001", TransporterStatus.ACTIVE).version(2L).build();

    transporterMapper.updateEntity(validPrivateRequest().name("Lowland Haulage").build(), existing);

    assertThat(existing.getName()).isEqualTo("Lowland Haulage");
    assertThat(existing.getType()).isEqualTo(TransporterType.PRIVATE);
    assertThat(existing.getApprovalNumber()).isNull();
    assertThat(existing.getId()).isEqualTo("775f1c2ab3e4d51a2c9d0e88");
    assertThat(existing.getOrganisationId()).isEqualTo("ORG-001");
    assertThat(existing.getStatus()).isEqualTo(TransporterStatus.ACTIVE);
    assertThat(existing.getVersion()).isEqualTo(2L);
  }

  private static Set<String> propertyNames(JsonNode json) {
    Set<String> names = new HashSet<>();
    json.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
