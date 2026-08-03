package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.OperatorRepository;

/**
 * EUDPA-286 m1-03 — DELETE /organisation/{orgId}/addresses/{id} soft-delete. Returns 204 without
 * removing the row; idempotent on repeat. Tombstones are excluded from list but remain resolvable
 * by GET-by-id with {@code deleted: true}.
 */
class AddressDeleteIT extends IntegrationBase {

  private static final String ORG_HEADER = "Trade-Imports-Organisation-Id";
  private static final String ORG = "5a8d2b19-6f4e-4d21-9c1b-7e3f0a2d5c88";
  private static final String OTHER_ORG = "9c1b7e3f-0a2d-5c88-5a8d-2b196f4e4d21";
  private static final String UNKNOWN_ID = "665f1c2ab3e4d51a2c9d0e77";

  @Autowired private OperatorRepository repository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  private Address saveActive() {
    Address address =
        Address.builder()
            .name("Highland Livestock Ltd")
            .addressLine1("14 Drover's Way")
            .townOrCity("Inverness")
            .postcode("IV2 3JH")
            .countryCode("GB")
            .phone("+44 1463 234567")
            .email("exports@highlandlivestock.example.com")
            .organisationId(ORG)
            .status(AddressStatus.ACTIVE)
            .createdAt(Instant.parse("2026-07-14T09:15:27Z"))
            .modifiedAt(Instant.parse("2026-07-14T09:15:27Z"))
            .build();
    return repository.save(address);
  }

  @Test
  void deleteLiveAddressReturns204AndKeepsRowWithDeletedStatus() throws Exception {
    Address saved = saveActive();

    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORG, saved.getId())
                .header(ORG_HEADER, ORG))
        .andExpect(status().isNoContent());

    assertThat(repository.findByIdAndOrganisationId(saved.getId(), ORG))
        .get()
        .extracting(Address::getStatus)
        .isEqualTo(AddressStatus.DELETED);
  }

  @Test
  void deletedAddressIsExcludedFromListButReturnedFlaggedByGetById() throws Exception {
    Address saved = saveActive();
    String id = saved.getId();

    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORG, id).header(ORG_HEADER, ORG))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORG).header(ORG_HEADER, ORG))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0))
        .andExpect(jsonPath("$.items").isEmpty());

    mockMvc
        .perform(get("/organisation/{orgId}/addresses/{operator-id}", ORG, id).header(ORG_HEADER, ORG))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.deleted").value(true));
  }

  @Test
  void deleteAlreadyDeletedAddressIsIdempotent204WithNoStateChange() throws Exception {
    Address saved = saveActive();
    String id = saved.getId();

    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORG, id).header(ORG_HEADER, ORG))
        .andExpect(status().isNoContent());
    Instant modifiedAtAfterFirstDelete =
        repository.findByIdAndOrganisationId(id, ORG).orElseThrow().getModifiedAt();

    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORG, id).header(ORG_HEADER, ORG))
        .andExpect(status().isNoContent());

    assertThat(repository.findByIdAndOrganisationId(id, ORG))
        .get()
        .satisfies(
            address -> {
              assertThat(address.getStatus()).isEqualTo(AddressStatus.DELETED);
              assertThat(address.getModifiedAt()).isEqualTo(modifiedAtAfterFirstDelete);
            });
  }

  @Test
  void deleteUnknownIdReturns404NotFoundProblem() throws Exception {
    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORG, UNKNOWN_ID)
                .header(ORG_HEADER, ORG))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"))
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void deleteCrossOrgPathWithMatchingHeaderReturns404AndLeavesAddressUntouched() throws Exception {
    Address saved = saveActive();

    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", OTHER_ORG, saved.getId())
                .header(ORG_HEADER, OTHER_ORG))
        .andExpect(status().isNotFound());

    assertThat(repository.findByIdAndOrganisationId(saved.getId(), ORG))
        .get()
        .extracting(Address::getStatus)
        .isEqualTo(AddressStatus.ACTIVE);
  }

  @Test
  void deleteWithHeaderOrgMismatchingPathReturns404() throws Exception {
    Address saved = saveActive();

    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORG, saved.getId())
                .header(ORG_HEADER, OTHER_ORG))
        .andExpect(status().isNotFound());

    assertThat(repository.findByIdAndOrganisationId(saved.getId(), ORG))
        .get()
        .extracting(Address::getStatus)
        .isEqualTo(AddressStatus.ACTIVE);
  }
}
