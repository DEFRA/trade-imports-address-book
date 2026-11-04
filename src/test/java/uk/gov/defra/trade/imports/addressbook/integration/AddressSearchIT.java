package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.AddressUse;

class AddressSearchIT extends IntegrationBase {

  @Test
  void partialWordSearchMatchesNameCaseInsensitively() throws Exception {
    Address match = saveAddress(addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE).name("Green Farm"));
    saveAddress(perthAddress("Blue Barn"));

    listAddresses("q", "gree")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(match.getId()));
  }

  @Test
  void partialWordSearchMatchesTownOrCityCaseInsensitively() throws Exception {
    Address match = saveAddress(addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE).name("North Depot"));
    saveAddress(perthAddress("South Depot"));

    listAddresses("q", "inver")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(match.getId()));
  }

  @Test
  void multiWordSearchMatchesNameCaseInsensitively() throws Exception {
    Address match =
        saveAddress(
            addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE).name("Green Valley Livestock Farm"));
    saveAddress(perthAddress("Blue Barn"));

    listAddresses("q", "green valley")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(match.getId()));

    listAddresses("q", "GREEN")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(match.getId()));
  }

  @Test
  void partialWordSearchMatchesPostcodeCaseInsensitively() throws Exception {
    Address match =
        saveAddress(addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE).name("Highland Livestock"));
    saveAddress(perthAddress("Lowland Cattle"));

    listAddresses("q", "iv2")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(match.getId()));
  }

  @Test
  void partialWordSearchMatchesAddressLineOneCaseInsensitively() throws Exception {
    Address match = saveAddress(perthAddress("North Depot").addressLine1("Green Lane"));
    saveAddress(perthAddress("South Depot"));

    listAddresses("q", "GREEN la")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(match.getId()));
  }

  @Test
  void partialWordSearchMatchesAddressLineTwoCaseInsensitively() throws Exception {
    Address match =
        saveAddress(
            addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE)
                .name("North Depot")
                .addressLine2("Ballymore Road")
                .county("Antrim"));
    saveAddress(
        addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE)
            .name("South Depot")
            .addressLine2("Station Road")
            .county("Down"));

    listAddresses("q", "ballymore")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(match.getId()));
  }

  @Test
  void partialWordSearchMatchesCountyCaseInsensitively() throws Exception {
    Address match =
        saveAddress(
            addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE)
                .name("North Depot")
                .addressLine2("Ballymore Road")
                .county("County Antrim"));
    saveAddress(
        addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE)
            .name("South Depot")
            .addressLine2("Station Road")
            .county("County Down"));

    listAddresses("q", "ANTRIM")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(match.getId()));
  }

  @Test
  void severalCountryCodesMatchTheAddressesInAnyOfThem() throws Exception {
    Address french = saveAddress(parisDepot());
    Address irish =
        saveAddress(
            addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE)
                .name("Dublin Depot")
                .addressLine1("1 Main Street")
                .townOrCity("Dublin")
                .postcode("D01 F5P2")
                .countryCode("IE"));
    saveAddress(londonDepot());

    listAddresses("countryCode", "FR", "countryCode", "IE")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(2))
        .andExpect(jsonPath("$.items[*].id", hasItem(french.getId())))
        .andExpect(jsonPath("$.items[*].id", hasItem(irish.getId())));
  }

  @Test
  void searchTermIsTreatedAsLiteralNotRegex() throws Exception {
    saveAddress(addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE).name("Green Farm"));
    saveAddress(perthAddress("Blue Barn"));

    listAddresses("q", ".*")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));
  }

  @Test
  void countryCodeParamMatchesStoredAlpha2CodeAndExcludesOtherOrgs() throws Exception {
    Address french = saveAddress(parisDepot());
    saveAddress(londonDepot());
    saveAddress(
        addressBuilder(OTHER_ORG, AddressStatus.ACTIVE)
            .name("Other FR")
            .addressLine1("2 Other Street")
            .townOrCity("Lyon")
            .postcode("69001")
            .countryCode("FR"));

    listAddresses("countryCode", "FR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(french.getId()));
  }

  @Test
  void countryCodeSearchExcludesSoftDeletedRows() throws Exception {
    Address live = saveAddress(parisDepot());
    saveAddress(
        addressBuilder(ORGANISATION_ID, AddressStatus.DELETED)
            .name("Deleted FR")
            .addressLine1("9 Deleted Lane")
            .townOrCity("Paris")
            .postcode("75002")
            .countryCode("FR"));

    listAddresses("countryCode", "FR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(live.getId()));
  }

  @Test
  void combinedQueryAndCountryCodeMatchesTextOrCountryCode() throws Exception {
    Address french = saveAddress(parisDepot());
    Address textOnly =
        saveAddress(londonDepot().name("France Freight").addressLine1("1 France Yard"));

    listAddresses("q", "France", "countryCode", "FR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(2))
        .andExpect(jsonPath("$.items[*].id", hasItem(french.getId())))
        .andExpect(jsonPath("$.items[*].id", hasItem(textOnly.getId())));
  }

  @Test
  void searchIsOrgScopedAndExcludesSoftDeletedForQueryPath() throws Exception {
    Address live = saveAddress(addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE).name("Green Farm"));
    saveAddress(
        addressBuilder(ORGANISATION_ID, AddressStatus.DELETED)
            .name("Green Ghost")
            .addressLine1("99 Deleted Lane"));
    saveAddress(
        addressBuilder(OTHER_ORG, AddressStatus.ACTIVE)
            .name("Green Other Org")
            .addressLine1("1 Other Street")
            .townOrCity("Glasgow")
            .postcode("G1 1AA"));

    listAddresses("q", "green")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(live.getId()));
  }

  @Test
  void searchResultsPaginateWithDisjointPages() throws Exception {
    Set<String> allIds = new HashSet<>();
    for (int i = 0; i < 30; i++) {
      allIds.add(
          saveAddress(addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE).name("Green Farm " + i))
              .getId());
    }

    String page1Body =
        listAddresses("q", "green", "countryCode", "GB", "page", "1")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(25))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String page2Body =
        listAddresses("q", "green", "countryCode", "GB", "page", "2")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(5))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Set<String> pageIds = new HashSet<>();
    pageIds.addAll(readIds(page1Body));
    pageIds.addAll(readIds(page2Body));
    assertThat(pageIds).containsExactlyInAnyOrderElementsOf(allIds);
  }

  @Test
  void searchUseAloneMatchesExactlyTheAddressesHoldingIt() throws Exception {
    Address consignor = saveAddress(londonDepot().name("Depot A").uses(List.of(AddressUse.CONSIGNOR)));
    saveAddress(londonDepot().name("Depot B").uses(List.of(AddressUse.IMPORTER)));
    saveAddress(londonDepot().name("Depot C"));

    listAddresses("searchUse", "CONSIGNOR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(consignor.getId()));
  }

  @Test
  void searchUseMatchesAnAddressHoldingAnyOfSeveralSearchUses() throws Exception {
    Address consignor = saveAddress(londonDepot().name("Depot A").uses(List.of(AddressUse.CONSIGNOR)));
    Address consignee = saveAddress(londonDepot().name("Depot B").uses(List.of(AddressUse.CONSIGNEE)));
    saveAddress(londonDepot().name("Depot C").uses(List.of(AddressUse.IMPORTER)));

    listAddresses("searchUse", "CONSIGNOR", "searchUse", "CONSIGNEE")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(2))
        .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(consignor.getId(), consignee.getId())));
  }

  @Test
  void searchUseWidensTheSearchTermToAnAddressHoldingTheUse() throws Exception {
    Address byName = saveAddress(londonDepot().name("Green Farm"));
    Address byUse = saveAddress(londonDepot().name("Depot B").uses(List.of(AddressUse.CONSIGNOR)));
    saveAddress(londonDepot().name("Depot C").uses(List.of(AddressUse.IMPORTER)));

    listAddresses("q", "green", "searchUse", "CONSIGNOR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(2))
        .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(byName.getId(), byUse.getId())));
  }

  @Test
  void searchUseTermAndCountryCodeTogetherReturnTheUnionOfTheirMatches() throws Exception {
    Address byName = saveAddress(londonDepot().name("Green Farm"));
    Address byCountry = saveAddress(parisDepot());
    Address byUse = saveAddress(londonDepot().name("Depot B").uses(List.of(AddressUse.CONSIGNOR)));
    saveAddress(londonDepot().name("Depot C").uses(List.of(AddressUse.IMPORTER)));

    listAddresses("q", "green", "countryCode", "FR", "searchUse", "CONSIGNOR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(3))
        .andExpect(
            jsonPath(
                "$.items[*].id",
                containsInAnyOrder(byName.getId(), byCountry.getId(), byUse.getId())));
  }

  @Test
  void searchUseStaysWithinTheAddressType() throws Exception {
    Address consignor = saveAddress(londonDepot().name("Depot A").uses(List.of(AddressUse.CONSIGNOR)));
    saveAddress(londonDepot().name("Depot B").uses(List.of(AddressUse.IMPORTER)));

    listAddresses("addressType", "BRANCH", "searchUse", "CONSIGNOR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));
    listAddresses("addressType", "ORIGIN_OR_CONSIGNOR", "searchUse", "CONSIGNOR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(consignor.getId()));
  }

  @Test
  void aBlankSearchUseIsDroppedAndTheOtherSearchUsesStillMatch() throws Exception {
    Address consignor = saveAddress(londonDepot().name("Depot A").uses(List.of(AddressUse.CONSIGNOR)));
    saveAddress(londonDepot().name("Depot B").uses(List.of(AddressUse.IMPORTER)));

    listAddresses("searchUse", "CONSIGNOR", "searchUse", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(consignor.getId()));
  }

  @Test
  void anUnknownSearchUseIsABadRequestWithNoErrorsMap() throws Exception {
    listAddresses("searchUse", "TRANSPORTER").andExpect(badRequestProblemWithNoErrors());
  }

  private static Address.AddressBuilder perthAddress(String name) {
    return addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE)
        .name(name)
        .addressLine1("2 Market Street")
        .townOrCity("Perth")
        .postcode("PH1 5AA");
  }

  private static Address.AddressBuilder parisDepot() {
    return addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE)
        .name("Paris Depot")
        .addressLine1("1 Rue de Rivoli")
        .townOrCity("Paris")
        .postcode("75001")
        .countryCode("FR");
  }

  private static Address.AddressBuilder londonDepot() {
    return addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE)
        .name("London Depot")
        .addressLine1("10 Downing Street")
        .townOrCity("London")
        .postcode("SW1A 1AA");
  }
}
