package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.defra.trade.imports.addressbook.address.AddressService.DEFAULT_PAGE_SIZE;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.AddressType;
import uk.gov.defra.trade.imports.addressbook.address.AddressUse;

class AddressListIT extends IntegrationBase {

  private static final String ORIGIN_AND_CONSIGNEE = "Origin And Consignee";
  private static final String CONSIGNOR = "Consignor";
  private static final String IMPORTER = "Importer";
  private static final String BRANCH = "Branch";
  private static final String NO_USES = "No Uses";
  private static final String EMPTY_USES = "Empty Uses";

  @Autowired private MongoTemplate mongoTemplate;

  private Address saveWithCreatedAt(String org, AddressStatus status, String name, Instant createdAt) {
    Address saved = saveAddress(addressBuilder(org, status).name(name));
    mongoTemplate.updateFirst(
        Query.query(Criteria.where("_id").is(saved.getId())),
        Update.update("createdAt", createdAt),
        Address.class);
    saved.setCreatedAt(createdAt);
    return saved;
  }

  private void seedActiveWithDistinctCreatedAt(int count) {
    Instant base = Instant.parse("2026-01-01T00:00:00Z");
    for (int i = 0; i < count; i++) {
      saveWithCreatedAt(
          ORGANISATION_ID, AddressStatus.ACTIVE, "Address " + i, base.plusSeconds(i + 1L));
    }
  }

