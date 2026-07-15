package uk.gov.defra.trade.imports.operators.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.defra.trade.imports.operators.operator.OperatorRepository;

/**
 * Cross-crn scoping and identity-header pins (design §2, §9.2 — c-001, c-018). crn A's operators are
 * invisible and unreachable to crn B; {@code organisation_id} is persisted on create but never used
 * to filter reads; a missing {@code Trade-Imports-Crn} (any op) or {@code Trade-Imports-Organisation-Id}
 * (POST) is a 400 bad-request with no {@code errors} map; and — the load-bearing c-018 pin — a 404
 * for another crn's LIVE operator is byte-for-byte identical to a 404 for an unknown id, so a 404
 * leaks neither existence nor a deletion signal.
 */
class OperatorScopingIT extends IntegrationBase {

  private static final String CRN_A = "1100014934";
  private static final String CRN_B = "9900000000";
  private static final String ORG_A = "5a8d2b19-6f4e-4d21-9c1b-7e3f0a2d5c88";

  private static final String CREATE_BODY =
      """
      {
        "operator_type": "CONSIGNOR",
        "name": "Highland Livestock Ltd",
        "address_line_1": "14 Drover's Way",
        "town": "Inverness",
        "postcode": "IV2 3JH",
        "country": "United Kingdom",
        "telephone": "+44 1463 234567",
        "email": "exports@highlandlivestock.example.com"
      }
      """;

  @Autowired private OperatorRepository repository;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  private String createAsCrnA() throws Exception {
    String location =
        mockMvc
            .perform(
                post("/operators")
                    .header("Trade-Imports-Crn", CRN_A)
                    .header("Trade-Imports-Organisation-Id", ORG_A)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_BODY))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
  }

  @Test
  void crnBCannotSeeOrReachCrnAsOperator() throws Exception {
    String id = createAsCrnA();

    // crn B lists nothing
    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN_B))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.total_items").value(0));

    // crn B gets 404 on GET / PUT / DELETE of A's id
    mockMvc
        .perform(get("/operators/{operator-id}", id).header("Trade-Imports-Crn", CRN_B))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            put("/operators/{operator-id}", id)
                .header("Trade-Imports-Crn", CRN_B)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete("/operators/{operator-id}", id).header("Trade-Imports-Crn", CRN_B))
        .andExpect(status().isNotFound());

    // A's operator is untouched under its owning crn
    mockMvc
        .perform(get("/operators/{operator-id}", id).header("Trade-Imports-Crn", CRN_A))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void organisationIdIsPersistedButNotUsedToFilterReads() throws Exception {
    String id = createAsCrnA();

    // both crn and organisation_id are stored (the c-001 both-stored ruling)
    assertThat(repository.findById(id))
        .get()
        .satisfies(
            operator -> {
              assertThat(operator.getCrn()).isEqualTo(CRN_A);
              assertThat(operator.getOrganisationId()).isEqualTo(ORG_A);
            });

    // a read carries NO organisation-id header and is not filtered by it — crn alone scopes reads
    mockMvc
        .perform(get("/operators/{operator-id}", id).header("Trade-Imports-Crn", CRN_A))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.organisation_id").value(ORG_A));
    mockMvc
        .perform(get("/operators").header("Trade-Imports-Crn", CRN_A))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_items").value(1));
  }

  @Test
  void missingCrnHeaderIs400BadRequestWithNoErrorsMap() throws Exception {
    mockMvc
        .perform(get("/operators"))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void missingOrganisationIdHeaderOnPostIs400BadRequestWithNoErrorsMap() throws Exception {
    mockMvc
        .perform(
            post("/operators")
                .header("Trade-Imports-Crn", CRN_A)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.errors").doesNotExist());

    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void a404ForAnotherCrnsLiveOperatorIsIdenticalToA404ForAnUnknownId() throws Exception {
    String liveId = createAsCrnA();

    // crn B fetching crn A's LIVE operator — no trace header, so the body has no trace_id
    MvcResult crossCrn =
        mockMvc
            .perform(get("/operators/{operator-id}", liveId).header("Trade-Imports-Crn", CRN_B))
            .andExpect(status().isNotFound())
            .andReturn();

    // crn B fetching an id that does not exist at all
    MvcResult unknown =
        mockMvc
            .perform(
                get("/operators/{operator-id}", "665f1c2ab3e4d51a2c9d0e77")
                    .header("Trade-Imports-Crn", CRN_B))
            .andExpect(status().isNotFound())
            .andReturn();

    // Identical problem — a 404 carries no existence and no deletion information (c-018). The RFC
    // 9457 `instance` echoes the caller's own request URI (the id it already put in the URL), so it
    // is excluded: it reveals nothing about whether the resource exists elsewhere or was deleted.
    assertThat(problemWithoutInstance(crossCrn)).isEqualTo(problemWithoutInstance(unknown));
  }

  private Map<String, Object> problemWithoutInstance(MvcResult result) throws Exception {
    @SuppressWarnings("unchecked")
    Map<String, Object> body =
        objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    body.remove("instance");
    return body;
  }
}
