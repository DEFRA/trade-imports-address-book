package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.OperatorRepository;

class AddressScopingIT extends IntegrationBase {

  private static final String ORG_A = ORGANISATION_ID;
  private static final String ORG_B = OTHER_ORG;

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

    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG_B).header(ORG_HEADER, ORG_B))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.totalItems").value(0));
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
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses/{operator-id}", ORG_A, id).header(ORG_HEADER, ORG_A))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(false));
  }

  @Test
  void forwardedOrgHeaderDisagreeingWithThePathOrgIdIs404() throws Exception {
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
    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORG_A, id).header(ORG_HEADER, ORG_B))
        .andExpect(status().isNotFound());
    assertThat(repository.findByIdAndOrganisationId(id, ORG_A))
        .get()
        .extracting("status")
        .isEqualTo(AddressStatus.ACTIVE);
    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORG_A)
                .header(ORG_HEADER, ORG_B)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isNotFound());
    assertThat(activeAddressesFor(ORG_A)).singleElement();
  }

  @Test
  void missingOrgHeaderIsAPlatformAuthRejection400WithNoErrorsMap() throws Exception {
    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG_A))
        .andExpect(badRequestProblemWithNoErrors());
  }

  @Test
  void blankOrgHeaderIsAPlatformAuthRejection400WithNoErrorsMap() throws Exception {
    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG_A).header(ORG_HEADER, "   "))
        .andExpect(badRequestProblemWithNoErrors());
  }

  @Test
  void missingOrgHeaderOnPostIsAPlatformAuthRejection400AndPersistsNothing() throws Exception {
    mockMvc
        .perform(
            post("/organisation/{orgId}/addresses", ORG_A)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(badRequestProblemWithNoErrors());

    assertThat(activeAddressesFor(ORG_A)).isEmpty();
  }

  @Test
  void organisationIdIsStampedOnCreateAndScopesReads() throws Exception {
    String id = createAsOrgA();

    assertThat(repository.findByIdAndOrganisationId(id, ORG_A))
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

    MvcResult crossOrg =
        mockMvc
            .perform(
                get("/organisation/{orgId}/addresses/{operator-id}", ORG_B, liveId)
                    .header(ORG_HEADER, ORG_B))
            .andExpect(status().isNotFound())
            .andReturn();
    MvcResult unknown =
        mockMvc
            .perform(
                get("/organisation/{orgId}/addresses/{operator-id}", ORG_B, UNKNOWN_ID)
                    .header(ORG_HEADER, ORG_B))
            .andExpect(status().isNotFound())
            .andReturn();
    assertThat(problemBody(crossOrg)).isEqualTo(problemBody(unknown));
    assertThat(problemBody(crossOrg)).doesNotContainKey("instance");
    assertThat(problemBody(unknown)).doesNotContainKey("instance");
  }

  private Map<String, Object> problemBody(MvcResult result) throws Exception {
    @SuppressWarnings("unchecked")
    Map<String, Object> body =
        objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    return body;
  }
}
