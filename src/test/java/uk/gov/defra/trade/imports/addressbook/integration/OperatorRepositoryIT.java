package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.domain.PageRequest.of;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;

class OperatorRepositoryIT extends IntegrationBase {

  private static final String ORG_A = "ORG-A";
  private static final String ORG_B = "ORG-B";

  @Test
  void findByIdAndOrganisationId_returnsAddress_whenOrganisationMatches() {
    Address saved = saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Acme Farms"));

    assertThat(operatorRepository.findByIdAndOrganisationId(saved.getId(), ORG_A))
        .isPresent()
        .get()
        .extracting(Address::getName)
        .isEqualTo("Acme Farms");
  }

  @Test
  void findByIdAndOrganisationId_returnsEmpty_whenOrganisationDiffers() {
    Address orgA1 = saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Org A One"));
    saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Org A Two"));
    saveAddress(addressBuilder(ORG_B, AddressStatus.ACTIVE).name("Org B One"));

    assertThat(operatorRepository.findByIdAndOrganisationId(orgA1.getId(), ORG_B)).isEmpty();
  }

  @Test
  void searchByAnyCountryCode_isOrgScopedAndExcludesDeleted() {
    saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Org A FR").countryCode("FR"));
    saveAddress(addressBuilder(ORG_B, AddressStatus.ACTIVE).name("Org B FR").countryCode("FR"));
    saveAddress(addressBuilder(ORG_A, AddressStatus.DELETED).name("Org A deleted FR").countryCode("FR"));

    Page<Address> page =
        operatorRepository.searchByAnyCountryCode(ORG_A, AddressStatus.ACTIVE, List.of("FR"), of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent()).singleElement().extracting(Address::getOrganisationId).isEqualTo(ORG_A);
  }

  @Test
  void searchByQueryOrAnyCountryCode_isOrgScoped() {
    saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Paris").countryCode("FR"));
    saveAddress(addressBuilder(ORG_B, AddressStatus.ACTIVE).name("Other Paris").countryCode("FR"));

    Page<Address> page =
        operatorRepository.searchByQueryOrAnyCountryCode(
            ORG_A, AddressStatus.ACTIVE, ".*\\QParis\\E.*", List.of("FR"), of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent()).singleElement().extracting(Address::getOrganisationId).isEqualTo(ORG_A);
  }

  @Test
  void searchByAnyCountryCode_matchesAnyOfSeveralCountryCodes() {
    Address french = saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Paris Depot").countryCode("FR"));
    Address irish = saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Dublin Depot").countryCode("IE"));
    saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Berlin Depot").countryCode("DE"));

    Page<Address> page =
        operatorRepository.searchByAnyCountryCode(
            ORG_A, AddressStatus.ACTIVE, List.of("FR", "IE"), of(0, 10));

    assertThat(page.getContent())
        .extracting(Address::getId)
        .containsExactlyInAnyOrder(french.getId(), irish.getId());
  }

  @Test
  void searchByQueryOrAnyCountryCode_matchesAnyOfSeveralCountryCodes() {
    Address french = saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Paris Depot").countryCode("FR"));
    Address irish = saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Dublin Depot").countryCode("IE"));
    saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Berlin Depot").countryCode("DE"));

    Page<Address> page =
        operatorRepository.searchByQueryOrAnyCountryCode(
            ORG_A, AddressStatus.ACTIVE, ".*\\QNoSuchText\\E.*", List.of("FR", "IE"), of(0, 10));

    assertThat(page.getContent())
        .extracting(Address::getId)
        .containsExactlyInAnyOrder(french.getId(), irish.getId());
  }
}
