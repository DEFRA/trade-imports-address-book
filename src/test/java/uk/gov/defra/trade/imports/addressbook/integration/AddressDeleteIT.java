package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.OptimisticLockingFailureException;
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

  @SpyBean private OperatorRepository repository;

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
            .organisationId(ORGANISATION_ID)
            .status(AddressStatus.ACTIVE)
            .build();
    return repository.save(address);
  }

  @Test
  void delete_shouldReturn204AndTombstoneRow_whenAddressIsLive() throws Exception {
    // Given
    Address saved = saveActive();
    Instant createdAt = saved.getCreatedAt();
    Instant modifiedAtBeforeDelete = saved.getModifiedAt();

    // When
    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId())
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isNoContent());

    // Then
    assertThat(repository.findByIdAndOrganisationId(saved.getId(), ORGANISATION_ID))
        .get()
        .satisfies(
            address -> {
              assertThat(address.getStatus()).isEqualTo(AddressStatus.DELETED);
              assertThat(address.getCreatedAt().toEpochMilli()).isEqualTo(createdAt.toEpochMilli());
              assertThat(address.getModifiedAt()).isAfter(modifiedAtBeforeDelete);
            });
  }

  @Test
  void delete_shouldExcludeFromListAndFlagDeletedOnGet_whenAddressIsTombstoned() throws Exception {
    // Given
    Address saved = saveActive();
    String id = saved.getId();

    // When
    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, id)
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isNoContent());

    // Then
    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORGANISATION_ID).header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0))
        .andExpect(jsonPath("$.items").isEmpty());

    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, id)
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.deleted").value(true));
  }

  @Test
  void delete_shouldBeIdempotent204WithNoSecondSave_whenAlreadyDeleted() throws Exception {
    // Given
    Address saved = saveActive();
    String id = saved.getId();

    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, id)
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isNoContent());
    clearInvocations(repository);
    Instant modifiedAtAfterFirstDelete =
        repository.findByIdAndOrganisationId(id, ORGANISATION_ID).orElseThrow().getModifiedAt();

    // When
    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, id)
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isNoContent());

    // Then
    verify(repository, never()).save(any());
    assertThat(repository.findByIdAndOrganisationId(id, ORGANISATION_ID))
        .get()
        .satisfies(
            address -> {
              assertThat(address.getStatus()).isEqualTo(AddressStatus.DELETED);
              assertThat(address.getModifiedAt()).isEqualTo(modifiedAtAfterFirstDelete);
            });
  }

  @Test
  void delete_shouldReturn404NotFoundProblem_whenIdIsUnknown() throws Exception {
    // When / Then
    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, UNKNOWN_ID)
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"))
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void delete_shouldReturn404AndLeaveRowActive_whenCrossOrgPathUsed() throws Exception {
    // Given
    Address saved = saveActive();

    // When
    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", OTHER_ORG, saved.getId())
                .header(ORG_HEADER, OTHER_ORG))
        .andExpect(status().isNotFound());

    // Then
    assertThat(repository.findByIdAndOrganisationId(saved.getId(), ORGANISATION_ID))
        .get()
        .extracting(Address::getStatus)
        .isEqualTo(AddressStatus.ACTIVE);
  }

  @Test
  void delete_shouldReturn404AndLeaveRowActive_whenHeaderOrgMismatchesPath() throws Exception {
    // Given
    Address saved = saveActive();

    // When
    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId())
                .header(ORG_HEADER, OTHER_ORG))
        .andExpect(status().isNotFound());

    // Then
    assertThat(repository.findByIdAndOrganisationId(saved.getId(), ORGANISATION_ID))
        .get()
        .extracting(Address::getStatus)
        .isEqualTo(AddressStatus.ACTIVE);
  }

  @Test
  void delete_shouldPreventTombstoneResurrection_whenStaleSaveFollowsDelete() throws Exception {
    // Given
    Address saved = saveActive();
    Address stale =
        repository.findByIdAndOrganisationId(saved.getId(), ORGANISATION_ID).orElseThrow();

    // When
    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId())
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isNoContent());

    stale.setName("Resurrected");
    stale.setStatus(AddressStatus.ACTIVE);

    // Then
    assertThatThrownBy(() -> repository.save(stale))
        .isInstanceOf(OptimisticLockingFailureException.class);
    assertThat(repository.findByIdAndOrganisationId(saved.getId(), ORGANISATION_ID))
        .get()
        .extracting(Address::getStatus)
        .isEqualTo(AddressStatus.DELETED);
  }
}
