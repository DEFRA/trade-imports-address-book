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
 * Cross-organisation scoping and identity-header pins (cv-010, cv-040). Organisation A's addresses
 * are invisible and unreachable to organisation B; {@code organisationId} is stamped on create from
 * the trusted {@code Trade-Imports-Organisation-Id} header and scopes every read and write; a
 * missing header is a 400 bad-request with no {@code errors} map; and — the load-bearing pin — a 404
 * for another organisation's LIVE address is byte-for-byte identical to a 404 for an unknown id, so a
 * 404 leaks neither existence nor a deletion signal.
 */
class OperatorScopingIT extends IntegrationBase {

  private static final String ORG_HEADER = "Trade-Imports-Organisation-Id";
  private static final String ORG_A = "5a8d2b19-6f4e-4d21-9c1b-7e3f0a2d5c88";
  private static final String ORG_B = "9c1b7e3f-0a2d-5c88-5a8d-2b196f4e4d21";

  private static final String CREATE_BODY =
      """
      {
        "name": "Highland Livestock Ltd",
        "addressLine1": "14 Drover's Way",
        "townOrCity": "Inverness",
        "postcode": "IV2 3JH",
        "countryCode": "GB",
        "phone": "+44 1463 234567",
        "email": "exports@highlandlivestock.example.com"
      }
      """;

  @Autowired private OperatorRepository repository;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  private String createAsOrgA() throws Exception {
    String location =
        mockMvc
            .perform(
                post("/operators")
                    .header(ORG_HEADER, ORG_A)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_BODY))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
  }

  @Test
  void orgBCannotSeeOrReachOrgAsAddress() throws Exception {
    String id = createAsOrgA();

    // org B lists nothing
    mockMvc
        .perform(get("/operators").header(ORG_HEADER, ORG_B))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.totalItems").value(0));

    // org B gets 404 on GET / PUT / DELETE of A's id
    mockMvc
        .perform(get("/operators/{operator-id}", id).header(ORG_HEADER, ORG_B))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            put("/operators/{operator-id}", id)
                .header(ORG_HEADER, ORG_B)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete("/operators/{operator-id}", id).header(ORG_HEADER, ORG_B))
        .andExpect(status().isNotFound());

    // A's address is untouched under its owning organisation
    mockMvc
        .perform(get("/operators/{operator-id}", id).header(ORG_HEADER, ORG_A))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(false));
  }

  @Test
  void organisationIdIsStampedOnCreateAndScopesReads() throws Exception {
    String id = createAsOrgA();

    // organisationId is stamped from the header, never the body (cv-010)
    assertThat(repository.findById(id))
        .get()
        .satisfies(address -> assertThat(address.getOrganisationId()).isEqualTo(ORG_A));

    // a read under the owning organisation resolves the address
    mockMvc
        .perform(get("/operators/{operator-id}", id).header(ORG_HEADER, ORG_A))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.organisationId").value(ORG_A));
    mockMvc
        .perform(get("/operators").header(ORG_HEADER, ORG_A))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1));
  }

  @Test
  void missingOrgHeaderIs400BadRequestWithNoErrorsMap() throws Exception {
    mockMvc
        .perform(get("/operators"))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void missingOrgHeaderOnPostIs400BadRequestWithNoErrorsMap() throws Exception {
    mockMvc
        .perform(
            post("/operators")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.errors").doesNotExist());

    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void a404ForAnotherOrgsLiveAddressIsIdenticalToA404ForAnUnknownId() throws Exception {
    String liveId = createAsOrgA();

    // org B fetching org A's LIVE address — no trace header, so the body has no traceId
    MvcResult crossOrg =
        mockMvc
            .perform(get("/operators/{operator-id}", liveId).header(ORG_HEADER, ORG_B))
            .andExpect(status().isNotFound())
            .andReturn();

    // org B fetching an id that does not exist at all
    MvcResult unknown =
        mockMvc
            .perform(
                get("/operators/{operator-id}", "665f1c2ab3e4d51a2c9d0e77")
                    .header(ORG_HEADER, ORG_B))
            .andExpect(status().isNotFound())
            .andReturn();

    // Identical problem — a 404 carries no existence and no deletion information. The RFC 9457
    // `instance` echoes the caller's own request URI (the id it already put in the URL), so it is
    // excluded: it reveals nothing about whether the resource exists elsewhere or was deleted.
    assertThat(problemWithoutInstance(crossOrg)).isEqualTo(problemWithoutInstance(unknown));
  }

  private Map<String, Object> problemWithoutInstance(MvcResult result) throws Exception {
    @SuppressWarnings("unchecked")
    Map<String, Object> body =
        objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    body.remove("instance");
    return body;
  }
}
