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
import uk.gov.defra.trade.imports.operators.operator.Operator;
import uk.gov.defra.trade.imports.operators.operator.OperatorRepository;
import uk.gov.defra.trade.imports.operators.operator.OperatorStatus;
import uk.gov.defra.trade.imports.operators.operator.OperatorType;

/**
 * Full-stack list integration test for {@code GET /operators} (inc-009). Pins the paginated,
 * newest-first, crn-scoped, ACTIVE-only listing: 30 seeded operators paginate 25 + 5 across two
 * pages, DELETED tombstones are excluded, another crn's operators are invisible, and out-of-range
 * pagination parameters produce a 400 bad-request problem with no {@code errors} map.
 */
class OperatorListIT extends IntegrationBase {

  private static final String CRN = "1100014934";
  private static final String OTHER_CRN = "9900000000";
  private static final String ORGANISATION_ID = "5a8d2b19-6f4e-4d21-9c1b-7e3f0a2d5c88";

  @Autowired private OperatorRepository repository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  private Operator save(String crn, OperatorStatus status, String name) {
    Operator operator =
        Operator.builder()
            .operatorType(OperatorType.CONSIGNOR)
            .name(name)
            .addressLine1("14 Drover's Way")
            .town("Inverness")
            .postcode("IV2 3JH")
            .country("United Kingdom")
            .telephone("+44 1463 234567")
            .email("exports@highlandlivestock.example.com")
            .crn(crn)
            .organisationId(ORGANISATION_ID)
            .status(status)
            .createdAt(Instant.parse("2026-07-14T09:15:27Z"))
            .modifiedAt(Instant.parse("2026-07-14T09:15:27Z"))
            .build();
    return repository.save(operator);
  }

  private void seedActive(int count) {
    for (int i = 0; i < count; i++) {
      save(CRN, OperatorStatus.ACTIVE, "Operator " + i);
    }
  }

  @Test
  void listDefaultsToPage1Size25AndReportsTotalPages2For30Operators() throws Exception {
    seedActive(30);

    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(25))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.page_size").value(25))
        .andExpect(jsonPath("$.total_items").value(30))
        .andExpect(jsonPath("$.total_pages").value(2));
  }

  @Test
  void listPageTwoReturnsTheRemaining5() throws Exception {
    seedActive(30);

    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN).param("page", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(5))
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.total_items").value(30))
        .andExpect(jsonPath("$.total_pages").value(2));
  }

  @Test
  void listExcludesDeletedTombstones() throws Exception {
    seedActive(3);
    Operator deleted = save(CRN, OperatorStatus.DELETED, "Ghost Operator");

    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_items").value(3))
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[*].status", everyItem(is("ACTIVE"))))
        .andExpect(jsonPath("$.items[*].id", not(hasItem(deleted.getId()))));
  }

  @Test
  void listIsScopedToTheCallersCrn() throws Exception {
    seedActive(2);
    save(OTHER_CRN, OperatorStatus.ACTIVE, "Other Org Operator");

    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_items").value(2))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[*].crn", everyItem(is(CRN))));
  }

  @Test
  void listWithPage0Returns400BadRequestProblemWithNoErrorsMap() throws Exception {
    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN).param("page", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void listWithPageSize101Returns400BadRequestProblemWithNoErrorsMap() throws Exception {
    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN).param("page_size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void listWithANonNumericPageReturns400BadRequestProblem() throws Exception {
    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN).param("page", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void listWithoutTheCrnHeaderReturns400BadRequestWithNoErrorsMap() throws Exception {
    mockMvc
        .perform(get("/operators"))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }
}
