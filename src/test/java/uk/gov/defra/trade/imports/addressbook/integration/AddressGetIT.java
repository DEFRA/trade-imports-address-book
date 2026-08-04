package uk.gov.defra.trade.imports.addressbook.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.OperatorRepository;

/**
 * EUDPA-286 m1-01 — GET /organisation/{orgId}/addresses/{id}. The only endpoint that surfaces a
 * soft-deleted row ({@code deleted: true}); list/search hide tombstones. Cross-org and unknown ids
 * yield 404 not-found problems; byte-identical non-disclosure is asserted in {@link AddressScopingIT}.
 */
class AddressGetIT extends IntegrationBase {

  @Autowired private OperatorRepository repository;

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
            .build();
    return repository.save(address);
  }

  @Test
  void get_shouldReturn200WithAllMappedFields_whenAddressIsLive() throws Exception {
    // Given
    Address saved = save(ORGANISATION_ID, AddressStatus.ACTIVE);

    // When / Then
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId())
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId()))
        .andExpect(jsonPath("$.name").value("Highland Livestock Ltd"))
        .andExpect(jsonPath("$.addressLine1").value("14 Drover's Way"))
        .andExpect(jsonPath("$.townOrCity").value("Inverness"))
        .andExpect(jsonPath("$.postcode").value("IV2 3JH"))
        .andExpect(jsonPath("$.countryCode").value("GB"))
        .andExpect(jsonPath("$.phone").value("+44 1463 234567"))
        .andExpect(jsonPath("$.email").value("exports@highlandlivestock.example.com"))
        .andExpect(jsonPath("$.organisationId").value(ORGANISATION_ID))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.modifiedAt").exists())
        .andExpect(jsonPath("$.deleted").value(false));
  }

  @Test
  void get_shouldReturn200WithDeletedTrue_whenAddressIsTombstoned() throws Exception {
    // Given
    Address saved = save(ORGANISATION_ID, AddressStatus.DELETED);

    // When / Then
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId())
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId()))
        .andExpect(jsonPath("$.deleted").value(true));
  }

  @Test
  void get_shouldReturn404NotFoundProblem_whenIdIsUnknown() throws Exception {
    // When / Then
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, UNKNOWN_ID)
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"))
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }
}
