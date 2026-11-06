package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressRepository;
import uk.gov.defra.trade.imports.addressbook.address.AddressRole;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;

@MockitoSpyBean(types = AddressRepository.class)
class AddressUpdateIT extends IntegrationBase {

  private Address saveActiveAddressWithRolesAndOptionals() {
    return saveAddress(
        addressHolding(ORGANISATION_ID, AddressRole.PLACE_OF_ORIGIN, AddressRole.CONSIGNOR)
            .addressLine2("Unit 3")
            .county("Highland"));
  }

  private Address storedAddress(String addressId) {
    return addressRepository.findByIdAndOrganisationId(addressId, ORGANISATION_ID).orElseThrow();
  }

  private void assertStoredValuesAreStillTheSeededOnes(Address seeded) {
    Address stored = storedAddress(seeded.getId());
    assertThat(stored.getRoles()).containsExactlyElementsOf(seeded.getRoles());
    assertThat(stored.getAddressLine2()).isEqualTo(seeded.getAddressLine2());
    assertThat(stored.getCounty()).isEqualTo(seeded.getCounty());
    assertThat(stored.getCountryCode()).isEqualTo(seeded.getCountryCode());
  }

  @Test
  void put_shouldReplaceAllFieldsAndClearOmittedOptionals_whenBodyIsValid() throws Exception {
    Address saved = saveActiveAddressWithRolesAndOptionals();
    AddressBody body =
        addressBody()
            .with("name", "Lowland Cattle Co")
            .with("addressLine1", "2 Market Street")
            .with("townOrCity", "Perth")
            .with("postcode", "PH1 5AA")
            .with("countryCode", "GB")
            .with("phone", "+44 1738 111222")
            .with("email", "ops@lowlandcattle.example.com")
            .with("roles", List.of("IMPORTER", "CONSIGNEE"));

    putAddress(saved.getId(), body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Lowland Cattle Co"))
        .andExpect(jsonPath("$.addressLine1").value("2 Market Street"))
        .andExpect(jsonPath("$.townOrCity").value("Perth"))
        .andExpect(jsonPath("$.countryCode").value("GB"))
        .andExpect(jsonPath("$.postcode").value("PH1 5AA"))
        .andExpect(jsonPath("$.phone").value("+44 1738 111222"))
        .andExpect(jsonPath("$.email").value("ops@lowlandcattle.example.com"))
        .andExpect(jsonPath("$.roles", contains("IMPORTER", "CONSIGNEE")))
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
              assertThat(address.getCountryCode()).isEqualTo("GB");
              assertThat(address.getPhone()).isEqualTo("+44 1738 111222");
              assertThat(address.getEmail()).isEqualTo("ops@lowlandcattle.example.com");
              assertThat(address.getRoles())
                  .containsExactly(AddressRole.IMPORTER, AddressRole.CONSIGNEE);
            });
  }

  @Test
  void put_shouldReturn400KeyedByRolesAndKeepStoredValues_whenBodyOmitsRoles() throws Exception {
    Address saved = saveActiveAddressWithRolesAndOptionals();

    putAddress(saved.getId(), addressBody().without("roles"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(VALIDATION_ERROR_TYPE))
        .andExpect(
            jsonPath("$.errors.roles[0]").value("Select what this address can be used for"));

    assertStoredValuesAreStillTheSeededOnes(saved);
  }

  @Test
  void put_shouldReturn400KeyedByRolesAndKeepStoredValues_whenARoleIsUnknown() throws Exception {
    Address saved = saveActiveAddressWithRolesAndOptionals();
    AddressBody body = addressBody().with("roles", List.of("IMPORTER", "TRANSPORTER"));

    putAddress(saved.getId(), body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(VALIDATION_ERROR_TYPE))
        .andExpect(jsonPath("$.errors.roles[0]").value("Select valid roles"));

    assertStoredValuesAreStillTheSeededOnes(saved);
  }

  @Test
  void put_shouldReturn400KeyedByCountryCodeAndKeepStoredValues_whenAReceivingRoleIsOutsideTheUk()
      throws Exception {
    Address saved = saveActiveAddressWithRolesAndOptionals();
    AddressBody body = addressBody().with("countryCode", "IE").with("roles", List.of("IMPORTER"));

    putAddress(saved.getId(), body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(VALIDATION_ERROR_TYPE))
        .andExpect(
            jsonPath("$.errors.countryCode[0]")
                .value(
                    "A consignee, importer or place of destination must be in the United"
                        + " Kingdom"));

    assertStoredValuesAreStillTheSeededOnes(saved);
  }

  @Test
  void put_shouldReturn400PerFieldErrorsAndLeaveRowUntouched_whenBodyIsInvalid() throws Exception {
    Address saved = saveActiveAddressWithRolesAndOptionals();
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
    Address saved = saveActiveAddressWithRolesAndOptionals();
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
  void put_shouldReturn409ConflictProblem_whenOptimisticLockingFails() throws Exception {
    Address saved = saveActiveAddressWithRolesAndOptionals();
    doThrow(new OptimisticLockingFailureException("stale"))
        .when(addressRepository)
        .save(any(Address.class));

    putAddress(saved.getId(), addressBody())
        .andExpect(status().isConflict())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/conflict"))
        .andExpect(jsonPath("$.title").value("Conflict"));
  }

  @Test
  void save_shouldThrowOptimisticLockingFailure_whenTheAddressWasUpdatedSinceItWasRead() throws Exception {
    Address saved = saveActiveAddressWithRolesAndOptionals();
    Address stale = storedAddress(saved.getId());

    putAddress(saved.getId(), addressBody()).andExpect(status().isOk());

    stale.setName("Stale concurrent writer");

    assertThatThrownBy(() -> addressRepository.save(stale))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }
}
