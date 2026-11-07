package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TransporterSearchIT extends IntegrationBase {

  @Autowired private ObjectMapper objectMapper;

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
  void partialWordSearchMatchesAddressLineOneCaseInsensitively() throws Exception {
    postTransporter(
            transporterBody().with("name", "Highland Haulage Ltd").with("addressLine1", "14 Drover's Way"))
        .andExpect(status().isCreated());
    postTransporter(
            transporterBody().with("name", "Lowland Cattle Co").with("addressLine1", "2 Market Street"))
        .andExpect(status().isCreated());

    listTransporters("q", "drover")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Highland Haulage Ltd"));
  }

  @Test
  void partialWordSearchMatchesAddressLineTwoCaseInsensitively() throws Exception {
    postTransporter(
            transporterBody().with("name", "Highland Haulage Ltd").with("addressLine2", "Ballymore Road"))
        .andExpect(status().isCreated());
    postTransporter(
            transporterBody().with("name", "Lowland Cattle Co").with("addressLine2", "Station Road"))
        .andExpect(status().isCreated());

    listTransporters("q", "ballymore")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Highland Haulage Ltd"));
  }

  @Test
  void partialWordSearchMatchesTownOrCityCaseInsensitively() throws Exception {
    postTransporter(
            transporterBody().with("name", "Highland Haulage Ltd").with("townOrCity", "Inverness"))
        .andExpect(status().isCreated());
    postTransporter(transporterBody().with("name", "Lowland Cattle Co").with("townOrCity", "Perth"))
        .andExpect(status().isCreated());

    listTransporters("q", "inver")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Highland Haulage Ltd"));
  }

  @Test
  void partialWordSearchMatchesCountyCaseInsensitively() throws Exception {
    postTransporter(transporterBody().with("name", "Highland Haulage Ltd").with("county", "County Antrim"))
        .andExpect(status().isCreated());
    postTransporter(transporterBody().with("name", "Lowland Cattle Co").with("county", "County Down"))
        .andExpect(status().isCreated());

    listTransporters("q", "antrim")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Highland Haulage Ltd"));
  }

  @Test
  void partialWordSearchMatchesPostcodeCaseInsensitively() throws Exception {
    postTransporter(
            transporterBody().with("name", "Highland Haulage Ltd").with("postcode", "IV2 3JH"))
        .andExpect(status().isCreated());
    postTransporter(transporterBody().with("name", "Lowland Cattle Co").with("postcode", "PH1 5AA"))
        .andExpect(status().isCreated());

    listTransporters("q", "iv2")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Highland Haulage Ltd"));
  }

  @Test
  void aSoftDeletedTransporterLeavesTheListAndStaysReadableByIdMarkedDeleted() throws Exception {
    String id = createdResourceId(postTransporter(transporterBody()));

    deleteTransporter(id).andExpect(status().isNoContent());

    listTransporters().andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
    getTransporter(id).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(true));
  }

  @Test
  void deletingAnAlreadyDeletedTransporterChangesNothing() throws Exception {
    String id = createdResourceId(postTransporter(transporterBody()));

    deleteTransporter(id).andExpect(status().isNoContent());
    String modifiedAtAfterFirstDelete = modifiedAtOf(id);

    deleteTransporter(id).andExpect(status().isNoContent());

    getTransporter(id).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(true));
    assertThat(modifiedAtOf(id)).isEqualTo(modifiedAtAfterFirstDelete);
  }

  private String modifiedAtOf(String id) throws Exception {
    return objectMapper
        .readTree(
            getTransporter(id)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("modifiedAt")
        .asText();
  }
}
