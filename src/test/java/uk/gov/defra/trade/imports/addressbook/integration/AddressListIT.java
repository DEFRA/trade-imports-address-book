package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.defra.trade.imports.addressbook.ListPaging.DEFAULT_PAGE_SIZE;

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
import uk.gov.defra.trade.imports.addressbook.address.AddressRole;

class AddressListIT extends IntegrationBase {

  private static final String ORIGIN_AND_CONSIGNOR = "Origin And Consignor";
  private static final String CONSIGNEE_AND_IMPORTER = "Consignee And Importer";
  private static final String IMPORTER = "Importer";
  private static final String BRANCH = "Branch";
  private static final String NO_ROLES = "No Roles";
  private static final String EMPTY_ROLES = "Empty Roles";

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

  private void seedAddressesAcrossRolesAndStatuses() {
    saveAddress(
        addressHolding(ORGANISATION_ID, AddressRole.PLACE_OF_ORIGIN, AddressRole.CONSIGNOR)
            .name(ORIGIN_AND_CONSIGNOR)
            .countryCode("FR"));
    saveAddress(
        addressHolding(ORGANISATION_ID, AddressRole.CONSIGNEE, AddressRole.IMPORTER)
            .name(CONSIGNEE_AND_IMPORTER));
    saveAddress(addressHolding(ORGANISATION_ID, AddressRole.IMPORTER).name(IMPORTER));
    saveAddress(
        addressHolding(ORGANISATION_ID, AddressRole.BRANCH_ADDRESS).name(BRANCH).countryCode("FR"));
    saveAddress(addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE).name(NO_ROLES).roles(null));
    saveAddress(
        addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE).name(EMPTY_ROLES).roles(List.of()));
    saveAddress(
        addressBuilder(ORGANISATION_ID, AddressStatus.DELETED)
            .name("Deleted Branch")
            .roles(List.of(AddressRole.BRANCH_ADDRESS)));
    saveAddress(
        addressHolding(OTHER_ORG, AddressRole.CONSIGNOR)
            .name("Other Organisation Consignor"));
  }

  @Test
  void list_shouldReturn25ItemsAcross2Pages_whenNoPageSizeIsRequested() throws Exception {
    seedActiveWithDistinctCreatedAt(30);

    listAddresses()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(25))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pageSize").value(25))
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
        .andExpect(jsonPath("$.totalPages").value(4))
        .andExpect(jsonPath("$.items[0].name").value("Address 29"))
        .andExpect(jsonPath("$.items[7].name").value("Address 22"));
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
    listAddressesAs(ORGANISATION_ID, null).andExpect(badRequestProblemWithNoErrors());
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
  void list_shouldReturnEveryLiveAddress_whenNoRoleIsGiven() throws Exception {
    seedAddressesAcrossRolesAndStatuses();

    listAddresses()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(6))
        .andExpect(
            jsonPath(
                "$.items[*].name",
                containsInAnyOrder(
                    ORIGIN_AND_CONSIGNOR,
                    CONSIGNEE_AND_IMPORTER,
                    IMPORTER,
                    BRANCH,
                    NO_ROLES,
                    EMPTY_ROLES)));
  }

  @Test
  void list_shouldReturnTheAddressesHoldingAnyOfTheRoles_whenSeveralRolesAreGiven()
      throws Exception {
    seedAddressesAcrossRolesAndStatuses();

    listAddresses("role", "CONSIGNEE", "role", "IMPORTER", "role", "PLACE_OF_DESTINATION")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(2))
        .andExpect(
            jsonPath("$.items[*].name", containsInAnyOrder(CONSIGNEE_AND_IMPORTER, IMPORTER)));
  }

  @Test
  void list_shouldReturnOnlyTheLiveAddressesHoldingTheRole_whenOneRoleIsGiven() throws Exception {
    seedAddressesAcrossRolesAndStatuses();

    listAddresses("role", "CONSIGNEE")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[*].name", contains(CONSIGNEE_AND_IMPORTER)));
    listAddresses("role", "BRANCH_ADDRESS")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[*].name", contains(BRANCH)));
  }

  @Test
  void list_shouldNotReturnAddressesWithNoRoles_whenARoleIsGiven() throws Exception {
    seedAddressesAcrossRolesAndStatuses();

    listAddresses("role", "PLACE_OF_ORIGIN", "role", "CONSIGNOR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[*].name", contains(ORIGIN_AND_CONSIGNOR)));
  }

  @Test
  void list_shouldNarrowTheSearchByTheRoles_whenRolesAndASearchTermAreGiven() throws Exception {
    seedAddressesAcrossRolesAndStatuses();

    listAddresses("role", "IMPORTER", "q", "Consign")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[*].name", contains(CONSIGNEE_AND_IMPORTER)));
    listAddresses("role", "BRANCH_ADDRESS", "q", "Consign")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));
  }

  @Test
  void list_shouldNarrowTheCountryCodeByTheRoles() throws Exception {
    seedAddressesAcrossRolesAndStatuses();

    listAddresses("role", "BRANCH_ADDRESS", "countryCode", "FR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[*].name", contains(BRANCH)));
    listAddresses("role", "CONSIGNEE", "role", "IMPORTER", "countryCode", "FR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));
  }

  @Test
  void list_shouldPageAndCountOverTheAddressesHoldingTheRoles() throws Exception {
    for (int i = 0; i < 3; i++) {
      saveAddress(addressHolding(ORGANISATION_ID, AddressRole.CONSIGNOR).name("Consignor " + i));
    }
    saveAddress(addressHolding(ORGANISATION_ID, AddressRole.IMPORTER).name("Importer"));

    listAddresses("role", "CONSIGNOR", "pageSize", "2")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.totalItems").value(3))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  void list_shouldReturn400BadRequestProblemWithNoErrorsMap_whenARoleIsUnknown()
      throws Exception {
    listAddresses("role", "CONSIGNOR", "role", "TRANSPORTER")
        .andExpect(badRequestProblemWithNoErrors());
  }
}
