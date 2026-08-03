package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.OperatorRepository;

class AddressSearchIT extends IntegrationBase {

  @Autowired private OperatorRepository repository;
  @Autowired private ObjectMapper objectMapper;

  private Address save(
      String org,
      AddressStatus status,
      String name,
      String townOrCity,
      String postcode,
      String countryCode,
      String addressLine1) {
    Address address =
        Address.builder()
            .name(name)
            .addressLine1(addressLine1)
            .townOrCity(townOrCity)
            .postcode(postcode)
            .countryCode(countryCode)
            .phone("+44 1463 234567")
            .email("exports@highlandlivestock.example.com")
            .organisationId(org)
            .status(status)
            .build();
    return repository.save(address);
  }

  @Test
  void partialWordSearchMatchesNameCaseInsensitively() throws Exception {
    Address match =
        save(ORGANISATION_ID, AddressStatus.ACTIVE, "Green Farm", "Inverness", "IV2 3JH", "GB", "14 Drover's Way");
    save(ORGANISATION_ID, AddressStatus.ACTIVE, "Blue Barn", "Perth", "PH1 5AA", "GB", "2 Market Street");

    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .param("q", "gree"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(match.getId()));
  }

  @Test
  void searchTermIsTreatedAsLiteralNotRegex() throws Exception {
    save(ORGANISATION_ID, AddressStatus.ACTIVE, "Green Farm", "Inverness", "IV2 3JH", "GB", "14 Drover's Way");
    save(ORGANISATION_ID, AddressStatus.ACTIVE, "Blue Barn", "Perth", "PH1 5AA", "GB", "2 Market Street");

    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .param("q", ".*"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));
  }

  @Test
  void countryCodeParamMatchesStoredAlpha2CodeAndExcludesOtherOrgs() throws Exception {
    Address french =
        save(ORGANISATION_ID, AddressStatus.ACTIVE, "Paris Depot", "Paris", "75001", "FR", "1 Rue de Rivoli");
    save(ORGANISATION_ID, AddressStatus.ACTIVE, "London Depot", "London", "SW1A 1AA", "GB", "10 Downing Street");
    Address otherOrgFrench =
        save(OTHER_ORG, AddressStatus.ACTIVE, "Other FR", "Lyon", "69001", "FR", "2 Other Street");

    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .param("countryCode", "FR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(french.getId()))
        .andExpect(jsonPath("$.items[*].id", not(hasItem(otherOrgFrench.getId()))));
  }

  @Test
  void countryCodeSearchExcludesSoftDeletedRows() throws Exception {
    save(ORGANISATION_ID, AddressStatus.ACTIVE, "Paris Depot", "Paris", "75001", "FR", "1 Rue de Rivoli");
    Address deleted =
        save(ORGANISATION_ID, AddressStatus.DELETED, "Deleted FR", "Paris", "75002", "FR", "9 Deleted Lane");

    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .param("countryCode", "FR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[*].id", not(hasItem(deleted.getId()))));
  }

  @Test
  void combinedQueryAndCountryCodeMatchesTextOrCountryCode() throws Exception {
    Address french =
        save(ORGANISATION_ID, AddressStatus.ACTIVE, "Paris Depot", "Paris", "75001", "FR", "1 Rue de Rivoli");
    Address textOnly =
        save(
            ORGANISATION_ID,
            AddressStatus.ACTIVE,
            "France Freight",
            "London",
            "SW1A 1AA",
            "GB",
            "1 France Yard");

    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .param("q", "France")
                .param("countryCode", "FR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(2))
        .andExpect(jsonPath("$.items[*].id", hasItem(french.getId())))
        .andExpect(jsonPath("$.items[*].id", hasItem(textOnly.getId())));
  }

  @Test
  void searchIsOrgScopedAndExcludesSoftDeletedForQueryPath() throws Exception {
    Address live =
        save(ORGANISATION_ID, AddressStatus.ACTIVE, "Green Farm", "Inverness", "IV2 3JH", "GB", "14 Drover's Way");
    Address deleted =
        save(ORGANISATION_ID, AddressStatus.DELETED, "Green Ghost", "Inverness", "IV2 3JH", "GB", "99 Deleted Lane");
    save(OTHER_ORG, AddressStatus.ACTIVE, "Green Other Org", "Glasgow", "G1 1AA", "GB", "1 Other Street");

    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .param("q", "green"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(live.getId()))
        .andExpect(jsonPath("$.items[*].id", not(hasItem(deleted.getId()))));
  }

  @Test
  void searchResultsPaginateWithDisjointPages() throws Exception {
    Set<String> allIds = new HashSet<>();
    for (int i = 0; i < 30; i++) {
      allIds.add(
          save(
                  ORGANISATION_ID,
                  AddressStatus.ACTIVE,
                  "Green Farm " + i,
                  "Inverness",
                  "IV2 3JH",
                  "GB",
                  "14 Drover's Way")
              .getId());
    }

    String page1Body =
        mockMvc
            .perform(
                get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                    .header(ORG_HEADER, ORGANISATION_ID)
                    .param("q", "green")
                    .param("countryCode", "GB")
                    .param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(25))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String page2Body =
        mockMvc
            .perform(
                get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                    .header(ORG_HEADER, ORGANISATION_ID)
                    .param("q", "green")
                    .param("countryCode", "GB")
                    .param("page", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(5))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Set<String> pageIds = new HashSet<>();
    pageIds.addAll(readIds(page1Body));
    pageIds.addAll(readIds(page2Body));
    assertThat(pageIds).containsExactlyInAnyOrderElementsOf(allIds);
  }

  private Set<String> readIds(String body) throws Exception {
    Set<String> ids = new HashSet<>();
    JsonNode items = objectMapper.readTree(body).get("items");
    items.forEach(item -> ids.add(item.get("id").asText()));
    return ids;
  }
}
