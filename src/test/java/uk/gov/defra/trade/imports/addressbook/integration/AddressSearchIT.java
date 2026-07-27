package uk.gov.defra.trade.imports.addressbook.integration;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.OperatorRepository;

/**
 * Full-stack search integration tests for {@code GET /organisation/{orgId}/addresses?q=} (EUDPA-186
 * m2-01). Pins case-insensitive partial-word regex search over name/townOrCity/postcode, optional
 * {@code countryCode} matching, org-scoping, ACTIVE-only exclusion of tombstones, and pagination
 * preserving the active search terms.
 */
class AddressSearchIT extends IntegrationBase {

  private static final String ORG_HEADER = "Trade-Imports-Organisation-Id";
  private static final String ORG = "5a8d2b19-6f4e-4d21-9c1b-7e3f0a2d5c88";
  private static final String OTHER_ORG = "9c1b7e3f-0a2d-5c88-5a8d-2b196f4e4d21";

  @Autowired private OperatorRepository repository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

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
            .createdAt(Instant.parse("2026-07-14T09:15:27Z"))
            .modifiedAt(Instant.parse("2026-07-14T09:15:27Z"))
            .build();
    return repository.save(address);
  }

  @Test
  void partialWordSearchMatchesNameCaseInsensitively() throws Exception {
    Address match =
        save(
            ORG,
            AddressStatus.ACTIVE,
            "Green Farm",
            "Inverness",
            "IV2 3JH",
            "GB",
            "14 Drover's Way");
    save(ORG, AddressStatus.ACTIVE, "Blue Barn", "Perth", "PH1 5AA", "GB", "2 Market Street");

    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG).header(ORG_HEADER, ORG).param("q", "gree"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(match.getId()))
        .andExpect(jsonPath("$.items[0].name").value("Green Farm"));
  }

  @Test
  void searchMatchesTownOrCityAndPostcodeButNotAddressLineFields() throws Exception {
    Address byTown =
        save(
            ORG,
            AddressStatus.ACTIVE,
            "Highland Livestock Ltd",
            "Greenwich",
            "SE10 9NN",
            "GB",
            "1 Wharf Road");
    Address byPostcode =
        save(
            ORG,
            AddressStatus.ACTIVE,
            "Lowland Cattle Co",
            "Perth",
            "GREEN1 2AB",
            "GB",
            "3 High Street");
    save(
        ORG,
        AddressStatus.ACTIVE,
        "Hidden Match",
        "Edinburgh",
        "EH1 1AA",
        "GB",
        "Green Lane Industrial Estate");

    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG).header(ORG_HEADER, ORG).param("q", "green"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(2))
        .andExpect(jsonPath("$.items[*].id", hasItem(byTown.getId())))
        .andExpect(jsonPath("$.items[*].id", hasItem(byPostcode.getId())));
  }

  @Test
  void countryCodeParamMatchesStoredAlpha2Code() throws Exception {
    Address french =
        save(
            ORG,
            AddressStatus.ACTIVE,
            "Paris Depot",
            "Paris",
            "75001",
            "FR",
            "1 Rue de Rivoli");
    save(
        ORG,
        AddressStatus.ACTIVE,
        "London Depot",
        "London",
        "SW1A 1AA",
        "GB",
        "10 Downing Street");

    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORG)
                .header(ORG_HEADER, ORG)
                .param("countryCode", "FR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(french.getId()))
        .andExpect(jsonPath("$.items[0].countryCode").value("FR"));
  }

  @Test
  void combinedQueryAndCountryCodeMatchesEitherTextOrCountryCode() throws Exception {
    Address french =
        save(
            ORG,
            AddressStatus.ACTIVE,
            "Paris Depot",
            "Paris",
            "75001",
            "FR",
            "1 Rue de Rivoli");
    save(
        ORG,
        AddressStatus.ACTIVE,
        "London Depot",
        "London",
        "SW1A 1AA",
        "GB",
        "10 Downing Street");

    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORG)
                .header(ORG_HEADER, ORG)
                .param("q", "France")
                .param("countryCode", "FR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(french.getId()));
  }

  @Test
  void searchIsOrgScopedAndExcludesSoftDeleted() throws Exception {
    Address live =
        save(
            ORG,
            AddressStatus.ACTIVE,
            "Green Farm",
            "Inverness",
            "IV2 3JH",
            "GB",
            "14 Drover's Way");
    Address deleted =
        save(
            ORG,
            AddressStatus.DELETED,
            "Green Ghost",
            "Inverness",
            "IV2 3JH",
            "GB",
            "99 Deleted Lane");
    save(
        OTHER_ORG,
        AddressStatus.ACTIVE,
        "Green Other Org",
        "Glasgow",
        "G1 1AA",
        "GB",
        "1 Other Street");

    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG).header(ORG_HEADER, ORG).param("q", "green"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(live.getId()))
        .andExpect(jsonPath("$.items[*].id", not(hasItem(deleted.getId()))))
        .andExpect(jsonPath("$.items[*].deleted", everyItem(is(false))));
  }

  @Test
  void searchResultsPaginateAt25PreservingQueryAndCountryCode() throws Exception {
    for (int i = 0; i < 30; i++) {
      save(
          ORG,
          AddressStatus.ACTIVE,
          "Green Farm " + i,
          "Inverness",
          "IV2 3JH",
          "GB",
          "14 Drover's Way");
    }

    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORG)
                .header(ORG_HEADER, ORG)
                .param("q", "green")
                .param("countryCode", "GB")
                .param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(25))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pageSize").value(25))
        .andExpect(jsonPath("$.totalItems").value(30))
        .andExpect(jsonPath("$.totalPages").value(2));

    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORG)
                .header(ORG_HEADER, ORG)
                .param("q", "green")
                .param("countryCode", "GB")
                .param("page", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(5))
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.totalItems").value(30))
        .andExpect(jsonPath("$.totalPages").value(2));
  }
}
