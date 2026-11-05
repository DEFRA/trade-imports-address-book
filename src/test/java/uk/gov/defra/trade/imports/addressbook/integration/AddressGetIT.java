package uk.gov.defra.trade.imports.addressbook.integration;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressRole;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;

class AddressGetIT extends IntegrationBase {

  @Test
  void get_shouldReturn200WithAllMappedFields_whenAddressIsLive() throws Exception {
    Address saved =
        saveAddress(
            addressHolding(ORGANISATION_ID, AddressRole.CONSIGNEE, AddressRole.IMPORTER)
                .addressLine2("Unit 3")
                .county("Highland"));

    getAddress(saved.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId()))
        .andExpect(jsonPath("$.name").value(NAME))
        .andExpect(jsonPath("$.addressLine1").value(ADDRESS_LINE_1))
        .andExpect(jsonPath("$.addressLine2").value("Unit 3"))
        .andExpect(jsonPath("$.townOrCity").value(TOWN_OR_CITY))
        .andExpect(jsonPath("$.county").value("Highland"))
        .andExpect(jsonPath("$.postcode").value(POSTCODE))
        .andExpect(jsonPath("$.countryCode").value(COUNTRY_CODE))
        .andExpect(jsonPath("$.phone").value(PHONE))
        .andExpect(jsonPath("$.email").value(EMAIL))
        .andExpect(jsonPath("$.roles[0]").value("CONSIGNEE"))
        .andExpect(jsonPath("$.roles[1]").value("IMPORTER"))
        .andExpect(jsonPath("$.roles.length()").value(2))
        .andExpect(jsonPath("$.organisationId").value(ORGANISATION_ID))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.modifiedAt").exists())
        .andExpect(jsonPath("$.deleted").value(false));
  }

  @Test
  void get_shouldReturn200WithDeletedTrue_whenAddressIsTombstoned() throws Exception {
    Address saved = saveAddress(addressBuilder(ORGANISATION_ID, AddressStatus.DELETED));

    getAddress(saved.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId()))
        .andExpect(jsonPath("$.deleted").value(true));
  }

  @Test
  void get_shouldReturn404NotFoundProblem_whenIdIsUnknown() throws Exception {
    getAddress(UNKNOWN_ID)
        .andExpect(status().isNotFound())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value("https://api.cdp.defra.cloud/problems/not-found"))
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }
}
