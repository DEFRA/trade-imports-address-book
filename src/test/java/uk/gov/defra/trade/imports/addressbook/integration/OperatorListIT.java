package uk.gov.defra.trade.imports.addressbook.integration;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashSet;
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

class OperatorListIT extends IntegrationBase {

  @Autowired private MongoTemplate mongoTemplate;
  @Autowired private ObjectMapper objectMapper;

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

  @Test
  void list_shouldReturn25ItemsAcross2Pages_whenNoPageSizeIsRequested() throws Exception {
    // Given
    seedActiveWithDistinctCreatedAt(30);

    // When / Then
    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORGANISATION_ID).header(ORG_HEADER, ORGANISATION_ID))
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
    // Given
    seedActiveWithDistinctCreatedAt(30);

    String page1Body =
        mockMvc
            .perform(get("/organisation/{orgId}/addresses", ORGANISATION_ID).header(ORG_HEADER, ORGANISATION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(25))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // When / Then
    String page2Body =
        mockMvc
            .perform(
                get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                    .header(ORG_HEADER, ORGANISATION_ID)
                    .param("page", "2"))
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
    org.assertj.core.api.Assertions.assertThat(page1Ids).isEmpty();
    org.assertj.core.api.Assertions.assertThat(page2Ids).hasSize(5);
  }

  @Test
  void list_shouldExcludeDeletedTombstones_whenMixedStatusesSeeded() throws Exception {
    // Given
    seedActiveWithDistinctCreatedAt(3);
    Address deleted =
        saveWithCreatedAt(
            ORGANISATION_ID, AddressStatus.DELETED, "Ghost Address", Instant.parse("2026-01-01T00:00:00Z"));

    // When / Then
    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORGANISATION_ID).header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(3))
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[*].deleted", everyItem(is(false))))
        .andExpect(jsonPath("$.items[*].id", not(hasItem(deleted.getId()))));
  }

  @Test
  void list_shouldReturnOnlyCallersOrganisation_whenOtherOrgHasAddresses() throws Exception {
    // Given
    seedActiveWithDistinctCreatedAt(2);
    saveWithCreatedAt(
        OTHER_ORG, AddressStatus.ACTIVE, "Other Org Address", Instant.parse("2026-01-01T00:00:00Z"));

    // When / Then
    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORGANISATION_ID).header(ORG_HEADER, ORGANISATION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(2))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[*].organisationId", everyItem(is(ORGANISATION_ID))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1"})
  void list_shouldReturn400BadRequestProblemWithNoErrorsMap_whenPageIsOutOfRange(String page)
      throws Exception {
    // When / Then
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .param("page", page))
        .andExpect(badRequestProblemWithNoErrors());
  }

  @Test
  void list_shouldReturn8ItemsAcross4Pages_whenPageSizeIs8() throws Exception {
    // Given
    seedActiveWithDistinctCreatedAt(30);

    // When / Then
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .param("pageSize", "8"))
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
    // When / Then
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .param("pageSize", pageSize))
        .andExpect(badRequestProblemWithNoErrors());
  }

  @ParameterizedTest
  @CsvSource({"1, 1, 3", "100, 3, 1"})
  void list_shouldServeTheRequestedPageSize_whenItIsAtABound(
      int pageSize, int expectedItems, int expectedTotalPages) throws Exception {
    // Given
    seedActiveWithDistinctCreatedAt(3);

    // When / Then
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .param("pageSize", String.valueOf(pageSize)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pageSize").value(pageSize))
        .andExpect(jsonPath("$.items.length()").value(expectedItems))
        .andExpect(jsonPath("$.totalPages").value(expectedTotalPages));
  }

  @Test
  void list_shouldReturn400BadRequestProblem_whenPageIsNonNumeric() throws Exception {
    // When / Then
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .param("page", "abc"))
        .andExpect(badRequestProblemWithNoErrors());
  }

  @Test
  void list_shouldReturn400BadRequestWithNoErrorsMap_whenOrgHeaderMissing() throws Exception {
    // When / Then
    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORGANISATION_ID))
        .andExpect(badRequestProblemWithNoErrors());
  }

  @Test
  void list_shouldReturnEmptyItems_whenPageIsBeyondTheLast() throws Exception {
    // Given
    seedActiveWithDistinctCreatedAt(30);

    // When / Then
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .param("page", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.page").value(3))
        .andExpect(jsonPath("$.totalItems").value(30))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  private Set<String> readIds(String body) throws Exception {
    Set<String> ids = new HashSet<>();
    JsonNode items = objectMapper.readTree(body).get("items");
    items.forEach(item -> ids.add(item.get("id").asText()));
    return ids;
  }
}
