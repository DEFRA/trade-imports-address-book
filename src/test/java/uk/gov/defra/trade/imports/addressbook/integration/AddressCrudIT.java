package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;
import uk.gov.defra.trade.imports.addressbook.address.AddressType;
import uk.gov.defra.trade.imports.addressbook.address.AddressUse;

class AddressCrudIT extends IntegrationBase {

  @Test
  void post_shouldReturn201WithLocationAndStampedOrganisation() throws Exception {
    AddressBody body = addressBody().with("addressLine2", "Unit 3").with("county", "Highland");

    postAddress(body)
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    "Location",
                    containsString("/organisation/" + ORGANISATION_ID + "/addresses/")))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.name").value(NAME))
        .andExpect(jsonPath("$.addressLine1").value(ADDRESS_LINE_1))
        .andExpect(jsonPath("$.addressLine2").value("Unit 3"))
        .andExpect(jsonPath("$.townOrCity").value(TOWN_OR_CITY))
        .andExpect(jsonPath("$.countryCode").value(COUNTRY_CODE))
        .andExpect(jsonPath("$.phone").value(PHONE))
        .andExpect(jsonPath("$.organisationId").value(ORGANISATION_ID))
        .andExpect(jsonPath("$.deleted").value(false))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.modifiedAt").exists());

    assertThat(activeAddressesFor(ORGANISATION_ID))
        .singleElement()
        .satisfies(
            address -> {
              assertThat(address.getOrganisationId()).isEqualTo(ORGANISATION_ID);
              assertThat(address.getStatus()).isEqualTo(AddressStatus.ACTIVE);
              assertThat(address.getCountryCode()).isEqualTo(COUNTRY_CODE);
              assertThat(address.getCreatedAt()).isNotNull();
            });
  }

  @Test
  void post_shouldResolveTheLocationHeaderIdToTheCreatedAddress() throws Exception {
    String createdId = createdAddressId(postAddress(addressBody()));

    getAddress(createdId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(createdId))
        .andExpect(jsonPath("$.name").value(NAME));
  }

  @Test
  void post_shouldStoreAndReadBackTheAddressTypeAndUsesInOrder() throws Exception {
    AddressBody body =
        addressBody()
            .with("addressType", "DESTINATION_CONSIGNEE_OR_IMPORTER")
            .with("uses", List.of("IMPORTER", "PLACE_OF_DESTINATION", "CONSIGNEE"));

    ResultActions created =
        postAddress(body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.addressType").value("DESTINATION_CONSIGNEE_OR_IMPORTER"))
            .andExpect(
                jsonPath("$.uses", contains("IMPORTER", "PLACE_OF_DESTINATION", "CONSIGNEE")));

    getAddress(createdAddressId(created))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.addressType").value("DESTINATION_CONSIGNEE_OR_IMPORTER"))
        .andExpect(jsonPath("$.uses", contains("IMPORTER", "PLACE_OF_DESTINATION", "CONSIGNEE")));
    assertThat(activeAddressesFor(ORGANISATION_ID))
        .singleElement()
        .satisfies(
            address -> {
              assertThat(address.getAddressType())
                  .isEqualTo(AddressType.DESTINATION_CONSIGNEE_OR_IMPORTER);
              assertThat(address.getUses())
                  .containsExactly(
                      AddressUse.IMPORTER, AddressUse.PLACE_OF_DESTINATION, AddressUse.CONSIGNEE);
            });
  }

  @Test
  void post_shouldReadBackNullAddressTypeAndUses_whenNeitherIsGiven() throws Exception {
    getAddress(createdAddressId(postAddress(addressBody())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.addressType").value(nullValue()))
        .andExpect(jsonPath("$.uses").value(nullValue()));
  }

  @Test
  void post_shouldReturn400KeyedByUsesAndSaveNothing_whenAUseIsUnknown() throws Exception {
    AddressBody body = addressBody().with("uses", List.of("CONSIGNOR", "TRANSPORTER"));

    postAddress(body)
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value(VALIDATION_ERROR_TYPE))
        .andExpect(jsonPath("$.errors.uses").exists())
        .andExpect(jsonPath("$.errors.addressType").doesNotExist());

    assertThat(activeAddressesFor(ORGANISATION_ID)).isEmpty();
  }

  @Test
  void post_shouldReturn400KeyedByAddressTypeAndSaveNothing_whenTheTypeIsUnknown()
      throws Exception {
    AddressBody body = addressBody().with("addressType", "TRANSPORTER");

    postAddress(body)
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value(VALIDATION_ERROR_TYPE))
        .andExpect(jsonPath("$.errors.addressType").exists())
        .andExpect(jsonPath("$.errors.uses").doesNotExist());

    assertThat(activeAddressesFor(ORGANISATION_ID)).isEmpty();
  }

  @Test
  void post_shouldReturn400ValidationProblemKeyedByCamelCaseFieldNames() throws Exception {
    AddressBody body = addressBody().blank("addressLine1").with("email", "not-an-email");

    postAddress(body)
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value(VALIDATION_ERROR_TYPE))
        .andExpect(jsonPath("$.title").value("Validation Error"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors.addressLine1").exists())
        .andExpect(jsonPath("$.errors.email").exists())
        .andExpect(jsonPath("$.errors.address_line_1").doesNotExist());

    assertThat(activeAddressesFor(ORGANISATION_ID)).isEmpty();
  }

  @Test
  void post_shouldReturn400BadRequestProblem_whenJsonBodyIsMalformed() throws Exception {
    postAddress("{\"name\":").andExpect(badRequestProblemWithNoErrors());

    assertThat(activeAddressesFor(ORGANISATION_ID)).isEmpty();
  }

  @Test
  void put_shouldReturn400BadRequestProblem_whenJsonBodyIsEmpty() throws Exception {
    Address saved = saveAddress(addressBuilder(ORGANISATION_ID, AddressStatus.ACTIVE));

    mockMvc
        .perform(
            put("/organisation/{orgId}/addresses/{operator-id}", ORGANISATION_ID, saved.getId())
                .header(ORG_HEADER, ORGANISATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
        .andExpect(badRequestProblemWithNoErrors());
  }

  @Test
  void post_shouldRejectStrayTypeOrRoleFieldsWithPerFieldErrors() throws Exception {
    AddressBody body = addressBody().with("type", "IMPORTER").with("role", "consignor");

    postAddress(body)
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value(VALIDATION_ERROR_TYPE))
        .andExpect(jsonPath("$.errors.type").exists())
        .andExpect(jsonPath("$.errors.role").exists());

    assertThat(activeAddressesFor(ORGANISATION_ID)).isEmpty();
  }

  @Test
  void post_shouldIgnoreEchoedReadOnlyAndUnknownFields() throws Exception {
    AddressBody body =
        addressBody()
            .with("id", "echoed-read-only-id")
            .with("createdAt", "2020-01-01T00:00:00Z")
            .with("deleted", true)
            .with("somethingUnknown", "ignored");

    postAddress(body)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.id").value(not("echoed-read-only-id")))
        .andExpect(jsonPath("$.deleted").value(false))
        .andExpect(jsonPath("$.createdAt").value(not(startsWith("2020"))));

    assertThat(activeAddressesFor(ORGANISATION_ID))
        .singleElement()
        .satisfies(
            address ->
                assertThat(address.getCreatedAt())
                    .isAfter(Instant.parse("2020-01-01T00:00:00Z")));
  }

  @Test
  void post_shouldAcceptCountryCodeAsGivenAndFreeStringPhone() throws Exception {
    AddressBody body =
        addressBody()
            .with("name", "Ferme des Deux Rivieres")
            .with("addressLine1", "12 Rue du Marche")
            .with("townOrCity", "Calais")
            .with("postcode", "62100")
            .with("countryCode", "FR")
            .with("phone", "ring the office")
            .with("email", "exports@deuxrivieres.example.com");

    postAddress(body)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.countryCode").value("FR"))
        .andExpect(jsonPath("$.phone").value("ring the office"));

    assertThat(activeAddressesFor(ORGANISATION_ID))
        .singleElement()
        .satisfies(address -> assertThat(address.getCountryCode()).isEqualTo("FR"));
  }

  @Test
  void post_shouldReturn400_whenCountryCodeIsBlank() throws Exception {
    postAddress(addressBody().blank("countryCode"))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.errors.countryCode").exists());

    assertThat(activeAddressesFor(ORGANISATION_ID)).isEmpty();
  }

  @Test
  void post_shouldReturn400WithTheFieldMessages_whenNamePostcodeCountryAndPhoneAreBlank()
      throws Exception {
    AddressBody body =
        addressBody().blank("name").blank("postcode").blank("countryCode").blank("phone");

    postAddress(body)
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.errors.name", hasItem("Enter a name or organisation")))
        .andExpect(jsonPath("$.errors.postcode", hasItem("Enter a postcode or Zip code")))
        .andExpect(jsonPath("$.errors.countryCode", hasItem("Select a country")))
        .andExpect(jsonPath("$.errors.phone", hasItem("Enter a phone number")));

    assertThat(activeAddressesFor(ORGANISATION_ID)).isEmpty();
  }
}
