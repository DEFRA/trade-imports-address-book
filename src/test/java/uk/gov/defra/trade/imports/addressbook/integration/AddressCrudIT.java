package uk.gov.defra.trade.imports.addressbook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import uk.gov.defra.trade.imports.addressbook.address.Address;
import uk.gov.defra.trade.imports.addressbook.address.AddressRole;
import uk.gov.defra.trade.imports.addressbook.address.AddressStatus;

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
  void post_shouldStoreAndReadBackTheRolesInOrderWithRepeatsCollapsed() throws Exception {
    AddressBody body =
        addressBody()
            .with("roles", List.of("IMPORTER", "PLACE_OF_DESTINATION", "IMPORTER", "CONSIGNEE"));

    ResultActions created =
        postAddress(body)
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.roles", contains("IMPORTER", "PLACE_OF_DESTINATION", "CONSIGNEE")))
            .andExpect(jsonPath("$.addressType").doesNotExist());

    getAddress(createdAddressId(created))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles", contains("IMPORTER", "PLACE_OF_DESTINATION", "CONSIGNEE")));
    assertThat(activeAddressesFor(ORGANISATION_ID))
        .singleElement()
        .satisfies(
            address ->
                assertThat(address.getRoles())
                    .containsExactly(
                        AddressRole.IMPORTER,
                        AddressRole.PLACE_OF_DESTINATION,
                        AddressRole.CONSIGNEE));
  }

  @Test
  void post_shouldIgnoreAnAddressType_whenTheBodyCarriesOne() throws Exception {
    postAddress(addressBody().with("addressType", "ORIGIN_OR_CONSIGNOR"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.addressType").doesNotExist());

    assertThat(activeAddressesFor(ORGANISATION_ID)).hasSize(1);
  }

  @Test
  void post_shouldReturn400KeyedByRolesAndSaveNothing_whenRolesAreMissing() throws Exception {
    assertCreateRefusedOnRolesAndNothingSaved(
        addressBody().without("roles"), "Select what this address can be used for");
  }

  @Test
  void post_shouldReturn400KeyedByRolesAndSaveNothing_whenRolesAreEmpty() throws Exception {
    assertCreateRefusedOnRolesAndNothingSaved(
        addressBody().with("roles", List.of()), "Select what this address can be used for");
  }

  @Test
  void post_shouldReturn400KeyedByRolesAndSaveNothing_whenARoleIsUnknown() throws Exception {
    assertCreateRefusedOnRolesAndNothingSaved(
        addressBody().with("roles", List.of("CONSIGNOR", "TRANSPORTER")), "Select valid roles");
  }

  @Test
  void post_shouldReturn400KeyedByRolesAndSaveNothing_whenTheRolesComeFromTwoGroups()
      throws Exception {
    assertCreateRefusedOnRolesAndNothingSaved(
        addressBody().with("countryCode", "GB").with("roles", List.of("CONSIGNOR", "CONSIGNEE")),
        "An address can only have roles from one group");
  }

  private void assertCreateRefusedOnRolesAndNothingSaved(AddressBody body, String message)
      throws Exception {
    postAddress(body)
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.type").value(VALIDATION_ERROR_TYPE))
        .andExpect(jsonPath("$.errors.roles", contains(message)))
        .andExpect(jsonPath("$.errors.countryCode").doesNotExist());

    assertThat(activeAddressesFor(ORGANISATION_ID)).isEmpty();
  }

  @Test
  void post_shouldReturn400KeyedByCountryCodeAndSaveNothing_whenAReceivingRoleIsOutsideTheUk()
      throws Exception {
    postAddress(addressBody().with("countryCode", "FR").with("roles", List.of("CONSIGNEE")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(VALIDATION_ERROR_TYPE))
        .andExpect(
            jsonPath(
                "$.errors.countryCode",
                contains(
                    "A consignee, importer or place of destination must be in the United"
                        + " Kingdom")))
        .andExpect(jsonPath("$.errors.roles").doesNotExist());

    assertThat(activeAddressesFor(ORGANISATION_ID)).isEmpty();
  }

  @Test
  void post_shouldSaveABranchAddressOutsideTheUk() throws Exception {
    postAddress(addressBody().with("countryCode", "FR").with("roles", List.of("BRANCH_ADDRESS")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.countryCode").value("FR"))
        .andExpect(jsonPath("$.roles", contains("BRANCH_ADDRESS")));
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

    putAddress(saved.getId(), "").andExpect(badRequestProblemWithNoErrors());
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
