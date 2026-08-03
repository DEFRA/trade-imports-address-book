package uk.gov.defra.trade.imports.addressbook.integration;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.OperatorRepository;

/**
 * Full-stack list integration test for {@code GET /organisation/{orgId}/addresses}. Pins the
 * paginated, newest-first, organisation-scoped, ACTIVE-only listing: 30 seeded addresses paginate
 * 25 + 5 across two pages at the server-config page size (cv-025), DELETED tombstones are excluded,
 * another organisation's addresses are invisible, an out-of-range or non-numeric {@code page} is a
 * 400 bad-request problem with no {@code errors} map, and a supplied page-size request parameter is
 * never honoured (locked contract: pagination size is server-configured, not a request parameter).
 */
class OperatorListIT extends IntegrationBase {

  @Autowired private OperatorRepository repository;
  @Autowired private MongoTemplate mongoTemplate;
  @Autowired private ObjectMapper objectMapper;

  private Address saveWithCreatedAt(String org, AddressStatus status, String name, Instant createdAt) {
    Address address =
        Address.builder()
            .name(name)
            .addressLine1("14 Drover's Way")
            .townOrCity("Inverness")
            .postcode("IV2 3JH")
            .countryCode("GB")
            .phone("+44 1463 234567")
            .email("exports@highlandlivestock.example.com")
            .organisationId(org)
            .status(status)
            .build();
    Address saved = repository.save(address);
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
          ORGANISATION_ID, AddressStatus.ACTIVE, "Address " + i, base.plusSeconds(count - i));
    }
  }

  @Test
  void list_shouldReturn25ItemsAcross2Pages_when30AddressesSeeded() throws Exception {
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
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void list_shouldIgnorePageSizeRequestParams_whenServerConfigSizeApplies() throws Exception {
    // Given
    seedActiveWithDistinctCreatedAt(30);

    // When / Then — locked contract: page size is server-configured, not a request parameter
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .param("page_size", "5")
                .param("pageSize", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(25))
        .andExpect(jsonPath("$.pageSize").value(25))
        .andExpect(jsonPath("$.totalItems").value(30));
  }

  @Test
  void list_shouldReturn400BadRequestProblem_whenPageIsNonNumeric() throws Exception {
    // When / Then
    mockMvc
        .perform(
            get("/organisation/{orgId}/addresses", ORGANISATION_ID)
                .header(ORG_HEADER, ORGANISATION_ID)
                .param("page", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void list_shouldReturn400BadRequestWithNoErrorsMap_whenOrgHeaderMissing() throws Exception {
    // When / Then
    mockMvc
        .perform(get("/organisation/{orgId}/addresses", ORGANISATION_ID))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/bad-request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors").doesNotExist());
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
