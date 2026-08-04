package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * EUDPA-286 m1-02 — PUT /organisation/{orgId}/addresses/{id} full replace. Omitted optionals are
 * cleared (not PATCH semantics). Same validation as create; tombstones and unknown/cross-org ids
 * return 404.
 */
class AddressUpdateIT extends IntegrationBase {

  private static final String VALID_REPLACE_BODY =
      """
      {
        "name": "Lowland Cattle Co",
        "addressLine1": "2 Market Street",
        "townOrCity": "Perth",
        "postcode": "PH1 5AA",
        "countryCode": "IE",
        "phone": "+44 1738 111222",
        "email": "ops@lowlandcattle.example.com"
      }
      """;

  @SpyBean private OperatorRepository repository;

  private Address saveActiveWithOptionals() {
    Address address =
        Address.builder()
            .name("Highland Livestock Ltd")
            .addressLine1("14 Drover's Way")
            .addressLine2("Unit 3")
            .townOrCity("Inverness")
            .county("Highland")
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
  void put_shouldReplaceAllFieldsAndClearOmittedOptionals_whenBodyIsValid() throws Exception {
    // Given
    Address saved = saveActiveWithOptionals();

    // When / Then
    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId())
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REPLACE_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Lowland Cattle Co"))
        .andExpect(jsonPath("$.addressLine1").value("2 Market Street"))
        .andExpect(jsonPath("$.townOrCity").value("Perth"))
        .andExpect(jsonPath("$.countryCode").value("IE"))
        .andExpect(jsonPath("$.postcode").value("PH1 5AA"))
        .andExpect(jsonPath("$.phone").value("+44 1738 111222"))
        .andExpect(jsonPath("$.email").value("ops@lowlandcattle.example.com"))
        .andExpect(jsonPath("$.deleted").value(false));

    assertThat(repository.findByIdAndOrganisationId(saved.getId(), ORGANISATION_ID))
        .get()
        .satisfies(
            address -> {
              assertThat(address.getAddressLine2()).isNull();
              assertThat(address.getCounty()).isNull();
              assertThat(address.getPostcode()).isEqualTo("PH1 5AA");
              assertThat(address.getPhone()).isEqualTo("+44 1738 111222");
              assertThat(address.getEmail()).isEqualTo("ops@lowlandcattle.example.com");
              assertThat(address.getName()).isEqualTo("Lowland Cattle Co");
            });
  }

  @Test
  void put_shouldReturn400PerFieldErrorsAndLeaveRowUntouched_whenBodyIsInvalid() throws Exception {
    // Given
    Address saved = saveActiveWithOptionals();
    String body =
        """
        {
          "name": "Lowland Cattle Co",
          "addressLine1": "",
          "townOrCity": "Perth",
          "postcode": "PH1 5AA",
          "countryCode": "IE",
          "phone": "+44 1738 111222",
          "email": "not-an-email"
        }
        """;

    // When / Then
    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId())
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/validation-error"))
        .andExpect(jsonPath("$.errors.addressLine1").exists())
        .andExpect(jsonPath("$.errors.email").exists());

    assertThat(repository.findByIdAndOrganisationId(saved.getId(), ORGANISATION_ID))
        .get()
        .satisfies(
            address -> {
              assertThat(address.getName()).isEqualTo("Highland Livestock Ltd");
              assertThat(address.getAddressLine1()).isEqualTo("14 Drover's Way");
              assertThat(address.getEmail()).isEqualTo("exports@highlandlivestock.example.com");
            });
  }

  @Test
  void put_shouldReturn404_whenIdIsUnknown() throws Exception {
    // When / Then
    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, UNKNOWN_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REPLACE_BODY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"));
  }

  @Test
  void put_shouldReturn404_whenTargetIsTombstone() throws Exception {
    // Given
    Address tombstone =
        Address.builder()
            .name("Highland Livestock Ltd")
            .addressLine1("14 Drover's Way")
            .townOrCity("Inverness")
            .postcode("IV2 3JH")
            .countryCode("GB")
            .phone("+44 1463 234567")
            .email("exports@highlandlivestock.example.com")
            .organisationId(ORGANISATION_ID)
            .status(AddressStatus.DELETED)
            .build();
    tombstone = repository.save(tombstone);

    // When / Then
    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, tombstone.getId())
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REPLACE_BODY))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void put_shouldBumpModifiedAtAndPreserveCreatedAt_whenReplaceSucceeds() throws Exception {
    // Given
    Address saved = saveActiveWithOptionals();
    Instant createdAt = saved.getCreatedAt();
    Instant baselineModifiedAt = saved.getModifiedAt();

    // When
    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId())
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REPLACE_BODY))
        .andExpect(status().isOk());

    // Then
    assertThat(repository.findByIdAndOrganisationId(saved.getId(), ORGANISATION_ID))
        .get()
        .satisfies(
            address -> {
              assertThat(address.getCreatedAt().toEpochMilli()).isEqualTo(createdAt.toEpochMilli());
              assertThat(address.getModifiedAt()).isAfter(baselineModifiedAt);
            });
  }

  @Test
  void put_shouldReturn409ConflictProblem_whenOptimisticLockingFails() throws Exception {
    // Given
    Address saved = saveActiveWithOptionals();
    doThrow(new OptimisticLockingFailureException("stale"))
        .when(repository)
        .save(any(Address.class));

    // When / Then
    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId())
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REPLACE_BODY))
        .andExpect(status().isConflict())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/conflict"))
        .andExpect(jsonPath("$.title").value("Conflict"));
  }

  @Test
  void put_shouldRejectStaleVersion_whenRowWasUpdatedConcurrently() throws Exception {
    // Given — snapshot before another writer bumps @Version (same pattern as delete IT)
    Address saved = saveActiveWithOptionals();
    Address stale =
        repository.findByIdAndOrganisationId(saved.getId(), ORGANISATION_ID).orElseThrow();

    // When
    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId())
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REPLACE_BODY))
        .andExpect(status().isOk());

    stale.setName("Stale concurrent writer");

    // Then
    assertThatThrownBy(() -> repository.save(stale))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  @Test
  void put_shouldPreventTombstoneResurrection_whenStaleSaveFollowsDelete() throws Exception {
    // Given
    Address saved = saveActiveWithOptionals();
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
