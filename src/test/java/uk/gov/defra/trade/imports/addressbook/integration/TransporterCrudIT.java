package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import uk.gov.defra.trade.imports.addressbook.transporter.Transporter;

class TransporterCrudIT extends IntegrationBase {

  @Autowired private ObjectMapper objectMapper;

  @Test
  void aCreatedPrivateTransporterReadsBackWithItsTypeAndNoApprovalNumberRolesOrAddressType()
      throws Exception {
    String id = createdResourceId(postTransporter(transporterBody()));

    expectPrivateTransporter(getTransporter(id).andExpect(status().isOk()));
  }

  private static void expectPrivateTransporter(ResultActions transporter) throws Exception {
    transporter
        .andExpect(jsonPath("$.name").value(NAME))
        .andExpect(jsonPath("$.addressLine1").value(ADDRESS_LINE_1))
        .andExpect(jsonPath("$.countryCode").value(COUNTRY_CODE))
        .andExpect(jsonPath("$.type").value("PRIVATE"))
        .andExpect(jsonPath("$.approvalNumber").doesNotExist())
        .andExpect(jsonPath("$.roles").doesNotExist())
        .andExpect(jsonPath("$.addressType").doesNotExist())
        .andExpect(jsonPath("$.organisationId").value(ORGANISATION_ID))
        .andExpect(jsonPath("$.deleted").value(false));
  }

  @Test
  void aCreatedCommercialTransporterReadsBackWithItsApprovalNumberAndFixedCountry()
      throws Exception {
    String id = createdResourceId(postTransporter(commercialTransporterBody()));

    getTransporter(id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("COMMERCIAL"))
        .andExpect(jsonPath("$.approvalNumber").value(APPROVAL_NUMBER))
        .andExpect(jsonPath("$.countryCode").value(NORTHERN_IRELAND_COUNTRY_CODE));
  }

  @Test
  void newestTransporterIsFirstOnTheList() throws Exception {
    postTransporter(transporterBody().with("name", "First Haulage")).andExpect(status().isCreated());
    postTransporter(transporterBody().with("name", "Second Haulage")).andExpect(status().isCreated());

    listTransporters()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].name").value("Second Haulage"))
        .andExpect(jsonPath("$.items[1].name").value("First Haulage"))
        .andExpect(jsonPath("$.totalItems").value(2));
  }

  @Test
  void aCommercialTransporterWithNoApprovalNumberIsRefusedOnApprovalNumber() throws Exception {
    postTransporter(commercialTransporterBody().without("approvalNumber"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.approvalNumber").exists());

    assertThat(storedCount(Transporter.class)).isZero();
  }

  @Test
  void aPrivateTransporterWithAnApprovalNumberIsRefusedOnApprovalNumber() throws Exception {
    postTransporter(transporterBody().with("approvalNumber", APPROVAL_NUMBER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.approvalNumber").exists());
  }

  @Test
  void aCommercialTransporterOutsideNorthernIrelandIsRefusedOnCountryCode() throws Exception {
    postTransporter(commercialTransporterBody().with("countryCode", "GB"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.countryCode").exists());
  }

  @Test
  void aTransporterTypeOutsidePrivateOrCommercialIsRefused() throws Exception {
    postTransporter(transporterBody().with("type", "APPROVED"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateReplacesEveryMutableFieldAndBumpsModifiedAt() throws Exception {
    String id = createdResourceId(postTransporter(transporterBody()));
    JsonNode before = transporterJson(id);
    Instant createdAt = Instant.parse(before.get("createdAt").asText());
    Instant baselineModifiedAt = Instant.parse(before.get("modifiedAt").asText());

    putTransporterAs(
            ORGANISATION_ID,
            ORGANISATION_ID,
            id,
            transporterBody().with("name", "Renamed Haulage").with("townOrCity", "Perth"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed Haulage"))
        .andExpect(jsonPath("$.townOrCity").value("Perth"));

    JsonNode after = transporterJson(id);
    assertThat(Instant.parse(after.get("createdAt").asText())).isEqualTo(createdAt);
    assertThat(Instant.parse(after.get("modifiedAt").asText())).isAfter(baselineModifiedAt);
  }

  private JsonNode transporterJson(String id) throws Exception {
    String body =
        getTransporter(id)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }
}
