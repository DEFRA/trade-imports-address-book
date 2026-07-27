package uk.gov.defra.trade.imports.operators.integration;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import uk.gov.defra.trade.imports.operators.operator.Address;
import uk.gov.defra.trade.imports.operators.operator.AddressStatus;
import uk.gov.defra.trade.imports.operators.operator.OperatorRepository;

/**
 * Full-stack list integration test for {@code GET /organisation/{orgId}/addresses}. Pins the
 * paginated, newest-first, organisation-scoped, ACTIVE-only listing: 30 seeded addresses paginate
 * 25 + 5 across two pages at the server-config page size (cv-025), DELETED tombstones are excluded,
 * another organisation's addresses are invisible, an out-of-range or non-numeric {@code page} is a
 * 400 bad-request problem with no {@code errors} map, and a supplied page-size request parameter is
 * never honoured. Search ({@code ?q=}) is not part of this listing — it is M2 (EUDPA-186).
 */
class OperatorListIT extends IntegrationBase {

  private static final String ORG_HEADER = "Trade-Imports-Organisation-Id";
  private static final String ORG = "5a8d2b19-6f4e-4d21-9c1b-7e3f0a2d5c88";
  private static final String OTHER_ORG = "9c1b7e3f-0a2d-5c88-5a8d-2b196f4e4d21";

  @Autowired private OperatorRepository repository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  private Address save(String org, AddressStatus status, String name) {
    Address address =
        Address.builder()
            .name(name)
            .addressLine1("14 Drover's Way")
            .townOrCity("Inverness")
            .postcode("IV2 3JH")
            .countryCode("GB")
            .phone("+44 1463 234567")
            .email("exports@highlandlivestock.example.com")
            .organisationId(org)
            .status(status)
            .createdAt(Instant.parse("2026-07-14T09:15:27Z"))
            .modifiedAt(Instant.parse("2026-07-14T09:15:27Z"))
            .build();
    return repository.save(address);
  }

  private void seedActive(int count) {
    for (int i = 0; i < count; i++) {
      save(ORG, AddressStatus.ACTIVE, "Address " + i);
    }
  }

  @Test
  void listDefaultsToPage1Size25AndReportsTotalPages2For30Addresses() throws Exception {
    seedActive(30);

    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG).header(ORG_HEADER, ORG))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(25))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pageSize").value(25))
        .andExpect(jsonPath("$.totalItems").value(30))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  void listPageTwoReturnsTheRemaining5() throws Exception {
    seedActive(30);

    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG).header(ORG_HEADER, ORG).param("page", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(5))
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.totalItems").value(30))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  void listExcludesDeletedTombstones() throws Exception {
    seedActive(3);
    Address deleted = save(ORG, AddressStatus.DELETED, "Ghost Address");

    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG).header(ORG_HEADER, ORG))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(3))
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[*].deleted", everyItem(is(false))))
        .andExpect(jsonPath("$.items[*].id", not(hasItem(deleted.getId()))));
  }

  @Test
  void listIsScopedToTheCallersOrganisation() throws Exception {
    seedActive(2);
    save(OTHER_ORG, AddressStatus.ACTIVE, "Other Org Address");

    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG).header(ORG_HEADER, ORG))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(2))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[*].organisationId", everyItem(is(ORG))));
  }

  @Test
  void listWithPage0Returns400BadRequestProblemWithNoErrorsMap() throws Exception {
    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG).header(ORG_HEADER, ORG).param("page", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void aPageSizeRequestParamIsNotHonouredTheServerConfigSizeApplies() throws Exception {
    seedActive(30);

    // page_size / pageSize are not request parameters (cv-025): the server-config size (25) applies
    // and a supplied value is ignored — never honoured, and never a 400.
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORG)
                .header(ORG_HEADER, ORG)
                .param("page_size", "5")
                .param("pageSize", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(25))
        .andExpect(jsonPath("$.pageSize").value(25))
        .andExpect(jsonPath("$.totalItems").value(30));
  }

  @Test
  void listWithANonNumericPageReturns400BadRequestProblem() throws Exception {
    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG).header(ORG_HEADER, ORG).param("page", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void listWithoutTheOrgHeaderReturns400BadRequestWithNoErrorsMap() throws Exception {
    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

}
