package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.domain.PageRequest.of;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.OperatorRepository;

class OperatorRepositoryIT extends IntegrationBase {

  private static final String ORG_A = "ORG-A";
  private static final String ORG_B = "ORG-B";

  @Autowired private OperatorRepository operatorRepository;

  @Test
  void findByIdAndOrganisationId_returnsAddress_whenOrganisationMatches() {
    Address saved = saveAddress("Acme Farms", ORG_A);

    assertThat(operatorRepository.findByIdAndOrganisationId(saved.getId(), ORG_A))
        .isPresent()
        .get()
        .extracting(Address::getName)
        .isEqualTo("Acme Farms");
  }

  @Test
  void findByIdAndOrganisationId_returnsEmpty_whenOrganisationDiffers() {
    Address orgA1 = saveAddress("Org A One", ORG_A);
    saveAddress("Org A Two", ORG_A);
    saveAddress("Org B One", ORG_B);

    assertThat(operatorRepository.findByIdAndOrganisationId(orgA1.getId(), ORG_B)).isEmpty();
  }

  @Test
  void searchByAnyCountryCode_isOrgScopedAndExcludesDeleted() {
    saveAddress("Org A FR", ORG_A, AddressStatus.ACTIVE, "FR");
    saveAddress("Org B FR", ORG_B, AddressStatus.ACTIVE, "FR");
    saveAddress("Org A deleted FR", ORG_A, AddressStatus.DELETED, "FR");

    Page<Address> page =
        operatorRepository.searchByAnyCountryCode(ORG_A, AddressStatus.ACTIVE, List.of("FR"), of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent()).singleElement().extracting(Address::getOrganisationId).isEqualTo(ORG_A);
  }

  @Test
  void searchByQueryOrAnyCountryCode_isOrgScoped() {
    saveAddress("Paris", ORG_A, AddressStatus.ACTIVE, "FR");
    saveAddress("Other Paris", ORG_B, AddressStatus.ACTIVE, "FR");

    Page<Address> page =
        operatorRepository.searchByQueryOrAnyCountryCode(
            ORG_A, AddressStatus.ACTIVE, ".*\\QParis\\E.*", List.of("FR"), of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent()).singleElement().extracting(Address::getOrganisationId).isEqualTo(ORG_A);
  }

  @Test
  void searchByAnyCountryCode_matchesAnyOfSeveralCountryCodes() {
    Address french = saveAddress("Paris Depot", ORG_A, AddressStatus.ACTIVE, "FR");
    Address irish = saveAddress("Dublin Depot", ORG_A, AddressStatus.ACTIVE, "IE");
    saveAddress("Berlin Depot", ORG_A, AddressStatus.ACTIVE, "DE");

    Page<Address> page =
        operatorRepository.searchByAnyCountryCode(
            ORG_A, AddressStatus.ACTIVE, List.of("FR", "IE"), of(0, 10));

    assertThat(page.getContent())
        .extracting(Address::getId)
        .containsExactlyInAnyOrder(french.getId(), irish.getId());
  }

  @Test
  void searchByQueryOrAnyCountryCode_matchesAnyOfSeveralCountryCodes() {
    Address french = saveAddress("Paris Depot", ORG_A, AddressStatus.ACTIVE, "FR");
    Address irish = saveAddress("Dublin Depot", ORG_A, AddressStatus.ACTIVE, "IE");
    saveAddress("Berlin Depot", ORG_A, AddressStatus.ACTIVE, "DE");

    Page<Address> page =
        operatorRepository.searchByQueryOrAnyCountryCode(
            ORG_A, AddressStatus.ACTIVE, ".*\\QNoSuchText\\E.*", List.of("FR", "IE"), of(0, 10));

    assertThat(page.getContent())
        .extracting(Address::getId)
        .containsExactlyInAnyOrder(french.getId(), irish.getId());
  }

  private Address saveAddress(String name, String organisationId) {
    return saveAddress(name, organisationId, AddressStatus.ACTIVE, "GB");
  }

  private Address saveAddress(
      String name, String organisationId, AddressStatus status, String countryCode) {
    return operatorRepository.save(
        Address.builder()
            .name(name)
            .addressLine1("1 Farm Lane")
            .townOrCity("Exeter")
            .postcode("EX1 1AA")
            .countryCode(countryCode)
            .phone("01234567890")
            .email("acme@example.com")
            .organisationId(organisationId)
            .status(status)
            .build());
  }
}
