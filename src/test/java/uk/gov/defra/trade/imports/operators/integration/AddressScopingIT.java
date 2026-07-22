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
 * Org-path scoping and the cv-040 path-vs-session authorisation (cv-010, cv-040). The address book
 * is path-scoped to {@code /organisation/{orgId}/addresses} and the API authorises the caller's
 * forwarded {@code Trade-Imports-Organisation-Id} against the path {@code orgId} — the path is never
 * trusted alone. This pins three defences:
 *
 * <ul>
 *   <li>organisation B, browsing its OWN org path, cannot see or reach organisation A's address —
 *       the scope is enforced in the query, so a cross-org id is a 404 byte-for-byte identical to an
 *       unknown id (no existence and no deletion leak);
 *   <li>a forwarded session-org that disagrees with the path {@code orgId} is a 404 (never 403 — no
 *       existence disclosure, cv-040), before any store lookup;
 *   <li>a missing or blank identity header is a platform auth rejection — a 400 bad-request problem
 *       with NO {@code errors} map — not a body-field business validation error.
 * </ul>
 */
class AddressScopingIT extends IntegrationBase {

  private static final String ORG_HEADER = "Trade-Imports-Organisation-Id";
  private static final String ORG_A = "5a8d2b19-6f4e-4d21-9c1b-7e3f0a2d5c88";
  private static final String ORG_B = "9c1b7e3f-0a2d-5c88-5a8d-2b196f4e4d21";
  private static final String UNKNOWN_ID = "665f1c2ab3e4d51a2c9d0e77";

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
                post("/organisation/{orgId}/addresses", ORG_A)
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
  void orgBBrowsingItsOwnPathCannotSeeOrReachOrgAsAddress() throws Exception {
    String id = createAsOrgA();

    // org B lists its own (empty) address book
    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG_B).header(ORG_HEADER, ORG_B))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.totalItems").value(0));

    // org B gets 404 on GET / PUT / DELETE of A's id under B's own scope
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses/{operator-id}", ORG_B, id).header(ORG_HEADER, ORG_B))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORG_B, id)
                .header(ORG_HEADER, ORG_B)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORG_B, id)
                .header(ORG_HEADER, ORG_B))
        .andExpect(status().isNotFound());

    // A's address is untouched under its owning organisation
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses/{operator-id}", ORG_A, id).header(ORG_HEADER, ORG_A))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(false));
  }

  @Test
  void forwardedOrgHeaderDisagreeingWithThePathOrgIdIs404() throws Exception {
    // cv-040: the API authorises the caller's session-org against the path orgId. A caller whose
    // session is ORG_B addressing ORG_A's path is rejected with a 404 (never 403), before any store
    // lookup, so it cannot even confirm ORG_A's book exists.
    String id = createAsOrgA();

    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses/{operator-id}", ORG_A, id).header(ORG_HEADER, ORG_B))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG_A).header(ORG_HEADER, ORG_B))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORG_A, id)
                .header(ORG_HEADER, ORG_B)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isNotFound());

    // a create against another org's path is rejected and persists nothing new
    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORG_A)
                .header(ORG_HEADER, ORG_B)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isNotFound());
    assertThat(repository.findAll()).singleElement();
  }

  @Test
  void missingOrgHeaderIsAPlatformAuthRejection400WithNoErrorsMap() throws Exception {
    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG_A))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void blankOrgHeaderIsAPlatformAuthRejection400WithNoErrorsMap() throws Exception {
    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG_A).header(ORG_HEADER, "   "))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void missingOrgHeaderOnPostIsAPlatformAuthRejection400AndPersistsNothing() throws Exception {
    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORG_A)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.errors").doesNotExist());

    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void organisationIdIsStampedOnCreateAndScopesReads() throws Exception {
    String id = createAsOrgA();

    // organisationId is stamped from the trusted identity, never the body (cv-010)
    assertThat(repository.findById(id))
        .get()
        .satisfies(address -> assertThat(address.getOrganisationId()).isEqualTo(ORG_A));

    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses/{operator-id}", ORG_A, id).header(ORG_HEADER, ORG_A))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.organisationId").value(ORG_A));
    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG_A).header(ORG_HEADER, ORG_A))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1));
  }

  @Test
  void a404ForAnotherOrgsLiveAddressIsByteIdenticalToA404ForAnUnknownId() throws Exception {
    String liveId = createAsOrgA();

    // org B, in its own scope, fetching org A's LIVE address — a scoped miss (query isolation).
    // No trace header, so the body carries no traceId.
    MvcResult crossOrg =
        mockMvc
            .perform(
                get("/organisation/{orgId}/addresses/{operator-id}", ORG_B, liveId)
                    .header(ORG_HEADER, ORG_B))
            .andExpect(status().isNotFound())
            .andReturn();

    // org B fetching an id that does not exist at all
    MvcResult unknown =
        mockMvc
            .perform(
                get("/organisation/{orgId}/addresses/{operator-id}", ORG_B, UNKNOWN_ID)
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
