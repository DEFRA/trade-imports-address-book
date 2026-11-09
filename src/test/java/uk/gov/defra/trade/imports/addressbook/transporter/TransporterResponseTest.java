package uk.gov.defra.trade.imports.addressbook.transporter;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.defra.trade.imports.addressbook.RecordNullGuards.assertEveryRequiredComponentRejectsNull;
import static uk.gov.defra.trade.imports.addressbook.transporter.TransporterTestSupport.STORED_CREATED_AT;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransporterResponseTest {

  private static TransporterResponse.TransporterResponseBuilder validResponse() {
    return TransporterResponse.builder()
        .id("775f1c2ab3e4d51a2c9d0e88")
        .name("Highland Haulage Ltd")
        .addressLine1("14 Drover's Way")
        .townOrCity("Inverness")
        .postcode("IV2 3JH")
        .countryCode("GB-NIR")
        .phone("+44 1463 234567")
        .email("haulage@highland.example.com")
        .type(TransporterType.COMMERCIAL)
        .organisationId("ORG-001")
        .createdAt(STORED_CREATED_AT)
        .modifiedAt(STORED_CREATED_AT);
  }

  @Test
  void compactConstructor_rejectsNullOnEveryRequiredField() {
    assertEveryRequiredComponentRejectsNull(
        TransporterResponse.class,
        Map.ofEntries(
            Map.entry("id", "775f1c2ab3e4d51a2c9d0e88"),
            Map.entry("name", "Highland Haulage Ltd"),
            Map.entry("addressLine1", "14 Drover's Way"),
            Map.entry("addressLine2", "Unit 3"),
            Map.entry("townOrCity", "Inverness"),
            Map.entry("county", "Highland"),
            Map.entry("postcode", "IV2 3JH"),
            Map.entry("countryCode", "GB-NIR"),
            Map.entry("phone", "+44 1463 234567"),
            Map.entry("email", "haulage@highland.example.com"),
            Map.entry("type", TransporterType.COMMERCIAL),
            Map.entry("approvalNumber", "UK/BELF/T2/00104115"),
            Map.entry("organisationId", "ORG-001"),
            Map.entry("deleted", Boolean.FALSE),
            Map.entry("createdAt", STORED_CREATED_AT),
            Map.entry("modifiedAt", STORED_CREATED_AT)),
        Set.of("addressLine2", "county", "approvalNumber"));
  }

  @Test
  void compactConstructor_allowsNullOptionalFields() {
    TransporterResponse response =
        validResponse().addressLine2(null).county(null).approvalNumber(null).build();

    assertThat(response.addressLine2()).isNull();
    assertThat(response.county()).isNull();
    assertThat(response.approvalNumber()).isNull();
  }
}
