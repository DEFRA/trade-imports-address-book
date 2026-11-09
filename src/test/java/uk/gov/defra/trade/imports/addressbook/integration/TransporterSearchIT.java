package uk.gov.defra.trade.imports.addressbook.integration;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class TransporterSearchIT extends IntegrationBase {

  private void seedTwoTransporters() throws Exception {
    postTransporter(
            transporterBody()
                .with("name", "Highland Haulage Ltd")
                .with("addressLine1", "14 Drover's Way")
                .with("townOrCity", "Inverness")
                .with("postcode", "IV2 3JH"))
        .andExpect(status().isCreated());
    postTransporter(commercialTransporterBody().with("name", "Lough Neagh Livestock Ltd"))
        .andExpect(status().isCreated());
  }

  @Test
  void aSearchTermMatchesTheName() throws Exception {
    seedTwoTransporters();

    listTransporters("q", "Highland")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Highland Haulage Ltd"));
  }

  @Test
  void aSearchTermMatchesTheApprovalNumber() throws Exception {
    seedTwoTransporters();

    listTransporters("q", APPROVAL_NUMBER)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Lough Neagh Livestock Ltd"));
  }

  @Test
  void aCountryCodeNarrowsToTransportersInThatCountry() throws Exception {
    seedTwoTransporters();

    listTransporters("countryCode", NORTHERN_IRELAND_COUNTRY_CODE)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Lough Neagh Livestock Ltd"));
  }

  @Test
  void aTypeFilterKeepsOnlyThatType() throws Exception {
    seedTwoTransporters();

    listTransporters("type", "COMMERCIAL")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].type").value("COMMERCIAL"));
  }

  @Test
  void searchType_shouldReturnExactlyTheTransportersOfThatType_whenOnlyASearchTypeIsGiven()
      throws Exception {
    String commercial =
        createdResourceId(
            postTransporter(commercialTransporterBody().with("name", "Lough Neagh Livestock Ltd")));
    createdResourceId(postTransporter(transporterBody().with("name", "Highland Haulage Ltd")));

    listTransporters("searchType", "COMMERCIAL")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(commercial));
  }

  @Test
  void searchType_shouldAlsoReturnATransporterOfThatType_whenASearchTermIsGiven() throws Exception {
    String byName =
        createdResourceId(postTransporter(transporterBody().with("name", "Green Farm Haulage")));
    String byType =
        createdResourceId(
            postTransporter(commercialTransporterBody().with("name", "Lough Neagh Livestock Ltd")));

    listTransporters("q", "green", "searchType", "COMMERCIAL")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(2))
        .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(byName, byType)));
  }

  @Test
  void list_shouldKeepSearchTypeMatchesWithinTheTypesToKeep_whenBothAreGiven() throws Exception {
    String commercial =
        createdResourceId(
            postTransporter(commercialTransporterBody().with("name", "Lough Neagh Livestock Ltd")));
    createdResourceId(postTransporter(transporterBody().with("name", "Highland Haulage Ltd")));

    listTransporters("type", "PRIVATE", "searchType", "COMMERCIAL")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));
    listTransporters("type", "COMMERCIAL", "searchType", "COMMERCIAL")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(commercial));
  }

  @Test
  void list_shouldIgnoreABlankSearchType_whenOtherSearchTypesAreGiven() throws Exception {
    String commercial =
        createdResourceId(
            postTransporter(commercialTransporterBody().with("name", "Lough Neagh Livestock Ltd")));
    createdResourceId(postTransporter(transporterBody().with("name", "Highland Haulage Ltd")));

    listTransporters("searchType", "COMMERCIAL", "searchType", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(commercial));
  }

  @Test
  void list_shouldReturn400BadRequestProblemWithNoErrorsMap_whenATypeIsUnknown() throws Exception {
    listTransporters("type", "APPROVED").andExpect(badRequestProblemWithNoErrors());
  }

  @Test
  void list_shouldReturn400BadRequestProblemWithNoErrorsMap_whenASearchTypeIsUnknown()
      throws Exception {
    listTransporters("searchType", "APPROVED").andExpect(badRequestProblemWithNoErrors());
  }

  @ParameterizedTest(name = "a partial word search matches the {0} field case-insensitively")
  @MethodSource("partialWordSearchFields")
  void partialWordSearchMatchesCaseInsensitively(
      String field, String matchingValue, String nonMatchingValue, String searchTerm)
      throws Exception {
    seedTwoTransportersDifferingBy(field, matchingValue, nonMatchingValue);

    listTransporters("q", searchTerm)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Highland Haulage Ltd"));
  }

  private static Stream<Arguments> partialWordSearchFields() {
    return Stream.of(
        Arguments.of("addressLine1", "14 Drover's Way", "2 Market Street", "drover"),
        Arguments.of("addressLine2", "Ballymore Road", "Station Road", "ballymore"),
        Arguments.of("townOrCity", "Inverness", "Perth", "inver"),
        Arguments.of("county", "County Antrim", "County Down", "antrim"),
        Arguments.of("postcode", "IV2 3JH", "PH1 5AA", "iv2"));
  }

  private void seedTwoTransportersDifferingBy(
      String field, String matchingValue, String nonMatchingValue) throws Exception {
    postTransporter(
            transporterBody().with("name", "Highland Haulage Ltd").with(field, matchingValue))
        .andExpect(status().isCreated());
    postTransporter(
            transporterBody().with("name", "Lowland Cattle Co").with(field, nonMatchingValue))
        .andExpect(status().isCreated());
  }
}
