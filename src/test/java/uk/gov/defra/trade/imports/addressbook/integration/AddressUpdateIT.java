package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.AddressType;
import uk.gov.defra.trade.imports.addressbook.address.AddressUse;

class AddressUpdateIT extends IntegrationBase {

  private Address saveActiveWithOptionals() {
    return saveAddress(
        addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE)
            .addressLine2("Unit 3")
            .county("Highland")
            .addressType(AddressType.ORIGIN_OR_CONSIGNOR)
            .uses(List.of(AddressUse.PLACE_OF_ORIGIN, AddressUse.CONSIGNOR)));
  }

  private ResultActions putAddress(String addressId, AddressBody body) throws Exception {
    return mockMvc.perform(
        put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, addressId)
            .header(ORG_HEADER, ORGANISATION_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.json()));
  }

  private Address storedAddress(String addressId) {
    return operatorRepository.findByIdAndOrganisationId(addressId, ORGANISATION_ID).orElseThrow();
  }

  private void assertStoredTypeAndUsesAreStillTheSeededOnes(Address seeded) {
    assertThat(storedAddress(seeded.getId()))
        .satisfies(
            address -> {
              assertThat(address.getAddressType()).isEqualTo(AddressType.ORIGIN_OR_CONSIGNOR);
              assertThat(address.getUses())
                  .containsExactly(AddressUse.PLACE_OF_ORIGIN, AddressUse.CONSIGNOR);
            });
  }

  @Test
  void put_shouldReplaceAllFieldsAndClearOmittedOptionals_whenBodyIsValid() throws Exception {
    Address saved = saveActiveWithOptionals();
    AddressBody body =
        addressBody()
            .with("name", "Lowland Cattle Co")
            .with("addressLine1", "2 Market Street")
            .with("townOrCity", "Perth")
            .with("postcode", "PH1 5AA")
            .with("countryCode", "IE")
            .with("phone", "+44 1738 111222")
            .with("email", "ops@lowlandcattle.example.com")
            .with("addressType", "DESTINATION_CONSIGNEE_OR_IMPORTER")
            .with("uses", List.of("IMPORTER", "CONSIGNEE", "BRANCH_ADDRESS"));

    putAddress(saved.getId(), body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Lowland Cattle Co"))
        .andExpect(jsonPath("$.addressLine1").value("2 Market Street"))
        .andExpect(jsonPath("$.townOrCity").value("Perth"))
        .andExpect(jsonPath("$.countryCode").value("IE"))
        .andExpect(jsonPath("$.postcode").value("PH1 5AA"))
        .andExpect(jsonPath("$.phone").value("+44 1738 111222"))
        .andExpect(jsonPath("$.email").value("ops@lowlandcattle.example.com"))
        .andExpect(jsonPath("$.addressType").value("DESTINATION_CONSIGNEE_OR_IMPORTER"))
        .andExpect(jsonPath("$.uses", contains("IMPORTER", "CONSIGNEE", "BRANCH_ADDRESS")))
        .andExpect(jsonPath("$.deleted").value(false));

    assertThat(storedAddress(saved.getId()))
        .satisfies(
            address -> {
              assertThat(address.getAddressLine2()).isNull();
              assertThat(address.getCounty()).isNull();
              assertThat(address.getName()).isEqualTo("Lowland Cattle Co");
              assertThat(address.getAddressLine1()).isEqualTo("2 Market Street");
              assertThat(address.getTownOrCity()).isEqualTo("Perth");
              assertThat(address.getPostcode()).isEqualTo("PH1 5AA");
              assertThat(address.getCountryCode()).isEqualTo("IE");
              assertThat(address.getPhone()).isEqualTo("+44 1738 111222");
              assertThat(address.getEmail()).isEqualTo("ops@lowlandcattle.example.com");
              assertThat(address.getAddressType())
                  .isEqualTo(AddressType.DESTINATION_CONSIGNEE_OR_IMPORTER);
              assertThat(address.getUses())
                  .containsExactly(
                      AddressUse.IMPORTER, AddressUse.CONSIGNEE, AddressUse.BRANCH_ADDRESS);
            });
  }

  @Test
  void put_shouldClearAddressTypeAndUses_whenBodyOmitsThem() throws Exception {
    Address saved = saveActiveWithOptionals();

    putAddress(saved.getId(), addressBody())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.addressType").value(nullValue()))
        .andExpect(jsonPath("$.uses").value(nullValue()));

    assertThat(storedAddress(saved.getId()))
        .satisfies(
            address -> {
              assertThat(address.getAddressType()).isNull();
              assertThat(address.getUses()).isNull();
            });
  }

  @Test
  void put_shouldReturn400KeyedByUsesAndKeepStoredValues_whenAUseIsUnknown() throws Exception {
    Address saved = saveActiveWithOptionals();
    AddressBody body = addressBody().with("uses", List.of("IMPORTER", "TRANSPORTER"));

    putAddress(saved.getId(), body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(VALIDATION_ERROR_TYPE))
        .andExpect(jsonPath("$.errors.uses").exists());

    assertStoredTypeAndUsesAreStillTheSeededOnes(saved);
  }

  @Test
  void put_shouldReturn400KeyedByAddressTypeAndKeepStoredValues_whenTheTypeIsUnknown()
      throws Exception {
    Address saved = saveActiveWithOptionals();
    AddressBody body = addressBody().with("addressType", "TRANSPORTER");

    putAddress(saved.getId(), body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(VALIDATION_ERROR_TYPE))
        .andExpect(jsonPath("$.errors.addressType").exists());

    assertStoredTypeAndUsesAreStillTheSeededOnes(saved);
  }

  @Test
  void put_shouldReturn400PerFieldErrorsAndLeaveRowUntouched_whenBodyIsInvalid() throws Exception {
    Address saved = saveActiveWithOptionals();
    AddressBody body = addressBody().blank("addressLine1").with("email", "not-an-email");

    putAddress(saved.getId(), body)
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value(VALIDATION_ERROR_TYPE))
        .andExpect(jsonPath("$.errors.addressLine1").exists())
        .andExpect(jsonPath("$.errors.email").exists());

    assertThat(storedAddress(saved.getId()))
        .satisfies(
            address -> {
              assertThat(address.getName()).isEqualTo(NAME);
              assertThat(address.getAddressLine1()).isEqualTo(ADDRESS_LINE_1);
              assertThat(address.getEmail()).isEqualTo(EMAIL);
            });
  }

  @Test
  void put_shouldReturn404_whenIdIsUnknown() throws Exception {
    putAddress(UNKNOWN_ID, addressBody())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"));
  }

  @Test
  void put_shouldReturn404_whenTargetIsTombstone() throws Exception {
    Address tombstone = saveAddress(addressBuilder(ORGANISATION_ID, AddressStatus.DELETED));

    putAddress(tombstone.getId(), addressBody())
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void put_shouldBumpModifiedAtAndPreserveCreatedAt_whenReplaceSucceeds() throws Exception {
    Address saved = saveActiveWithOptionals();
    Instant createdAt = saved.getCreatedAt();
    Instant baselineModifiedAt = saved.getModifiedAt();

    putAddress(saved.getId(), addressBody()).andExpect(status().isOk());

    assertThat(storedAddress(saved.getId()))
        .satisfies(
            address -> {
              assertThat(address.getCreatedAt().toEpochMilli()).isEqualTo(createdAt.toEpochMilli());
              assertThat(address.getModifiedAt()).isAfter(baselineModifiedAt);
            });
  }

  @Test
  void put_shouldRejectStaleVersion_whenRowWasUpdatedConcurrently() throws Exception {
    Address saved = saveActiveWithOptionals();
    Address stale = storedAddress(saved.getId());

    putAddress(saved.getId(), addressBody()).andExpect(status().isOk());

    stale.setName("Stale concurrent writer");

    assertThatThrownBy(() -> operatorRepository.save(stale))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  @Test
  void put_shouldPreventTombstoneResurrection_whenStaleSaveFollowsDelete() throws Exception {
    Address saved = saveActiveWithOptionals();
    Address stale = storedAddress(saved.getId());

    mockMvc
        .perform(
            delete("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId())
                .header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isNoContent());

    stale.setName("Resurrected");
    stale.setStatus(AddressStatus.ACTIVE);

    assertThatThrownBy(() -> operatorRepository.save(stale))
        .isInstanceOf(OptimisticLockingFailureException.class);
    assertThat(storedAddress(saved.getId()))
        .extracting(Address::getStatus)
        .isEqualTo(AddressStatus.DELETED);
  }
}
