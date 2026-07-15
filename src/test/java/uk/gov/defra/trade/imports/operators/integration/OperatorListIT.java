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

  private Operator saveSearchable(
      OperatorType type, String name, String addressLine1, String postcode, String country) {
    Operator operator =
        Operator.builder()
            .operatorType(type)
            .name(name)
            .addressLine1(addressLine1)
            .town("Inverness")
            .postcode(postcode)
            .country(country)
            .telephone("+44 1463 234567")
            .email("ops@example.com")
            .crn(CRN)
            .organisationId(ORGANISATION_ID)
            .status(OperatorStatus.ACTIVE)
            .createdAt(Instant.parse("2026-07-14T09:15:27Z"))
            .modifiedAt(Instant.parse("2026-07-14T09:15:27Z"))
            .build();
    return repository.save(operator);
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

  @Test
  void searchMatchesOnName() throws Exception {
    saveSearchable(OperatorType.CONSIGNOR, "Highland Livestock Ltd", "14 Drover's Way", "IV2 3JH", "United Kingdom");
    saveSearchable(OperatorType.IMPORTER, "Lowland Cattle Co", "2 Market Street", "PH1 5AA", "Ireland");

    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN).param("q", "Highland"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_items").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Highland Livestock Ltd"));
  }

  @Test
  void searchMatchesOnAnAddressLine() throws Exception {
    saveSearchable(OperatorType.CONSIGNOR, "Highland Livestock Ltd", "14 Drover's Way", "IV2 3JH", "United Kingdom");
    saveSearchable(OperatorType.IMPORTER, "Lowland Cattle Co", "2 Market Street", "PH1 5AA", "Ireland");

    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN).param("q", "Market"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_items").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Lowland Cattle Co"));
  }

  @Test
  void searchMatchesOnPostcodeCaseInsensitively() throws Exception {
    saveSearchable(OperatorType.CONSIGNOR, "Highland Livestock Ltd", "14 Drover's Way", "IV2 3JH", "United Kingdom");
    saveSearchable(OperatorType.IMPORTER, "Lowland Cattle Co", "2 Market Street", "PH1 5AA", "Ireland");

    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN).param("q", "iv2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_items").value(1))
        .andExpect(jsonPath("$.items[0].postcode").value("IV2 3JH"));
  }

  @Test
  void searchMatchesOnACountryDisplayNameSubstring() throws Exception {
    // c-004: country is stored as the display-name STRING ("United Kingdom"), never an ISO code — so
    // a substring of the display name matches. There is no code<->name conversion anywhere.
    saveSearchable(OperatorType.CONSIGNOR, "Highland Livestock Ltd", "14 Drover's Way", "IV2 3JH", "United Kingdom");
    saveSearchable(OperatorType.IMPORTER, "Lowland Cattle Co", "2 Market Street", "PH1 5AA", "Ireland");

    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN).param("q", "United King"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_items").value(1))
        .andExpect(jsonPath("$.items[0].country").value("United Kingdom"));
  }

  @Test
  void searchWithRegexMetacharactersIsInertAndMatchesNothingRatherThanEverything() throws Exception {
    // Pattern.quote() makes ".*" a literal — it matches only a field literally containing ".*", of
    // which there are none — so the result is empty, NOT every operator (which an unquoted regex
    // would return) and NOT a 500.
    saveSearchable(OperatorType.CONSIGNOR, "Highland Livestock Ltd", "14 Drover's Way", "IV2 3JH", "United Kingdom");
    saveSearchable(OperatorType.IMPORTER, "Lowland Cattle Co", "2 Market Street", "PH1 5AA", "Ireland");

    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN).param("q", ".*"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_items").value(0))
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void searchWithAnUnbalancedRegexParenIsInertAndDoesNotError() throws Exception {
    // "(" is an invalid regex on its own; unquoted it would 500. Pattern.quote() makes it a literal
    // so it simply matches nothing.
    saveSearchable(OperatorType.CONSIGNOR, "Highland Livestock Ltd", "14 Drover's Way", "IV2 3JH", "United Kingdom");

    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN).param("q", "("))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_items").value(0));
  }

  @Test
  void operatorTypeFilterReturnsOnlyThatType() throws Exception {
    saveSearchable(OperatorType.CONSIGNOR, "Highland Livestock Ltd", "14 Drover's Way", "IV2 3JH", "United Kingdom");
    saveSearchable(OperatorType.IMPORTER, "Lowland Cattle Co", "2 Market Street", "PH1 5AA", "Ireland");
    saveSearchable(OperatorType.TRANSPORTER, "Border Hauliers", "9 Bridge Road", "CA1 1AA", "France");

    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN).param("operator_type", "IMPORTER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_items").value(1))
        .andExpect(jsonPath("$.items[0].operator_type").value("IMPORTER"));
  }

  @Test
  void searchAndOperatorTypeCombineAsAnAnd() throws Exception {
    saveSearchable(OperatorType.CONSIGNOR, "Riverside Farms", "1 River Lane", "IV2 3JH", "United Kingdom");
    saveSearchable(OperatorType.IMPORTER, "Riverside Farms", "1 River Lane", "PH1 5AA", "Ireland");

    mockMvc
        .perform(
            get("/operators")
                .header("Trade-Imports-Crn", CRN)
                .param("q", "Riverside")
                .param("operator_type", "CONSIGNOR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_items").value(1))
        .andExpect(jsonPath("$.items[0].operator_type").value("CONSIGNOR"));
  }

  @Test
  void allFourSentinelCombinationsOfQAndOperatorTypeFilterAsExpected() throws Exception {
    // §1.3 four combinations against one seed of 3: neither -> all, q only, type only, both.
    saveSearchable(OperatorType.CONSIGNOR, "Alpha Farms", "1 Alpha Way", "AL1 1AA", "United Kingdom");
    saveSearchable(OperatorType.IMPORTER, "Beta Farms", "2 Beta Way", "BE1 1BB", "United Kingdom");
    saveSearchable(OperatorType.CONSIGNOR, "Gamma Traders", "3 Gamma Way", "GA1 1GG", "United Kingdom");

    // neither: unfiltered list returns all active
    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_items").value(3));

    // q only: "Farms" matches Alpha + Beta
    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN).param("q", "Farms"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_items").value(2));

    // type only: CONSIGNOR matches Alpha + Gamma
    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN).param("operator_type", "CONSIGNOR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_items").value(2));

    // both: "Farms" AND CONSIGNOR matches only Alpha
    mockMvc
        .perform(
            get("/operators")
                .header("Trade-Imports-Crn", CRN)
                .param("q", "Farms")
                .param("operator_type", "CONSIGNOR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_items").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Alpha Farms"));
  }

  @Test
  void searchIsStillScopedToTheCallersCrn() throws Exception {
    saveSearchable(OperatorType.CONSIGNOR, "Highland Livestock Ltd", "14 Drover's Way", "IV2 3JH", "United Kingdom");
    save(OTHER_CRN, OperatorStatus.ACTIVE, "Highland Rivals Ltd");

    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN).param("q", "Highland"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_items").value(1))
        .andExpect(jsonPath("$.items[*].crn", everyItem(is(CRN))));
  }

  @Test
  void searchExcludesDeletedTombstones() throws Exception {
    saveSearchable(OperatorType.CONSIGNOR, "Highland Livestock Ltd", "14 Drover's Way", "IV2 3JH", "United Kingdom");
    save(CRN, OperatorStatus.DELETED, "Highland Ghost Ltd");

    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN).param("q", "Highland"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_items").value(1))
        .andExpect(jsonPath("$.items[*].status", everyItem(is("ACTIVE"))));
  }
}
