package uk.gov.defra.trade.imports.addressbook.address;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.defra.trade.imports.addressbook.RecordNullGuards.assertEveryRequiredComponentRejectsNull;
import static uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport.STORED_CREATED_AT;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AddressResponseTest {

  private static AddressResponse.AddressResponseBuilder validResponse() {
    return AddressResponse.builder()
        .id("665f1c2ab3e4d51a2c9d0e77")
        .name("Acme Livestock")
        .addressLine1("1 Market Street")
        .townOrCity("Hull")
        .postcode("HU1 1AA")
        .countryCode("GB")
        .phone("+441482000000")
        .email("ops@acme.example")
        .organisationId("ORG-001")
        .createdAt(STORED_CREATED_AT)
        .modifiedAt(STORED_CREATED_AT);
  }

  @Test
  void compactConstructor_rejectsNullOnEveryRequiredField() {
    assertEveryRequiredComponentRejectsNull(
        AddressResponse.class,
        Map.ofEntries(
            Map.entry("id", "665f1c2ab3e4d51a2c9d0e77"),
            Map.entry("name", "Acme Livestock"),
            Map.entry("addressLine1", "1 Market Street"),
            Map.entry("addressLine2", "Unit 3"),
            Map.entry("townOrCity", "Hull"),
            Map.entry("county", "East Riding of Yorkshire"),
            Map.entry("postcode", "HU1 1AA"),
            Map.entry("countryCode", "GB"),
            Map.entry("phone", "+441482000000"),
            Map.entry("email", "ops@acme.example"),
            Map.entry("roles", List.of(AddressRole.CONSIGNOR)),
            Map.entry("organisationId", "ORG-001"),
            Map.entry("deleted", Boolean.FALSE),
            Map.entry("createdAt", STORED_CREATED_AT),
            Map.entry("modifiedAt", STORED_CREATED_AT)),
        Set.of("addressLine2", "county", "roles"));
  }

  @Test
  void compactConstructor_allowsNullOptionalFields() {
    AddressResponse response = validResponse().addressLine2(null).county(null).build();

    assertThat(response.addressLine2()).isNull();
    assertThat(response.county()).isNull();
  }

  @Test
  void compactConstructor_readsNullRolesAsAnEmptyList() {
    AddressResponse response = validResponse().roles(null).build();

    assertThat(response.roles()).isEmpty();
  }
}