  private void saveActiveAddressWithUses(String name, String countryCode, AddressUse... uses) {
    saveAddress(
        addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE)
            .name(name)
            .countryCode(countryCode)
            .uses(List.of(uses)));
  }

  private void seedAddressesAcrossTabsUsesAndStatuses() {
    saveActiveAddressWithUses(
        ORIGIN_AND_CONSIGNEE, "GB", AddressUse.PLACE_OF_ORIGIN, AddressUse.CONSIGNEE);
    saveActiveAddressWithUses(CONSIGNOR, "GB", AddressUse.CONSIGNOR);
    saveActiveAddressWithUses(IMPORTER, "FR", AddressUse.IMPORTER);
    saveActiveAddressWithUses(BRANCH, "FR", AddressUse.BRANCH_ADDRESS);
    saveAddress(addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE).name(NO_USES));
    saveAddress(
        addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE)
            .name(EMPTY_USES)
            .addressType(AddressType.BRANCH)
            .uses(List.of()));
    saveAddress(
        addressBuilder(ORGANISATION_ID, AddressStatus.DELETED)
            .name("Deleted Branch")
            .uses(List.of(AddressUse.BRANCH_ADDRESS)));
    saveAddress(
        addressBuilder(OTHER_ORG, AddressStatus.ACTIVE)
            .name("Other Organisation Consignor")
            .uses(List.of(AddressUse.CONSIGNOR)));
  }

  @Test
  void list_shouldReturn25ItemsAcross2Pages_whenNoPageSizeIsRequested() throws Exception {
    seedActiveWithDistinctCreatedAt(30);

    listAddresses()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(DEFAULT_PAGE_SIZE))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pageSize").value(DEFAULT_PAGE_SIZE))
        .andExpect(jsonPath("$.totalItems").value(30))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.items[0].name").value("Address 29"));
  }

  @Test
  void list_shouldReturnRemaining5OnPage2WithDisjointIds_when30AddressesSeeded() throws Exception {
    seedActiveWithDistinctCreatedAt(30);

    String page1Body =
        listAddresses()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(DEFAULT_PAGE_SIZE))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String page2Body =
        listAddresses("page", "2")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(5))
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.totalItems").value(30))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.items[0].name").value("Address 4"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Set<String> page1Ids = readIds(page1Body);
    Set<String> page2Ids = readIds(page2Body);
    page1Ids.retainAll(page2Ids);
    assertThat(page1Ids).isEmpty();
    assertThat(page2Ids).hasSize(5);
  }

  @Test
  void list_shouldExcludeDeletedTombstones_whenMixedStatusesSeeded() throws Exception {
    seedActiveWithDistinctCreatedAt(3);
    Address deleted =
        saveWithCreatedAt(
            ORGANISATION_ID, AddressStatus.DELETED, "Ghost Address", Instant.parse("2026-01-01T00:00:00Z"));

    listAddresses()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(3))
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[*].deleted", everyItem(is(false))))
        .andExpect(jsonPath("$.items[*].id", not(hasItem(deleted.getId()))));
  }

  @Test
  void list_shouldReturnOnlyCallersOrganisation_whenOtherOrgHasAddresses() throws Exception {
    seedActiveWithDistinctCreatedAt(2);
    saveWithCreatedAt(
        OTHER_ORG, AddressStatus.ACTIVE, "Other Org Address", Instant.parse("2026-01-01T00:00:00Z"));

    listAddresses()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(2))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[*].organisationId", everyItem(is(ORGANISATION_ID))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1"})
  void list_shouldReturn400BadRequestProblemWithNoErrorsMap_whenPageIsOutOfRange(String page)
      throws Exception {
    listAddresses("page", page).andExpect(badRequestProblemWithNoErrors());
  }

  @Test
  void list_shouldReturn8ItemsAcross4Pages_whenPageSizeIs8() throws Exception {
    seedActiveWithDistinctCreatedAt(30);

    listAddresses("pageSize", "8")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(8))
        .andExpect(jsonPath("$.pageSize").value(8))
        .andExpect(jsonPath("$.totalItems").value(30))
        .andExpect(jsonPath("$.totalPages").value(4));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "101", "-1", "abc"})
  void list_shouldReturn400BadRequestProblemWithNoErrorsMap_whenPageSizeIsOutOfRangeOrNonNumeric(
      String pageSize) throws Exception {
    listAddresses("pageSize", pageSize).andExpect(badRequestProblemWithNoErrors());
  }

  @ParameterizedTest
  @CsvSource({"1, 1, 3", "100, 3, 1"})
  void list_shouldServeTheRequestedPageSize_whenItIsAtABound(
      int pageSize, int expectedItems, int expectedTotalPages) throws Exception {
    seedActiveWithDistinctCreatedAt(3);

    listAddresses("pageSize", String.valueOf(pageSize))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pageSize").value(pageSize))
        .andExpect(jsonPath("$.items.length()").value(expectedItems))
        .andExpect(jsonPath("$.totalPages").value(expectedTotalPages));
  }

  @Test
  void list_shouldReturn400BadRequestProblem_whenPageIsNonNumeric() throws Exception {
    listAddresses("page", "abc").andExpect(badRequestProblemWithNoErrors());
  }

  @Test
  void list_shouldReturn400BadRequestWithNoErrorsMap_whenOrgHeaderMissing() throws Exception {
    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORGANISATION_ID))
        .andExpect(badRequestProblemWithNoErrors());
  }

  @Test
  void list_shouldReturnEmptyItems_whenPageIsBeyondTheLast() throws Exception {
    seedActiveWithDistinctCreatedAt(30);

    listAddresses("page", "3")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.page").value(3))
        .andExpect(jsonPath("$.totalItems").value(30))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  void list_shouldReturnEveryLiveAddress_whenNoAddressTypeIsGiven() throws Exception {
    seedAddressesAcrossTabsUsesAndStatuses();

    listAddresses()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(6))
        .andExpect(
            jsonPath(
                "$.items[*].name",
                containsInAnyOrder(
                    ORIGIN_AND_CONSIGNEE,
                    CONSIGNOR,
                    IMPORTER,
                    BRANCH,
                    NO_USES,
                    EMPTY_USES)));
  }

  @Test
  void list_shouldReturnAddressesHoldingAnOriginOrConsignorUseAndThoseHoldingNone_whenAddressTypeIsOriginOrConsignor()
      throws Exception {
    seedAddressesAcrossTabsUsesAndStatuses();

    listAddresses("addressType", "ORIGIN_OR_CONSIGNOR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(4))
        .andExpect(
            jsonPath(
                "$.items[*].name",
                containsInAnyOrder(
                    ORIGIN_AND_CONSIGNEE, CONSIGNOR, NO_USES, EMPTY_USES)));
  }

  @Test
  void list_shouldReturnAddressesHoldingADestinationConsigneeOrImporterUse_whenAddressTypeIsDestinationConsigneeOrImporter()
      throws Exception {
    seedAddressesAcrossTabsUsesAndStatuses();

    listAddresses("addressType", "DESTINATION_CONSIGNEE_OR_IMPORTER")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(2))
        .andExpect(jsonPath("$.items[*].name", containsInAnyOrder(ORIGIN_AND_CONSIGNEE, IMPORTER)));
  }

  @Test
  void list_shouldReturnOnlyTheLiveAddressHoldingABranchAddressUse_whenAddressTypeIsBranch()
      throws Exception {
    seedAddressesAcrossTabsUsesAndStatuses();

    listAddresses("addressType", "BRANCH")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[*].name", contains(BRANCH)));
  }

  @Test
  void list_shouldCombineTheSearchTermWithTheAddressType() throws Exception {
    seedAddressesAcrossTabsUsesAndStatuses();

    listAddresses("addressType", "DESTINATION_CONSIGNEE_OR_IMPORTER", "q", "Consign")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[*].name", contains(ORIGIN_AND_CONSIGNEE)));
  }

  @Test
  void list_shouldCombineTheCountryCodeWithTheAddressType() throws Exception {
    seedAddressesAcrossTabsUsesAndStatuses();

    listAddresses("addressType", "BRANCH", "countryCode", "FR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[*].name", contains(BRANCH)));
    listAddresses("addressType", "ORIGIN_OR_CONSIGNOR", "countryCode", "FR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));
  }

  @Test
  void list_shouldPageAndCountOnlyTheAddressesListedUnderTheAddressType() throws Exception {
    seedAddressesAcrossTabsUsesAndStatuses();

    listAddresses("addressType", "ORIGIN_OR_CONSIGNOR", "pageSize", "1")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.totalItems").value(4))
        .andExpect(jsonPath("$.totalPages").value(4));
  }

  @Test
  void list_shouldReturn400BadRequestProblemWithNoErrorsMap_whenAddressTypeIsUnknown()
      throws Exception {
    listAddresses("addressType", "TRANSPORTER").andExpect(badRequestProblemWithNoErrors());
  }

  @Test
  void list_shouldReturnOnlyTheLiveAddressesHoldingTheUse_whenUseIsGiven() throws Exception {
    seedAddressesAcrossTabsUsesAndStatuses();

    listAddresses("use", "CONSIGNOR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[*].name", contains(CONSIGNOR)));
    listAddresses("use", "CONSIGNEE")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[*].name", contains(ORIGIN_AND_CONSIGNEE)));
  }

  @Test
  void list_shouldNotReturnAddressesWithNoUses_whenUseIsGivenWithOriginOrConsignor()
      throws Exception {
    seedAddressesAcrossTabsUsesAndStatuses();

    listAddresses("addressType", "ORIGIN_OR_CONSIGNOR", "use", "PLACE_OF_ORIGIN")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[*].name", contains(ORIGIN_AND_CONSIGNEE)));
  }

  @Test
  void list_shouldNarrowTheSearchByTheUse_whenUseAndSearchTermAreGiven() throws Exception {
    seedAddressesAcrossTabsUsesAndStatuses();

    listAddresses("use", "CONSIGNEE", "q", "Consign")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[*].name", contains(ORIGIN_AND_CONSIGNEE)));
    listAddresses("use", "CONSIGNEE", "q", "Importer")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));
  }

  @Test
  void list_shouldPageAndCountOverTheAddressesHoldingTheUse() throws Exception {
    for (int i = 0; i < 3; i++) {
      saveActiveAddressWithUses("Consignor " + i, "GB", AddressUse.CONSIGNOR);
    }
    saveActiveAddressWithUses("Importer", "GB", AddressUse.IMPORTER);

    listAddresses("use", "CONSIGNOR", "pageSize", "2")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.totalItems").value(3))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  void list_shouldReturn400BadRequestProblemWithNoErrorsMap_whenUseIsUnknown() throws Exception {
    listAddresses("use", "TRANSPORTER").andExpect(badRequestProblemWithNoErrors());
  }
}
