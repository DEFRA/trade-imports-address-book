package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.domain.PageRequest.of;
import static uk.gov.defra.trade.imports.addressbook.address.AddressTestSupport.listCriteria;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.Page;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressRole;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.ListCriteria;

class AddressRepositoryIT extends IntegrationBase {

  private static final String ORG_A = "ORG-A";
  private static final String ORG_B = "ORG-B";

  private Page<Address> listLive(String organisationId, ListCriteria criteria) {
    return addressRepository.listLiveAddresses(organisationId, criteria, of(0, 10));
  }

  @Test
  void findByIdAndOrganisationId_shouldReturnTheAddress_whenOrganisationMatches() {
    Address saved = saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Acme Farms"));

    assertThat(addressRepository.findByIdAndOrganisationId(saved.getId(), ORG_A))
        .isPresent()
        .get()
        .extracting(Address::getName)
        .isEqualTo("Acme Farms");
  }

  @Test
  void findByIdAndOrganisationId_shouldReturnEmpty_whenOrganisationDiffers() {
    Address orgA1 = saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Org A One"));
    saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Org A Two"));
    saveAddress(addressBuilder(ORG_B, AddressStatus.ACTIVE).name("Org B One"));

    assertThat(addressRepository.findByIdAndOrganisationId(orgA1.getId(), ORG_B)).isEmpty();
  }

  @Test
  void listLiveAddresses_shouldKeepOnlyTheOrganisationsLiveAddresses_whenACountryCodeIsGiven() {
    saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Org A FR").countryCode("FR"));
    saveAddress(addressBuilder(ORG_B, AddressStatus.ACTIVE).name("Org B FR").countryCode("FR"));
    saveAddress(addressBuilder(ORG_A, AddressStatus.DELETED).name("Org A deleted FR").countryCode("FR"));

    Page<Address> page = listLive(ORG_A, listCriteria().countryCodes("FR").build());

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent()).singleElement().extracting(Address::getName).isEqualTo("Org A FR");
  }

  @Test
  void listLiveAddresses_shouldKeepOnlyTheOrganisationsAddresses_whenASearchPatternAndACountryCodeAreGiven() {
    saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Paris").countryCode("FR"));
    saveAddress(addressBuilder(ORG_B, AddressStatus.ACTIVE).name("Other Paris").countryCode("FR"));

    Page<Address> page =
        listLive(ORG_A, listCriteria().searchPattern(".*\\QParis\\E.*").countryCodes("FR").build());

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent()).singleElement().extracting(Address::getOrganisationId).isEqualTo(ORG_A);
  }

  @Test
  void listLiveAddresses_shouldMatchAnyOfTheCountryCodes_whenSeveralAreGiven() {
    Map<String, Address> depotsByCountryCode = saveDepotsInFranceIrelandAndGermany();

    Page<Address> page = listLive(ORG_A, listCriteria().countryCodes("FR", "IE").build());

    assertThat(page.getContent())
        .extracting(Address::getId)
        .containsExactlyInAnyOrder(
            depotsByCountryCode.get("FR").getId(), depotsByCountryCode.get("IE").getId());
  }

  @Test
  void listLiveAddresses_shouldMatchTheCountryCodes_whenTheSearchPatternMatchesNothing() {
    Map<String, Address> depotsByCountryCode = saveDepotsInFranceIrelandAndGermany();

    Page<Address> page =
        listLive(
            ORG_A,
            listCriteria().searchPattern(".*\\QNoSuchText\\E.*").countryCodes("FR", "IE").build());

    assertThat(page.getContent())
        .extracting(Address::getId)
        .containsExactlyInAnyOrder(
            depotsByCountryCode.get("FR").getId(), depotsByCountryCode.get("IE").getId());
  }

  @Test
  void listLiveAddresses_shouldKeepAddressesHoldingAnyOfTheRoles_whenSeveralRolesAreGiven() {
    saveAddress(addressHolding(ORG_A, AddressRole.CONSIGNEE).name("Org A consignee"));
    saveAddress(addressHolding(ORG_A, AddressRole.IMPORTER).name("Org A importer"));
    saveAddress(addressHolding(ORG_A, AddressRole.BRANCH_ADDRESS).name("Org A branch"));

    Page<Address> page =
        listLive(
            ORG_A, listCriteria().roles(AddressRole.CONSIGNEE, AddressRole.IMPORTER).build());

    assertThat(page.getContent())
        .extracting(Address::getName)
        .containsExactlyInAnyOrder("Org A consignee", "Org A importer");
  }

  @ParameterizedTest
  @MethodSource("criteriaMatchingTheConsignorAddress")
  void listLiveAddresses_shouldKeepOnlyTheOrganisationsLiveConsignor_whenARoleOrASearchRoleIsGiven(
      ListCriteria criteria) {
    saveAddress(addressHolding(ORG_A, AddressRole.CONSIGNOR).name("Org A consignor"));
    saveAddress(addressHolding(ORG_A, AddressRole.IMPORTER).name("Org A importer"));
    saveAddress(addressHolding(ORG_B, AddressRole.CONSIGNOR).name("Org B consignor"));
    saveAddress(
        addressBuilder(ORG_A, AddressStatus.DELETED)
            .name("Org A deleted consignor")
            .roles(List.of(AddressRole.CONSIGNOR)));
    saveAddress(
        addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Org A with no roles").roles(null));

    Page<Address> page = listLive(ORG_A, criteria);

    assertThat(page.getContent()).singleElement().extracting(Address::getName).isEqualTo("Org A consignor");
  }

  private static Stream<ListCriteria> criteriaMatchingTheConsignorAddress() {
    return Stream.of(
        listCriteria().roles(AddressRole.CONSIGNOR).build(),
        listCriteria().searchRoles(AddressRole.CONSIGNOR).build());
  }

  private Map<String, Address> saveDepotsInFranceIrelandAndGermany() {
    return Map.of(
        "FR", saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Paris Depot").countryCode("FR")),
        "IE", saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Dublin Depot").countryCode("IE")),
        "DE", saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Berlin Depot").countryCode("DE")));
  }
}
