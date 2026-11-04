package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.domain.PageRequest.of;
import static uk.gov.defra.trade.imports.addressbook.address.OperatorTestSupport.listCriteria;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.Page;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.AddressType;
import uk.gov.defra.trade.imports.addressbook.address.AddressUse;
import uk.gov.defra.trade.imports.addressbook.address.ListCriteria;

class OperatorRepositoryIT extends IntegrationBase {

  private static final String ORG_A = "ORG-A";
  private static final String ORG_B = "ORG-B";

  private Page<Address> listLive(String organisationId, ListCriteria criteria) {
    return operatorRepository.listLiveAddresses(organisationId, criteria, of(0, 10));
  }

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
  void listLiveAddresses_withACountryCode_isOrgScopedAndExcludesDeleted() {
    saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Org A FR").countryCode("FR"));
    saveAddress(addressBuilder(ORG_B, AddressStatus.ACTIVE).name("Org B FR").countryCode("FR"));
    saveAddress(addressBuilder(ORG_A, AddressStatus.DELETED).name("Org A deleted FR").countryCode("FR"));

    Page<Address> page = listLive(ORG_A, listCriteria().countryCodes("FR").build());

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent()).singleElement().extracting(Address::getName).isEqualTo("Org A FR");
  }

  @Test
  void listLiveAddresses_withASearchPatternOrACountryCode_isOrgScoped() {
    saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Paris").countryCode("FR"));
    saveAddress(addressBuilder(ORG_B, AddressStatus.ACTIVE).name("Other Paris").countryCode("FR"));

    Page<Address> page =
        listLive(ORG_A, listCriteria().searchPattern(".*\\QParis\\E.*").countryCodes("FR").build());

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent()).singleElement().extracting(Address::getOrganisationId).isEqualTo(ORG_A);
  }

  @Test
  void listLiveAddresses_matchesAnyOfSeveralCountryCodes() {
    Map<String, Address> depotsByCountryCode = saveDepotsInFranceIrelandAndGermany();

    Page<Address> page = listLive(ORG_A, listCriteria().countryCodes("FR", "IE").build());

    assertThat(page.getContent())
        .extracting(Address::getId)
        .containsExactlyInAnyOrder(
            depotsByCountryCode.get("FR").getId(), depotsByCountryCode.get("IE").getId());
  }

  @Test
  void listLiveAddresses_withASearchPatternAndSeveralCountryCodes_matchesTheCodesWhenTheTextMatchesNothing() {
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
  void listLiveAddresses_withAnAddressType_isOrgScopedAndExcludesDeleted() {
    saveAddress(
        addressBuilder(ORG_A, AddressStatus.ACTIVE)
            .name("Org A branch")
            .uses(List.of(AddressUse.BRANCH_ADDRESS)));
    saveAddress(
        addressBuilder(ORG_B, AddressStatus.ACTIVE)
            .name("Org B branch")
            .uses(List.of(AddressUse.BRANCH_ADDRESS)));
    saveAddress(
        addressBuilder(ORG_A, AddressStatus.DELETED)
            .name("Org A deleted branch")
            .uses(List.of(AddressUse.BRANCH_ADDRESS)));

    Page<Address> page = listLive(ORG_A, listCriteria().category(AddressType.BRANCH).build());

    assertThat(page.getContent()).singleElement().extracting(Address::getName).isEqualTo("Org A branch");
  }

  @ParameterizedTest
  @MethodSource("criteriaMatchingTheConsignorAddress")
  void listLiveAddresses_withAUseOrASearchUse_isOrgScopedAndExcludesDeletedAndOtherUses(
      ListCriteria criteria) {
    saveAddress(
        addressBuilder(ORG_A, AddressStatus.ACTIVE)
            .name("Org A consignor")
            .uses(List.of(AddressUse.CONSIGNOR)));
    saveAddress(
        addressBuilder(ORG_A, AddressStatus.ACTIVE)
            .name("Org A importer")
            .uses(List.of(AddressUse.IMPORTER)));
    saveAddress(
        addressBuilder(ORG_B, AddressStatus.ACTIVE)
            .name("Org B consignor")
            .uses(List.of(AddressUse.CONSIGNOR)));
    saveAddress(
        addressBuilder(ORG_A, AddressStatus.DELETED)
            .name("Org A deleted consignor")
            .uses(List.of(AddressUse.CONSIGNOR)));

    Page<Address> page = listLive(ORG_A, criteria);

    assertThat(page.getContent()).singleElement().extracting(Address::getName).isEqualTo("Org A consignor");
  }

  private static Stream<ListCriteria> criteriaMatchingTheConsignorAddress() {
    return Stream.of(
        listCriteria().use(AddressUse.CONSIGNOR).build(),
        listCriteria().searchUses(AddressUse.CONSIGNOR).build());
  }

  private Map<String, Address> saveDepotsInFranceIrelandAndGermany() {
    return Map.of(
        "FR", saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Paris Depot").countryCode("FR")),
        "IE", saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Dublin Depot").countryCode("IE")),
        "DE", saveAddress(addressBuilder(ORG_A, AddressStatus.ACTIVE).name("Berlin Depot").countryCode("DE")));
  }
}
