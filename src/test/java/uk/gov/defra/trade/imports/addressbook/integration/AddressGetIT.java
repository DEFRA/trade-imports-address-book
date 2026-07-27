package uk.gov.defra.trade.imports.addressbook.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.OperatorRepository;

/**
 * EUDPA-286 m1-01 — GET /organisation/{orgId}/addresses/{id}. The only endpoint that surfaces a
 * soft-deleted row ({@code deleted: true}); list/search hide tombstones. Cross-org and unknown ids
 * yield byte-identical 404s (cv-040).
 */
class AddressGetIT extends IntegrationBase {

  private static final String ORG_HEADER = "Trade-Imports-Organisation-Id";
  private static final String ORG_A = "5a8d2b19-6f4e-4d21-9c1b-7e3f0a2d5c88";
  private static final String ORG_B = "9c1b7e3f-0a2d-5c88-5a8d-2b196f4e4d21";
  private static final String UNKNOWN_ID = "665f1c2ab3e4d51a2c9d0e77";

  @Autowired private OperatorRepository repository;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  private Address save(String orgId, AddressStatus status) {
    Address address =
        Address.builder()
            .name("Highland Livestock Ltd")
            .addressLine1("14 Drover's Way")
            .townOrCity("Inverness")
            .postcode("IV2 3JH")
            .countryCode("GB")
            .phone("+44 1463 234567")
            .email("exports@highlandlivestock.example.com")
            .organisationId(orgId)
            .status(status)
            .createdAt(Instant.parse("2026-07-14T09:15:27Z"))
            .modifiedAt(Instant.parse("2026-07-14T09:15:27Z"))
            .build();
    return repository.save(address);
  }

  @Test
  void getLiveAddressReturns200WithDeletedFalse() throws Exception {
    Address saved = save(ORG_A, AddressStatus.ACTIVE);

    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses/{operator-id}", ORG_A, saved.getId())
                .header(ORG_HEADER, ORG_A))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId()))
        .andExpect(jsonPath("$.name").value("Highland Livestock Ltd"))
        .andExpect(jsonPath("$.countryCode").value("GB"))
        .andExpect(jsonPath("$.organisationId").value(ORG_A))
        .andExpect(jsonPath("$.deleted").value(false));
  }

  @Test
  void getSoftDeletedAddressReturns200WithDeletedTrue() throws Exception {
    Address saved = save(ORG_A, AddressStatus.DELETED);

    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses/{operator-id}", ORG_A, saved.getId())
                .header(ORG_HEADER, ORG_A))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId()))
        .andExpect(jsonPath("$.deleted").value(true));
  }

  @Test
  void getUnknownIdReturns404NotFoundProblem() throws Exception {
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses/{operator-id}", ORG_A, UNKNOWN_ID)
                .header(ORG_HEADER, ORG_A))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"))
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void getCrossOrgIdReturns404IdenticalToUnknownId() throws Exception {
    Address live = save(ORG_A, AddressStatus.ACTIVE);

    MvcResult crossOrg =
        mockMvc
            .perform(
                get("/organisation/{orgId}/addresses/{operator-id}", ORG_B, live.getId())
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

    assertThatProblemBodiesMatch(crossOrg, unknown);
  }

  private void assertThatProblemBodiesMatch(MvcResult first, MvcResult second) throws Exception {
    @SuppressWarnings("unchecked")
    Map<String, Object> firstBody =
        objectMapper.readValue(first.getResponse().getContentAsString(), Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> secondBody =
        objectMapper.readValue(second.getResponse().getContentAsString(), Map.class);
    firstBody.remove("instance");
    secondBody.remove("instance");
    org.assertj.core.api.Assertions.assertThat(firstBody).isEqualTo(secondBody);
  }
}
